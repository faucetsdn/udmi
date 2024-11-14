import collections
import json
import os
import time
from unittest import mock
import pytest
import udmi.core
import udmi.schema.util
import udmi.discovery.discovery

def timestamp_now():
  return udmi.schema.util.datetime_serializer(udmi.schema.util.current_time_utc())

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
          udmi.core.UDMICore, "publish_discovery", new=messages.append
      ) as published_discovery,
  ):
    mock_mqtt_client = mock.MagicMock()
    udmi_client = udmi.core.UDMICore(publisher=mock_mqtt_client, topic_prefix="notneeded", config=test_config)
    
    # Start discovery
    udmi_client.config_handler(
        json.dumps({
            "timestamp": timestamp_now(),
            "discovery": {
                "families": {
                    "bacnet": {"generation": timestamp_now(), "scan_duration_sec": 20},
                    "ipv4": {"generation": timestamp_now(), "scan_duration_sec": 20}
                }
            },
        })
    )

    time.sleep(30)

    # check has stopped
    assert udmi_client.state.discovery.families["bacnet"].phase == udmi.discovery.discovery.states.CANCELLED
    assert udmi_client.state.discovery.families["ipv4"].phase == udmi.discovery.discovery.states.CANCELLED

    for message in messages:
      print(message.to_json())
      print("----")
    
    expected_ethmacs = set(d["ether"] for d in docker_config.values())
    seen_ethmac_toplevel = set(m.families["ether"].addr for m in messages if "ether" in m.families)

    expected_bacnet_ids = set(str(d["bacnet_id"]) for d in docker_config.values())
    seen_bacnet_ids_toplevel = set(m.scan_addr for m in messages if m.scan_family == "bacnet")

    # subset because passive scan will find the gateway and device itself
    assert seen_ethmac_toplevel == expected_ethmacs + 2
    assert expected_bacnet_ids == seen_bacnet_ids_toplevel 
