"""Multithreaded UDMI client for multiple AHU devices."""

import argparse
import concurrent.futures
import datetime
import glob
import hashlib
import json
import logging
import os
import random
import ssl
import sys
import threading
import time
from typing import Any, Callable
from unittest import mock

import jwt
import paho.mqtt.client
import paho.mqtt.enums


class MQTT:
  """MQTT client incorporated directly from udmi.publishers.mqtt."""

  def __init__(
      self,
      *,
      device_id: str,
      registry_id: str,
      region: str,
      project_id: str,
      hostname: str,
      port: int,
      key_file: str,
      public_key_file: str,
      algorithm: str,
      topic_prefix: str,
      jwt_minutes: int = 60,
      ca_file: str | None = None,
      cert_file: str | None = None,
      autentication_mechanism: str = "jwt_gcp",
  ):
    self.public_key_printed_last_time = 0
    self.public_key_print_interval_seconds: int = 3600  # s
    self.public_key_file: str = public_key_file
    self.public_key: str | None = None

    self.logger = logging.getLogger("mqtt_client")
    self.logger.setLevel(logging.DEBUG)
    self.config_callback = None

    self.device_id = device_id
    self.registry_id = registry_id
    self.topic_prefix = topic_prefix
    self.mqtt_host = hostname
    self.mqtt_port = port

    self.private_key_file = key_file
    self.jwt_exp_mins = jwt_minutes
    self.project_id = project_id
    self.algorithm = algorithm

    self.ca_file = ca_file
    self.cert_file = cert_file
    self.autentication_mechanism = autentication_mechanism

    match autentication_mechanism:
      case "jwt_gcp":
        self.client_id = f"projects/{project_id}/locations/{region}/registries/{registry_id}/devices/{device_id}"
        self.username = "unused"
      case "udmi_local":
        self.username = f"/r/{registry_id}/d/{device_id}"
        self.client_id = self.username
        pkcs_file = f"{key_file.rpartition('.')[0]}.pkcs8"
        with open(pkcs_file, "rb") as f:
          key_bytes = f.read()
          hash_val = hashlib.sha256(key_bytes).hexdigest()
          self.password = hash_val[:8]
          print(self.password)
      case _:
        raise RuntimeError("unknown authentication mechanism")

    self.make_client()

  def _create_jwt(self):
    self.logger.info("[%s] Creating JWT", self.device_id)
    jwt_iat = datetime.datetime.now(tz=datetime.timezone.utc)
    self.jwt_expiry = jwt_iat + datetime.timedelta(minutes=self.jwt_exp_mins)
    token = {
        "iat": jwt_iat,
        "exp": self.jwt_expiry,
        "aud": self.project_id,
    }

    with open(self.private_key_file, "r") as f:
      private_key = f.read()

    return jwt.encode(token, private_key, algorithm=self.algorithm)

  def on_connect(
      self,
      client: paho.mqtt.client.Client,
      userdata: Any,
      flags: paho.mqtt.client.ConnectFlags,
      reason_code: paho.mqtt.client.ReasonCode,
      properties: paho.mqtt.client.Properties,
  ):
    del client, userdata, flags, properties  # Unused, part of callback API.

    self.logger.info("[%s] Connected (reason code: %s)", self.device_id, reason_code)
    if reason_code == 0:
      res, mid = self.client.subscribe(f"{self.topic_prefix}/config", qos=1)
      if res == paho.mqtt.client.MQTT_ERR_SUCCESS:
        logging.info("[%s] Requested config subscription (mid: %s)", self.device_id, mid)
      else:
        logging.error("[%s] Failed to request config subscription (error code: %s)", self.device_id, res)
    else:
      self.print_public_key()

  def on_disconnect(
      self,
      client: paho.mqtt.client.Client,
      userdata: Any,
      disconnect_flags: paho.mqtt.client.DisconnectFlags,
      reason_code: paho.mqtt.client.ReasonCode,
      properties: paho.mqtt.client.Properties,
  ):
    del (
        client,
        userdata,
        disconnect_flags,
        properties,
    )  # Unused, part of callback API.

    if reason_code == 0:
      self.logger.info("[%s] Disconnected (reason code: %s)", self.device_id, reason_code)
    else:
      self.logger.error("[%s] Disconnected with error (reason code: %s)", self.device_id, reason_code)

  def on_pre_connect(self, client, userdata):
    del userdata  # Unused, part of callback API.
    self.logger.info("[%s] Connecting", self.device_id)
    if self.autentication_mechanism == "jwt_gcp":
      client.username_pw_set(username="unused", password=self._create_jwt())
    else:
      client.username_pw_set(username=self.username, password=self.password)

  def on_message(
      self,
      client: paho.mqtt.client.Client,
      userdata: Any,
      message: paho.mqtt.client.MQTTMessage,
  ):
    del client, userdata  # Unused, part of callback API.

    self.logger.info("[%s] Received config", self.device_id)

    if self.config_callback:
      self.logger.debug("[%s] Dispatched config to callback", self.device_id)
      self.config_callback(message.payload.decode("utf-8"))
    else:
      self.logger.warning("[%s] No config callback", self.device_id)

  def on_subscribe(
      self,
      client: paho.mqtt.client.Client,
      userdata: Any,
      mid: int,
      reason_codes: list[paho.mqtt.client.ReasonCode],
      properties: paho.mqtt.client.Properties,
  ):
    del client, userdata, properties

    for rc in reason_codes:
      if rc >= 128:
        logging.error("[%s] Subscription failed (mid: %s, reason code: %s)", self.device_id, mid, rc)
      else:
        logging.info("[%s] Subscription successful (mid: %s, granted QoS: %s)", self.device_id, mid, rc)

  def make_client(self):
    """Creates MQTT client."""
    client = paho.mqtt.client.Client(
        client_id=self.client_id,
        clean_session=True,
        callback_api_version=paho.mqtt.enums.CallbackAPIVersion.VERSION2,
    )

    if self.autentication_mechanism == "jwt_gcp":
      client.tls_set(self.ca_file, tls_version=ssl.PROTOCOL_TLSv1_2)
    elif self.autentication_mechanism == "udmi_local":
      client.tls_set(
          self.ca_file,
          tls_version=ssl.PROTOCOL_TLSv1_2,
          keyfile=self.private_key_file,
          certfile=self.cert_file,
      )
      client.tls_insecure_set(True)
    else:
      raise RuntimeError("unknown authentication mechanism")

    client.on_connect = self.on_connect
    client.on_disconnect = self.on_disconnect
    client.on_message = self.on_message
    client.on_pre_connect = self.on_pre_connect
    client.on_subscribe = self.on_subscribe

    self.client = client

  def start_client(self):
    """Starts the MQTT client."""
    self.client.connect(self.mqtt_host, self.mqtt_port, keepalive=60)
    self.client.loop_start()
    if hasattr(self.client, "_thread") and self.client._thread:
      self.client._thread.name = f"MQTT_{self.device_id}"

  def set_config_callback(self, callback: Callable[[str], None]):
    self.config_callback = callback

  def publish_message(self, topic, message):
    logging.debug("[%s] Publishing to: %s", self.device_id, topic)
    return self.client.publish(topic, message)

  def print_public_key(self) -> None:
    if (
        time.monotonic() - self.public_key_printed_last_time
        > self.public_key_print_interval_seconds
    ):
      if not self.public_key:
        with open(self.public_key_file, "r") as f:
          self.public_key = f.read()
      logging.warning(
          "[%s] Not authenticated. Public key: %s",
          self.device_id,
          self.public_key.encode("utf-8"),
      )
      self.public_key_printed_last_time = time.monotonic()


def get_arguments():
  parser = argparse.ArgumentParser(
      description="Start multithreaded UDMI Client for AHUs"
  )

  parser.add_argument(
      "site_model_path", type=str, help="path to the site model directory"
  )
  parser.add_argument(
      "interval", type=float, help="interval in seconds between published messages"
  )
  parser.add_argument(
      "-all", "--all", action="store_true", help="Load all devices with keys in the site model"
  )
  parser.add_argument(
      "--devices", type=str, help="Comma separated list of devices to use"
  )
  parser.add_argument(
      "--batch-size", type=int, default=50, help="Number of devices to assign per thread (default: 50)"
  )
  parser.add_argument(
      "--proxy-ids", type=str, help="Comma separated list of proxy device IDs to use (overrides metadata.json)"
  )
  args = parser.parse_args()

  if not args.all and not args.devices:
    parser.error("Either -all or --devices must be specified.")

  return args


def run_device_batch(device_ids_batch, site_model_path, interval, shutdown_event, proxy_ids_override=None, registry_id="AA-BIG-TEST"):
  """Initializes and runs a batch of UDMI devices in a single thread."""
  clients = {}

  for device_id in device_ids_batch:
    if shutdown_event.is_set():
      break

    device_dir = os.path.join(site_model_path, "devices", device_id)
    if not os.path.exists(device_dir):
      device_dir = os.path.join(site_model_path, "device", device_id)

    topic_prefix = f"/devices/{device_id}"

    if proxy_ids_override is not None:
      proxy_ids = proxy_ids_override
    else:
      proxy_ids = []
      metadata_path = os.path.join(device_dir, "metadata.json")
      if os.path.exists(metadata_path):
        try:
          with open(metadata_path, "r") as f:
            metadata = json.load(f)
          if "gateway.proxy_ids" in metadata:
            proxy_ids = metadata["gateway.proxy_ids"]
          elif "gateway" in metadata and "proxy_ids" in metadata["gateway"]:
            proxy_ids = metadata["gateway"]["proxy_ids"]
          if not isinstance(proxy_ids, list):
            proxy_ids = []
        except Exception as e:
          logging.warning("[%s] Error reading metadata.json: %s", device_id, e)

    try:
      # Initialize MQTT Publisher
      mclient = MQTT(
          device_id=device_id,
          registry_id=registry_id,
          region="us-central1",
          project_id="bos-platform-dev",
          hostname="mqtt.bos.goog",
          port=8883,
          topic_prefix=topic_prefix,
          key_file=os.path.join(device_dir, "rsa_private.pem"),
          public_key_file=os.path.join(device_dir, "rsa_public.pem"),
          algorithm="RS256",
          autentication_mechanism="jwt_gcp",
          ca_file="/usr/local/google/home/elsaidi/roots.pem",
      )

      # Override on_connect to also subscribe to proxy config topics
      orig_on_connect = mclient.client.on_connect
      def custom_on_connect(client, userdata, flags, reason_code, properties, p_ids=proxy_ids, dev_id=device_id, orig_func=orig_on_connect):
        orig_func(client, userdata, flags, reason_code, properties)
        if reason_code == 0:
          for proxy_id in p_ids:
            res, mid = client.subscribe(f"/devices/{proxy_id}/config", qos=1)
            if res == paho.mqtt.client.MQTT_ERR_SUCCESS:
              logging.info("[%s] Requested proxy config subscription (mid: %s)", proxy_id, mid)
            else:
              logging.error("[%s] Failed to request proxy config subscription (error code: %s)", proxy_id, res)
      mclient.client.on_connect = custom_on_connect

      # Override on_message to log success or error without printing message payload
      def custom_on_message(client, userdata, message):
        try:
          _ = message.payload.decode("utf-8")
          logging.info("[%s] Successfully received config", device_id)
        except Exception as err:
          logging.error("[%s] Error processing config message: %s", device_id, err)
      mclient.client.on_message = custom_on_message

      mclient.start_client()
      logging.info("[%s] Started client", device_id)
      clients[device_id] = (mclient, proxy_ids)
    except Exception as e:
      logging.exception("[%s] Error in device setup/connection: %s", device_id, e)

  if not clients:
    logging.warning("No successful MQTT clients initialized in this batch thread. Exiting thread.")
    return

  # 40 KB target size (40 * 1024 bytes)
  target_bytes = 40960

  # Loop until the shutdown event is triggered
  while not shutdown_event.is_set():
    start_time = time.time()

    for device_id, (mclient, proxy_ids) in clients.items():
      if shutdown_event.is_set():
        break

      for target_id in [device_id] + proxy_ids:
        try:
          # Base message structure
          message = {
              "version": "1.5.2",
              "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
              "points": {
                  "padding": {
                      "present_value": ""
                  }
              }
          }

          # Calculate how much padding is needed to hit exactly 40KB
          base_json_str = json.dumps(message)
          base_size = len(base_json_str.encode("utf-8"))

          padding_size = target_bytes - base_size
          if padding_size > 0:
            message["points"]["padding"]["present_value"] = "x" * padding_size

          payload_str = json.dumps(message)

          pub_info = mclient.publish_message(f"/devices/{target_id}/events/pointset", payload_str)
          if pub_info.rc == paho.mqtt.client.MQTT_ERR_SUCCESS:
            logging.info("[%s] Successfully published message", target_id)
          else:
            logging.error("[%s] Failed to publish message (return code: %s)", target_id, pub_info.rc)

        except Exception as pub_err:
          logging.error("[%s] Error publishing message: %s", target_id, pub_err)

      # Add a random delay between messages across the devices in the batch.
      # To ensure the total batch publishing time doesn't exceed the interval,
      # we scale the max delay based on the interval and number of devices in the batch.
      max_delay = min(0.5, interval / (len(clients) * 2.0) if len(clients) > 0 else 0.1)
      delay = random.uniform(0.01, max_delay)
      shutdown_event.wait(delay)

    if shutdown_event.is_set():
      break

    # Wait for the remaining time of the interval
    elapsed = time.time() - start_time
    remaining_time = max(0, interval - elapsed)
    if remaining_time > 0:
      shutdown_event.wait(remaining_time)

  logging.info("Shutting down batch thread safely.")


def main():
  # Configure logging
  stdout = logging.StreamHandler(sys.stdout)
  stdout.addFilter(lambda log: log.levelno < logging.WARNING)
  stdout.setLevel(logging.INFO)
  stderr = logging.StreamHandler(sys.stderr)
  stderr.setLevel(logging.WARNING)

  logging.basicConfig(
      format="%(asctime)s|%(levelname)s|%(threadName)s|%(module)s:%(funcName)s %(message)s",
      handlers=[stderr, stdout],
      level=logging.INFO,
      force=True,
  )
  logging.root.setLevel(logging.INFO)

  args = get_arguments()
  proxy_ids_override = [p.strip() for p in args.proxy_ids.split(",") if p.strip()] if args.proxy_ids else None

  registry_id = "AA-BIG-TEST"
  cloud_iot_config_path = os.path.join(args.site_model_path, "cloud_iot_config.json")
  if os.path.exists(cloud_iot_config_path):
    try:
      with open(cloud_iot_config_path, "r") as f:
        cloud_config = json.load(f)
      if "registry_id" in cloud_config:
        registry_id = cloud_config["registry_id"]
      logging.info("Read registry_id '%s' from %s", registry_id, cloud_iot_config_path)
    except Exception as e:
      logging.warning("Error reading cloud_iot_config.json: %s", e)
  else:
    logging.warning("cloud_iot_config.json not found at %s. Using default registry_id: %s", cloud_iot_config_path, registry_id)

  device_ids = []
  if args.devices:
    # Comma separated list of devices
    candidate_devices = [d.strip() for d in args.devices.split(",") if d.strip()]
    for d in candidate_devices:
      pub_key_path_devices = os.path.join(args.site_model_path, "devices", d, "rsa_public.pem")
      pub_key_path_device = os.path.join(args.site_model_path, "device", d, "rsa_public.pem")
      if os.path.exists(pub_key_path_devices) or os.path.exists(pub_key_path_device):
        device_ids.append(d)
      else:
        logging.warning("Skipping device %s: public key not found at %s or %s", d, pub_key_path_devices, pub_key_path_device)
  elif args.all:
    # Glob all devices with rsa_public.pem
    glob_path_devices = os.path.join(args.site_model_path, "devices", "*", "rsa_public.pem")
    glob_path_device = os.path.join(args.site_model_path, "device", "*", "rsa_public.pem")
    matches = glob.glob(glob_path_devices) + glob.glob(glob_path_device)
    for pub_key_path in matches:
      device_id = os.path.basename(os.path.dirname(pub_key_path))
      device_ids.append(device_id)

  # Sort device_ids for consistency and remove duplicates
  device_ids = sorted(list(set(device_ids)))

  if not device_ids:
    logging.error("No devices with valid keys found to simulate. Exiting.")
    sys.exit(1)

  # Split device_ids into batches of size args.batch_size
  batches = [device_ids[i:i + args.batch_size] for i in range(0, len(device_ids), args.batch_size)]

  logging.info(
      "Starting %d devices across %d batches (threads) with a publish interval of %s seconds",
      len(device_ids),
      len(batches),
      args.interval,
  )

  # Create an event to signal threads to stop
  shutdown_event = threading.Event()

  with concurrent.futures.ThreadPoolExecutor(
      max_workers=len(batches), thread_name_prefix="AHUBatchThread"
  ) as executor:
    futures = {
        executor.submit(
            run_device_batch, batch, args.site_model_path, args.interval, shutdown_event, proxy_ids_override, registry_id
        ): batch
        for batch in batches
    }

    # A non-blocking wait loop.
    # This keeps the main thread breathing and listening for Ctrl+C.
    try:
      while True:
        # Sleep in 1-second chunks so the main thread checks for KeyboardInterrupt
        time.sleep(1)
    except KeyboardInterrupt:
      logging.info("KeyboardInterrupt detected. Signaling threads to shut down...")
      # Set the flag to true. Threads will see this and exit their loops.
      shutdown_event.set()

    # Now we wait for the threads to finish their current loop and close cleanly
    concurrent.futures.wait(futures)

  logging.info("All threads terminated gracefully.")


if __name__ == "__main__":
  main()
