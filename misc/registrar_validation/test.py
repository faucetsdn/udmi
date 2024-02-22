# pylint: skip-file
# because this is an old partial file

from collections.abc import Callable
import copy
import datetime
import glob
import json
import json
import os
from pathlib import Path
import random
import re
import shutil
import shutil
import signal
import ssl
import subprocess
import sys
import time
import time
from typing import Any
from typing import Iterator
import jwt
import paho.mqtt.client as mqtt
import pytest

KEYS_TO_REDACT = ["timestamp", "version", "operation"]
REDACTED_VALUE = "redacted"

SITE_PATH = os.getenv("SITE_PATH") if os.getenv("SITE_PATH") else "."
devices_list = [
    x.parent.stem
    for x in Path(SITE_PATH).glob(
        os.path.join("udmi/devices/*/rsa_private.pem")
    )
]


class MqttClient:
  algorithm = "RS256"
  mqtt_bridge_port = 443
  mqtt_bridge_hostname = "mqtt.bos.goog"
  jwt_exp_mins = 20
  puback_regex = r"^Received PUBACK \(Mid: (\d+)\)"
  topic_regex = r"^/devices/(.*)/config"

  def __init__(self, site_path, project_id, device_id):
    self.pubacks = []
    self.device_id = device_id
    self.project_id = project_id
    self.private_key_file = os.path.join(
        site_path, "devices", device_id, "rsa_private.pem"
    )

    with open(
        os.path.join(site_path, "cloud_iot_config.json"), encoding="utf-8"
    ) as f:
      cloud_iot_config = json.load(f)

    self.registry_id = cloud_iot_config.get("registry_id")
    self.cloud_region = cloud_iot_config.get("cloud_region")

    self.configs = {}

    self.client = self.start_client()

  def subscribe_proxy_config(self, proxy_id):
    self.client.subscribe(f"/devices/{proxy_id}/config", qos=1)

  def attach_device(self, device_id) -> int:
    """Returns mid"""
    attach_topic = f"/devices/{device_id}/attach"
    attach_payload = '{{"authorization" : "{}"}}'
    result = self.client.publish(attach_topic, attach_payload, qos=1)
    return result.mid

  def config(self, device_id):
    return self.configs.get(device_id, None)

  def puback(self, mid):
    print(self.pubacks)
    return mid in self.pubacks

  def start_client(self):

    client_id = "projects/{}/locationss/{}/registries/{}/devices/{}".format(
        self.project_id, self.cloud_region, self.registry_id, self.device_id
    )

    client = mqtt.Client(client_id=client_id, clean_session=False)
    client.username_pw_set(username="unused", password=self.create_jwt())
    client.tls_set(tls_version=ssl.PROTOCOL_TLS_CLIENT)
    client.on_connect = self.on_connect
    client.on_disconnect = self.on_disconnect
    client.on_message = self.on_message
    client.on_log = self.on_log

    client.connect(
        self.mqtt_bridge_hostname, self.mqtt_bridge_port, keepalive=60
    )

    client.subscribe(f"/devices/{self.device_id}/config", qos=1)
    client.subscribe(f"/devices/{self.device_id}/errors", qos=0)
    return client

  def create_jwt(self):
    self.jwt_iat = datetime.datetime.now(tz=datetime.timezone.utc)
    token = {
        "iat": self.jwt_iat,
        "exp": datetime.datetime.now(
            tz=datetime.timezone.utc
        ) + datetime.timedelta(minutes=self.jwt_exp_mins),
        "aud": self.project_id,
    }
    with open(self.private_key_file, "r") as f:
      private_key = f.read()
    return jwt.encode(token, private_key, algorithm=self.algorithm)

  def on_log(self, client, ud, level, buf):
    m = re.match(self.puback_regex, buf)
    if m:
      self.pubacks.append(int(m.group(1)))

  def on_connect(self, client, ud, flag, rc):
    print(f"on_connect {mqtt.connack_string(rc)}")
    assert rc == 0, mqtt.connack_string(rc)

  def on_disconnect(self, client, userdata, rc):
    print(f"on_disconnect {mqtt.error_string(rc)}")

  def on_message(self, client, userdata, message):
    print(f"receieved {message.topic}")
    print(message.payload.decode("utf-8"))
    if "error" in message.topic:
      raise Exception(message.payload.decode("utf-8"))

    m = re.match(self.topic_regex, message.topic)
    if m:
      self.configs[m.group(1)] = json.loads(message.payload.decode("utf-8"))


def until_true(func: Callable, message: str, **kwargs):
  """Blocks until given func returns True

  Raises:
    Exception if timeout has elapsed
  """
  timeout = kwargs.get("timeout", 0)
  interval = kwargs.get("interval", 0.1)

  expiry_time = time.time() + timeout
  while time.time() < expiry_time or timeout == 0:
    if func():
      return True
    if "do" in kwargs:
      kwargs["do"]()
    time.sleep(interval)
  raise Exception(f"Timed out waiting {timeout}s for {message}")


def dict_paths(thing: dict, stem: str = "") -> Iterator[str]:
  """Returns json paths (in dot notation) from a given dictionary"""
  for k, v in thing.items():
    path = f"{stem}.{k}" if stem else k
    if isinstance(v, dict):
      yield from dict_paths(v, path)
    else:
      yield path


def normalize_keys(target, replacement, *args):
  for k, v in target.items():
    if k in args:
      target[k] = replacement
    elif isinstance(v, dict):
      normalize_keys(v, replacement, *args)
  return target


def run(cmd: str) -> str:
  return subprocess.run(cmd, shell=True, capture_output=True, cwd=SITE_PATH)


def is_registrar_done() -> bool:
  run("git pull")
  history_files = list(Path(SITE_PATH).glob("udmi/history/*.json"))

  # we've deleted the history so there will only be one file
  assert len(history_files) <= 1

  if history_files:
    # currently file is empty at start, and only written to at the end
    return history_files[0].stat().st_size > 0


@pytest.fixture
def random_size_site_model():
  devices_count = random.randint(1, 40)
  device_prefix = "AHU"
  base_device = f"{device_prefix}-1"

  metadata_path = os.path.join(
      SITE_PATH, "udmi/devices", base_device, "metadata.json"
  )
  public_key_file_path = os.path.join(
      SITE_PATH, "udmi/devices", base_device, "rsa_public.pem"
  )
  private_key_file_path = os.path.join(
      SITE_PATH, "udmi/devices", base_device, "rsa_private.pem"
  )

  with open(metadata_path, encoding="utf-8") as f:
    base_metadata = json.load(f)

  print(base_metadata)

  desired_devices = [f"{device_prefix}-{i}" for i in range(2, devices_count)]

  existing_devices = [os.stem for x in Path(SITE_PATH).glob("udmi/devices/*")]
  for existing_device in existing_devices:
    if existing_device != base_device:
      shutil.rmtree(os.path.join(SITE_PATH, "udmi/devices", existing_device))

  for device_id in desired_devices:

    device_path = os.path.join(SITE_PATH, "udmi/devices", device_id)
    metadata = normalize_keys(copy.copy(base_metadata), device_id, "name")
    print(metadata)

    os.mkdir(device_path)

    with open(
        os.path.join(device_path, "metadata.json"), mode="w", encoding="utf-8"
    ) as f:
      json.dump(metadata, f)

    shutil.copy(
        public_key_file_path, os.path.join(device_path, "rsa_public.pem")
    )
    shutil.copy(
        private_key_file_path, os.path.join(device_path, "rsa_private.pem")
    )
  yield


def git_flow():
  run("git pull")

  # delete history for ease of tracking
  try:
    shutil.rmtree(os.path.join(SITE_PATH, "udmi/history/"))
  except FileNotFoundError:
    pass

  Path(os.path.join(SITE_PATH, "run-registrar")).touch()

  cmd = run(f"git add -A")
  cmd = run(f'git commit -m "run registrar"')
  cmd = run(f"git push -o nokeycheck")

  # wait until registrar is complete
  until_true(is_registrar_done, "registrar to complete", timeout=3600)


def proxy_id(x: int) -> str:
  return "".join([chars[int(x)] for x in str(x)]).rjust(4, "A")


def gateway_site_model():
  # generate random gateway site model
  gateways = {
      f"GAT-{i}": [f"{proxy_id(i)}-{x}" for x in range(1, random.randint(2, 5))]
      for i in range(1, random.randint(2, 5))
  }

def test_git():
  git_flow()


def test_random_site_model(random_size_site_model):
  git_flow()


def test_random_gateway(random_size_site_model):
  git_flow()


@pytest.mark.parametrize("device_id", devices_list)
def test_device(device_id):
  site_path = os.path.join(SITE_PATH, "udmi/")
  project_id = "bos-platform-prod"

  with open(
      os.path.join(
          site_path, "devices", device_id, "out/generated_config.json"
      ),
      encoding="utf-8",
  ) as f:
    expected_config = json.load(f)

  device = MqttClient(site_path, project_id, device_id)

  until_true(
      lambda: device.config(device_id) is not None,
      "device recieved config",
      do=lambda: device.client.loop(),
      timeout=5,
  )

  config = copy.deepcopy(device.config(device_id))

  # timestamp and version gets set by the cloud so normalise these
  # 'operation' because new?
  normalized_config = normalize_keys(config, REDACTED_VALUE, *KEYS_TO_REDACT)
  normalized_expected = normalize_keys(
      expected_config, REDACTED_VALUE, *KEYS_TO_REDACT
  )

  assert normalized_config == normalized_expected

  metadata_path = os.path.join(site_path, "devices", device_id, "metadata.json")

  with open(metadata_path, encoding="utf-8") as f:
    metadata = json.load(f)

  proxy_ids = metadata.get("gateway", {}).get("proxy_ids", [])
  for proxy_id in proxy_ids:
    mid = device.attach_device(proxy_id)
    print(mid)
    until_true(
        lambda: device.puback(mid),
        f"puback for attach {proxy_id}",
        do=lambda: device.client.loop(),
        timeout=5,
    )

    device.subscribe_proxy_config(proxy_id)
    until_true(
        lambda: device.config(proxy_id) is not None,
        f"device {device_id} recieved config",
        do=lambda: device.client.loop(),
        timeout=5,
    )

    with open(
        os.path.join(
            site_path, "devices", proxy_id, "out/generated_config.json"
        ),
        encoding="utf-8",
    ) as f:
      expected_config = json.load(f)
    config = copy.deepcopy(device.config(proxy_id))

    # timestamp and version gets set by the cloud so normalise these
    # operation because last_start, new, etc
    normalized_config = normalize_keys(config, REDACTED_VALUE, *KEYS_TO_REDACT)
    normalized_expected = normalize_keys(
        expected_config, REDACTED_VALUE, *KEYS_TO_REDACT
    )
    print(normalized_config)
    print(normalized_expected)

    assert normalized_config == normalized_expected
