# Execute these serially from outside (e.g. -k <test name>)
import collections
import json
import os
import time
from unittest import mock
import pytest
import udmi.core
import udmi.schema.util
import udmi.discovery.discovery
import os
import logging

# This test runs inside a discoverynode container as a command
assert "I_AM_INTEGRATION_TEST" in os.environ

def timestamp_now():
  return udmi.schema.util.datetime_serializer(udmi.schema.util.current_time_utc())

# Running two bacnet tests in the same session is currently broken
# Need to disconnect the first bacnet device
def test_bacnet_system():
  
  # This is the "config.json" which is passed to `main.py` typically
  test_config = collections.defaultdict()
  test_config["mqtt"] = dict(device_id="THUNDERBIRD-2")
  test_config["mqtt"] = dict(device_id="THUNDERBIRD-2")
  test_config["bacnet"] = dict(ip=None, port=None, interface=None)
  test_config["udmi"] = {"discovery": dict(ipv4=False,vendor=False,ether=False,bacnet=True)}
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
                    "bacnet": {"generation": timestamp_now(), "scan_duration_sec": 20, "depth":"system"}
                }
            },
        })
    )
    
    time.sleep(15)

    for message in messages:
      print(message.to_json())
      print("----")
   
    expected_bacnet_ids = set(["1"])
    seen_bacnet_ids_toplevel = set(m.addr for m in messages[1:] if m.family == "bacnet")
    
    assert expected_bacnet_ids == seen_bacnet_ids_toplevel 
    assert len(messages[1].refs) == 0
    # no points
    

def test_bacnet_refs():
  
  # This is the "config.json" which is passed to `main.py` typically
  test_config = collections.defaultdict()
  test_config["mqtt"] = dict(device_id="THUNDERBIRD-2")
  test_config["mqtt"] = dict(device_id="THUNDERBIRD-2")
  test_config["bacnet"] = dict(ip=None, port=None, interface=None)
  test_config["udmi"] = {"discovery": dict(ipv4=False,vendor=False,ether=False,bacnet=True)}
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
                    "bacnet": {"generation": timestamp_now(), "scan_duration_sec": 20, "depth": "refs"}
                }
            },
        })
    )

    time.sleep(15)

    for message in messages:
      print(message.to_json())
      print("----")
   
    expected_bacnet_ids = set(["1"])
    seen_bacnet_ids_toplevel = set(m.addr for m in messages[1:] if m.family == "bacnet")

    assert expected_bacnet_ids == seen_bacnet_ids_toplevel 
    assert len(messages[1].refs) > 0


def test_nmap():
  
  # This is the "config.json" which is passed to `main.py` typically
  test_config = collections.defaultdict()
  test_config["mqtt"] = dict(device_id="THUNDERBIRD-2")
  test_config["mqtt"] = dict(device_id="THUNDERBIRD-2")
  test_config["bacnet"] = dict(ip=None, port=None, interface=None)
  test_config["udmi"] = {"discovery": dict(ipv4=False,vendor=False,ether=True,bacnet=False)}
  test_config["nmap"] = dict(targets=["192.168.12.1"])

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
                    "ether": {"generation": timestamp_now(), "depth": "ports", "addrs": ["192.168.12.1/24"]}
                }
            },
        })
    )

    time.sleep(30)
    
    print(len(messages))
    for message in messages:
      print(message.to_json())
      print("----")
    

    # verify that nmap discovery completed
    #1 because 0 is the publish marker
    assert messages[1].refs["1256"]["adjunct"]["product"] == "Postfix smtpd"

