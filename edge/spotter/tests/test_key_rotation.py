#!/usr/bin/env python3
"""Unit tests for cloud-controlled key rotation over ephemeral download/blobset."""

from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
import stat
import tempfile
import unittest
from unittest.mock import MagicMock

from udmi.core.device import Device
from udmi.core.messaging.mqtt_messaging_client import MqttMessagingClient
from udmi.schema import EndpointConfiguration, Protocol
from udmi.schema.common import Phase


def _canonical_sha256(payload: dict) -> str:
  canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"))
  return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


class TestCloudControlledKeyRotation(unittest.TestCase):
  """Verifies download/blobset cloud-controlled key and credential rotation."""

  def _make_device(self, key_file: str):
    endpoint = EndpointConfiguration(
        protocol=Protocol.mqtt,
        hostname="broker.example.com",
        port=8883,
        client_id="/r/ZZ-TRI-FECTA/d/DSN-1",
        key_file=key_file,
    )
    persistence = MagicMock()
    persistence.get_effective_endpoint.return_value = endpoint
    persistence.get_active_generation.return_value = None
    dispatcher = MagicMock()
    device = Device(
        managers=[],
        endpoint_config=endpoint,
        persistence_manager=persistence,
    )
    device.dispatcher = dispatcher
    device._setup_message_handlers()
    return device, dispatcher, persistence

  def test_download_blobset_registers_and_subscribes_qos_0(self):
    """Verifies Device registers download/# and MqttMessagingClient subscribes with QoS 0."""
    with tempfile.TemporaryDirectory() as tmpdir:
      key_file = os.path.join(tmpdir, "rsa_private.pem")
      _, dispatcher, _ = self._make_device(key_file)
      registered_channels = [
          call.args[0] for call in dispatcher.register_handler.call_args_list
      ]
      self.assertIn("download/#", registered_channels)

      mock_mqtt = MagicMock()
      client = MqttMessagingClient.__new__(MqttMessagingClient)
      client._mqtt_client = mock_mqtt
      client._topic_prefix_str = "/r/ZZ-TRI-FECTA/d"
      client._config = EndpointConfiguration(
          protocol=Protocol.mqtt,
          hostname="broker.example.com",
          client_id="/r/ZZ-TRI-FECTA/d/DSN-1",
      )
      client._connected = True
      client._subscribed_channels = set()
      client.register_channel_subscription("download/blobset")
      mock_mqtt.subscribe.assert_called_once_with(
          "/r/ZZ-TRI-FECTA/d/DSN-1/download/blobset", qos=0
      )

  def test_cloud_controlled_private_key_rotation_happy_path(self):
    """Verifies apply -> download/blobset -> SHA-256 -> atomic key_bytes write -> final -> reset."""
    with tempfile.TemporaryDirectory() as tmpdir:
      key_file = os.path.join(tmpdir, "rsa_private.pem")
      with open(key_file, "w", encoding="utf-8") as f:
        f.write("OLD_PRIVATE_KEY_PEM")

      device, dispatcher, persistence = self._make_device(key_file)
      future_expiry = (
          datetime.now(timezone.utc) + timedelta(hours=24)
      ).strftime("%Y-%m-%dT%H:%M:%SZ")
      new_key_pem = "-----BEGIN PRIVATE KEY-----\nROTATED\n-----END PRIVATE KEY-----\n"

      downloaded_endpoint = {
          "protocol": "mqtt",
          "hostname": "broker.example.com",
          "port": 8883,
          "client_id": "/r/ZZ-TRI-FECTA/d/DSN-1",
          "key_file": key_file,
          "key_bytes": new_key_pem,
          "expiry": future_expiry,
      }
      expected_sha = _canonical_sha256(downloaded_endpoint)
      generation = "2026-11-26T09:00:00Z"

      # 1. Cloud publishes config.blobset with download:// URL and sha256
      config_payload = {
          "timestamp": generation,
          "version": "1.5.7",
          "blobset": {
              "blobs": {
                  "_iot_endpoint_config": {
                      "phase": "final",
                      "generation": generation,
                      "sha256": expected_sha,
                      "url": "download://_iot_endpoint_config",
                  }
              }
          },
      }
      device.handle_config("DSN-1", "config", config_payload)

      # Device must publish state.blobset phase = apply
      self.assertEqual(dispatcher.publish_state.call_count, 1)
      apply_state = dispatcher.publish_state.call_args_list[0].args[0]
      blob_state = apply_state.blobset.blobs["_iot_endpoint_config"]
      self.assertEqual(blob_state.phase, Phase.apply)
      self.assertEqual(blob_state.generation, generation)

      # 2. Cloud delivers ephemeral download/blobset payload
      download_payload = {
          "timestamp": "2026-11-26T09:00:01Z",
          "version": "1.5.7",
          "blobs": {
              "_iot_endpoint_config": downloaded_endpoint,
          },
      }
      device.handle_download("DSN-1", "download/blobset", download_payload)

      # Key file updated atomically with 0600 permissions & backup created
      with open(key_file, "r", encoding="utf-8") as f:
        self.assertEqual(f.read(), new_key_pem)
      mode = stat.S_IMODE(os.stat(key_file).st_mode)
      self.assertEqual(mode, 0o600)
      backups = [p for p in os.listdir(tmpdir) if p.endswith(".bak")]
      self.assertEqual(len(backups), 1)
      with open(os.path.join(tmpdir, backups[0]), "r", encoding="utf-8") as f:
        self.assertEqual(f.read(), "OLD_PRIVATE_KEY_PEM")

      # Persisted endpoint must NOT contain raw key_bytes
      persistence.save_active_endpoint.assert_called_once()
      saved_ep = persistence.save_active_endpoint.call_args.args[0]
      self.assertIsNone(saved_ep.key_bytes)
      self.assertEqual(saved_ep.key_file, key_file)

      # Device published phase = final without error and signaled connection reset
      final_state = dispatcher.publish_state.call_args_list[-1].args[0]
      final_blob = final_state.blobset.blobs["_iot_endpoint_config"]
      self.assertEqual(final_blob.phase, Phase.final)
      self.assertIsNone(final_blob.status)
      self.assertTrue(device._loop_state.reset_event.is_set())

  def test_rejects_sha256_mismatch_and_expired_credential(self):
    """Verifies corrupted SHA-256 or expired credentials fail with status 500 without touching key file."""
    with tempfile.TemporaryDirectory() as tmpdir:
      key_file = os.path.join(tmpdir, "rsa_private.pem")
      with open(key_file, "w", encoding="utf-8") as f:
        f.write("ORIGINAL_KEY")

      device, dispatcher, persistence = self._make_device(key_file)
      gen1 = "2026-11-26T09:05:00Z"
      device.handle_config(
          "DSN-1",
          "config",
          {
              "timestamp": gen1,
              "version": "1.5.7",
              "blobset": {
                  "blobs": {
                      "_iot_endpoint_config": {
                          "phase": "final",
                          "generation": gen1,
                          "sha256": "0" * 64,
                          "url": "download://_iot_endpoint_config",
                      }
                  }
              },
          },
      )
      device.handle_download(
          "DSN-1",
          "download/blobset",
          {
              "timestamp": gen1,
              "version": "1.5.7",
              "blobs": {
                  "_iot_endpoint_config": {
                      "protocol": "mqtt",
                      "hostname": "broker.example.com",
                      "client_id": "/r/ZZ-TRI-FECTA/d/DSN-1",
                      "key_bytes": "BAD_KEY",
                  }
              },
          },
      )
      err_state = dispatcher.publish_state.call_args_list[-1].args[0]
      err_blob = err_state.blobset.blobs["_iot_endpoint_config"]
      self.assertEqual(err_blob.phase, Phase.final)
      self.assertEqual(err_blob.status.level, 500)
      self.assertIn("SHA-256 mismatch", err_blob.status.message)
      persistence.save_active_endpoint.assert_not_called()
      with open(key_file, "r", encoding="utf-8") as f:
        self.assertEqual(f.read(), "ORIGINAL_KEY")

      # Expired credential with valid SHA-256 is also rejected
      expired_ep = {
          "protocol": "mqtt",
          "hostname": "broker.example.com",
          "client_id": "/r/ZZ-TRI-FECTA/d/DSN-1",
          "key_bytes": "EXPIRED_KEY",
          "expiry": "2020-01-01T00:00:00Z",
      }
      gen2 = "2026-11-26T09:10:00Z"
      device.handle_config(
          "DSN-1",
          "config",
          {
              "timestamp": gen2,
              "version": "1.5.7",
              "blobset": {
                  "blobs": {
                      "_iot_endpoint_config": {
                          "phase": "final",
                          "generation": gen2,
                          "sha256": _canonical_sha256(expired_ep),
                          "url": "download://_iot_endpoint_config",
                      }
                  }
              },
          },
      )
      device.handle_download(
          "DSN-1",
          "download/blobset",
          {
              "timestamp": gen2,
              "version": "1.5.7",
              "blobs": {"_iot_endpoint_config": expired_ep},
          },
      )
      exp_state = dispatcher.publish_state.call_args_list[-1].args[0]
      exp_blob = exp_state.blobset.blobs["_iot_endpoint_config"]
      self.assertEqual(exp_blob.phase, Phase.final)
      self.assertEqual(exp_blob.status.level, 500)
      self.assertIn("expired", exp_blob.status.message.lower())
      persistence.save_active_endpoint.assert_not_called()


if __name__ == "__main__":
  unittest.main()
