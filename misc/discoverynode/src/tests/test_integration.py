import collections
import json
import os
import time
from unittest import mock
import pytest
import udmi.core


def test_bacnet_integration():
  try:
    with open("docker_config.json") as f:
      docker_config = json.load(f)
  except FileNotFoundError:
    raise Exception("Test must be run inside a docker container")
  
  test_config = collections.defaultdict()
  test_config["mqtt"] = dict(device_id="THUNDERBIRD-2")
  test_config["bacnet"] = dict(ip=None, port=None, interface=None)
  test_config["nmap"] = dict(targets="127.0.0.1/32")

  # Container for storing all discovery messages
  messages = []

  with (
      mock.patch.object(
          udmi.core.UDMI, "publish_discovery", new=messages.append
      ) as published_discovery,
  ):
    mock_mqtt_client = mock.MagicMock()
    udmi_client = udmi.core.UDMI(publisher=mock_mqtt_client, topic_prefix="notneeded", config=test_config)

    udmi_client.config_handler(
        json.dumps({
            "timestamp": 123,
            "discovery": {
                "families": {
                    "bacnet": {"generation": "123"},
                    "ipv4": {"generation": "123"},
                }
            },
        })
    )

    time.sleep(15)

    for message in messages:
      print(message.to_json())
      print("----")

    # subset because passive scan will find the gateway and device itself
    assert set(
        m.families["ethmac"].addr for m in messages if "ethmac" in m.families
    ) >= set(d["ethmac"] for d in docker_config.values())

    assert set(
        m.scan_addr for m in messages if m.scan_family == "bacnet"
    ) == set(d["bacnet_id"] for d in docker_config.values())
