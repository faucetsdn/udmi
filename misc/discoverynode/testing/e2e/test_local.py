from collections.abc import Callable
import contextlib
import copy
import datetime
import glob
import json
import json
from logging import error, info, warning
import os
from pathlib import Path
import random
import re
import shlex
import shutil
import shutil
import signal
import ssl
import subprocess
import sys
import textwrap
import time
import time
from typing import Any
from typing import Any
from typing import Iterator
import re
import pytest
from typing import Final

ROOT_DIR = os.path.dirname(__file__)
UDMI_DIR = str(Path(__file__).parents[4])

SITE_PATH: Final = os.environ["DN_SITE_PATH"]
error(SITE_PATH)
TARGET: Final = "//mqtt/localhost"
PROJECT_ID: Final = "localhost"

# Some light hardcoding exists, so this is only guaranteed to work with localhost currently
assert os.environ["DN_TARGET"] == TARGET

# Assume the UDMI Directory is the UDMI directory and has not moved
assert UDMI_DIR.rsplit("/", 1)[1] == "udmi"


def until_true(func: Callable, message: str, *, timeout = 0, interval = 0.1, **kwargs):
  """Blocks until given func returns True

  Raises:
    Exception if timeout has elapsed
  """

  expiry_time = time.time() + timeout
  while time.time() < expiry_time or timeout == 0:
    if func():
      return True
    if "do" in kwargs:
      kwargs["do"]()
    time.sleep(interval)
  raise Exception(f"Timed out waiting {timeout}s for {message}")


def dict_paths(thing: dict[str:Any], stem: str = "") -> Iterator[str]:
  """Returns json paths (in dot notation) from a given dictionary."""
  for k, v in thing.items():
    path = f"{stem}.{k}" if stem else k
    if isinstance(v, dict):
      yield from dict_paths(v, path)
    else:
      yield path


def normalize_keys(target: dict[Any:Any], replacement, *args):
  """Replaces value of given keys in a nested dictionary with given replacement."""
  for k, v in target.items():
    if k in args:
      target[k] = replacement
    elif isinstance(v, dict):
      normalize_keys(v, replacement, *args)
  return target


def localnet_block_from_id(id: int):
  """Generates localnet block f"""
  if id > 250:
    # because IP allocation and mac address assignment
    raise RuntimeError("more than 250 devices not supported")

  return {
      "ipv4": {"addr": f"192.168.11.{id}"},
      "ether": {"addr": f"00:00:aa:bb:cc:{id:02x}"},
      "bacnet": {"addr": str(3000 + id)},
      "vendor": {"addr": str(id)},
  }


def run(cmd: str) -> subprocess.CompletedProcess:
  """Runs the given command inside the UDMI directory and wait for it to complete"""
  #
  # stdout=sys.stdout, stderr=sys.stderr
  info("executing: %s", cmd)
  start = time.monotonic()
  result = subprocess.run(
      cmd,
      shell=True,
      stdout=subprocess.PIPE, #sys.stdout
      stderr=subprocess.STDOUT, #sys.stderr
      cwd=UDMI_DIR,
  )
  execution_time_seconds = time.monotonic() - start
  info("completed with result code %s in %s seconds", str(result.returncode), str(execution_time_seconds))
  # print not log, so they are captured when there is a failure
  print("-----")
  print(cmd)
  print(result.stdout.decode("utf-8"))
  print("-----")
  return result


@pytest.fixture
def docker_devices():
  def _docker_devices(*, devices):
    for i in devices:
      localnet = localnet_block_from_id(i)
      run(
          shlex.join([
              "docker",
              "run",
              "--rm",
              "-d",
              f"--name=discoverynode-test-device{i}",
              f"--network=discoverynode-network",
              f"--ip={localnet['ipv4']['addr']}",
              "-e",
              f"BACNET_ID={localnet['bacnet']['addr']}",
              "test-bacnet-device",
          ])
      )

  yield _docker_devices

  result = run("docker logs discoverynode-test-device1")
  print("discovery node logs")
  print(result.stdout.decode("utf-8"))
  run(
      "docker ps -a | grep 'discoverynode-test-device' | awk '{print $1}' |"
      " xargs docker stop"
  )


@pytest.fixture
def discovery_node():

  def _discovery_node(
      *, device_id, site_path
  ):

    with open(
      os.path.join(site_path, "cloud_iot_config.json"), encoding="utf-8"
    ) as f:
      cloud_iot_config = json.load(f)
    
    device_directory = os.path.join(site_path, "devices", device_id)
    with open(
      os.path.join(device_directory, "metadata.json"), encoding="utf-8"
    ) as f:
      metadata = json.load(f)

    config = {
        "mqtt": {
            "device_id": device_id,
            "host": "192.168.99.2",
            "port": 8883,
            "registry_id": cloud_iot_config["registry_id"],
            "region": "us-central1",
            "project_id": PROJECT_ID,
            "key_file": "rsa_private.pem",
            "ca_file": "ca.crt",
            "cert_file": "rsa_private.crt",
            "algorithm": metadata["cloud"]["auth_type"],
            "authentication_mechanism": "udmi_local",
        },
        "nmap": {"targets": ["127.0.0.1"], "interface": "eth0"},
        "bacnet": {"ip": "192.168.11.251"},
    }

    with open(
        os.path.join(ROOT_DIR, "discovery_node_config.json"),
        mode="w",
        encoding="utf-8",
    ) as f:
      json.dump(config, f, indent=2)

    run(
        shlex.join([
            "docker",
            "run",
            "-d",
            f"--name=discoverynode-test-node",
            f"--network=discoverynode-network",
            "--mount",
            f"type=bind,source={ROOT_DIR}/discovery_node_config.json,target=/app/config.json",
            "--mount",
            f"type=bind,source={site_path}/devices/{device_id}/rsa_private.pem,target=/app/rsa_private.pem",
            "--mount",
            f"type=bind,source={site_path}/devices/{device_id}/rsa_private.crt,target=/app/rsa_private.crt",
            "--mount",
            f"type=bind,source={site_path}/devices/{device_id}/rsa_private.pkcs8,target=/app/rsa_private.pkcs8",
            "--mount",
            f"type=bind,source={site_path}/reflector/ca.crt,target=/app/ca.crt",
            "--ip=192.168.11.251",
            "test-discovery_node",
            "--config_file=config.json",
        ])
    )

    run("docker network connect udminet discoverynode-test-node")

  yield _discovery_node

  run("docker stop discoverynode-test-node")
  logs = run("docker logs discoverynode-test-node")
  print(logs.stdout.decode("utf-8"))
  run("docker rm discoverynode-test-node")


def test_discovered_proxied_devices_are_created(
    new_site_model, docker_devices, discovery_node
):

  new_site_model(
      site_path=SITE_PATH,
      delete=True,
      devices=range(0),
      devices_with_localnet_block=range(0),
      discovery_node_id="GAT-1",
      discovery_node_is_gateway=True,
      discovery_node_families=["bacnet"],
  )

  docker_devices(devices=range(1, 10))

  info("deleting all devices")

  run(f"bin/registrar {SITE_PATH} {TARGET} -d -x")

  run(f"bin/registrar {SITE_PATH} {TARGET}")

  # Note: Start after running registrar preferably
  discovery_node(
      device_id="GAT-1",
      site_path=SITE_PATH
  )

  run("bin/mapper GAT-1 provision")

  time.sleep(5)

  run("bin/mapper GAT-1 discover")

  time.sleep(30)

  run(f"bin/registrar {SITE_PATH} {TARGET}")

  site_model = Path(SITE_PATH)

  extra_devices = list([x.stem for x in site_model.glob("extras/*")])
  assert len(extra_devices) == 9, "found exactly 9 devices"

def test_discovered_devices_are_created(
    new_site_model, docker_devices, discovery_node
):

  new_site_model(
      site_path=SITE_PATH,
      delete=True,
      devices=range(0),
      devices_with_localnet_block=range(0),
      discovery_node_id="AHU-1",
      discovery_node_is_gateway=False,
      discovery_node_families=["bacnet"],
  )

  docker_devices(devices=range(1, 7))

  info("deleting all devices")

  run(f"bin/registrar {SITE_PATH} {TARGET} -d -x")

  run(f"bin/registrar {SITE_PATH} {TARGET}")

  # Note: Start after running registrar preferably
  discovery_node(
      device_id="AHU-1",
      site_path=SITE_PATH
  )

  run("bin/mapper AHU-1 provision")

  time.sleep(5)

  run("bin/mapper AHU-1 discover")

  time.sleep(30)

  run(f"bin/registrar {SITE_PATH} {TARGET}")

  site_model = Path(SITE_PATH)
  extra_devices = list([x.stem for x in site_model.glob("extras/*")])
  assert len(extra_devices) == 6, "found exactly 6 devices"

def test_sequencer(new_site_model, docker_devices, discovery_node):

  new_site_model(
      site_path=SITE_PATH,
      delete=True,
      # Because the sample rate in the test is 10 seconds and 11 results are produced (1 - 11 inclusive)
      devices=range(1, 12),
      devices_with_localnet_block=range(1, 12),
      discovery_node_id="GAT-1",
      discovery_node_is_gateway=True,
      discovery_node_families=["vendor"],
  )

  run(f"bin/registrar {SITE_PATH} {TARGET} -d -x")

  run(f"bin/registrar {SITE_PATH} {TARGET}")

  # Note: Start after running registrar preferably.
  discovery_node(
      device_id="GAT-1",
      site_path=SITE_PATH
  )

  result = run(
      f"bin/sequencer -v {SITE_PATH} {TARGET} GAT-1 scan_single_future"
  )

  print("sequencer output")
  print(result.stdout.decode("utf8"))
  assert "RESULT pass discovery.scan scan_single_future" in str(result.stdout), "result is pass (note this test can be flakey)"


@pytest.fixture
def new_site_model():

  def _new_site_model(
      *,
      delete,
      site_path,
      devices,
      devices_with_localnet_block,
      discovery_node_id,
      discovery_node_is_gateway,
      discovery_node_families,
  ):
    devices_directory = os.path.join(site_path, "devices")
    device_prefix = "DDC"
    
    if delete:
      with contextlib.suppress(FileNotFoundError):
        shutil.rmtree(devices_directory)

    os.mkdir(devices_directory)

    ##########################

    with open(
      os.path.join(site_path, "cloud_iot_config.json"), encoding="utf-8"
    ) as f:
      cloud_iot_config = json.load(f)
    name = cloud_iot_config["registry_id"]

    ##########################

    # Create gateway
    os.mkdir(os.path.join(devices_directory, discovery_node_id))
    gateway_metadata = {
        "system": {
            "location": {"section": "2-3N8C"},
            "physical_tag": {
                "asset": {
                    "guid": "drw://TBB",
                    "site": "ZZ-TRI-FECTA",
                    "name": discovery_node_id,
                }
            },
        },
        "discovery": {"families": {}},
        "cloud": {"auth_type": "RS256"},
        "version": "1.5.1",
        "timestamp": "2020-05-01T13:39:07Z",
    }

    if discovery_node_families:
      warning("setting scan target to 0th index of discovery_node_families: %s", discovery_node_families[0])
      gateway_metadata["testing"] = {"targets": {"scan_family": {"target_value": discovery_node_families[0]}}}

    for family in discovery_node_families:
      gateway_metadata["discovery"]["families"][family] = {}

    if discovery_node_is_gateway:
      gateway_metadata["gateway"] = {"proxy_ids": []}

    gateway_path = os.path.join(devices_directory, discovery_node_id)

    with open(
        os.path.join(gateway_path, "metadata.json"), mode="w", encoding="utf-8"
    ) as f:
      json.dump(gateway_metadata, f, indent=2)
    
    print(f"gateway metadata: {json.dumps(gateway_metadata, indent=2)}")

    run(f"bin/keygen RS256 {gateway_path}")
    run(f"bin/keygen CERT/localhost {gateway_path}")
    ##############################

    for i in devices:
      device_id = f"{device_prefix}-{i}"
      device_path = os.path.join(site_path, "devices", device_id)
      os.mkdir(device_path)

      device_metadata = {
          "system": {
              "location": {"section": "2-3N8C", "site": name},
              "physical_tag": {
                  "asset": {
                      "guid": "drw://TBB",
                      "site": name,
                      "name": device_id,
                  }
              },
          },
          "cloud": {"auth_type": "RS256"},
          "version": "1.5.1",
          "timestamp": "2020-05-01T13:39:07Z",
      }

      if i in devices_with_localnet_block:
        device_metadata["localnet"] = {}
        device_metadata["localnet"]["families"] = localnet_block_from_id(i)

      with open(
          os.path.join(device_path, "metadata.json"), mode="w", encoding="utf-8"
      ) as f:
        json.dump(device_metadata, f, indent=2)

      warning("created device %s without keys", device_id)

  yield _new_site_model


def proxy_id(x: int) -> str:
  return "".join([chr[int(x)] for x in str(x)]).rjust(4, "Ack")


def gateway_site_model():
  # generate random gateway site model
  gateways = {
      f"GAT-{i}": [f"{proxy_id(i)}-{x}" for x in range(1, random.randint(2, 5))]
      for i in range(1, random.randint(2, 5))
  }
