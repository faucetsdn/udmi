# Execute these serially from outside (e.g. -k <test name>)
import collections
import datetime
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
    udmi_client.discovery_manager.shutdown()
    

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
    udmi_client.discovery_manager.shutdown()


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
                    "ether": {"generation": timestamp_now(), "depth": "services", "addrs": ["192.168.12.1"]}
                }
            },
        })
    )

    for _ in range(60):
        if len(messages) >= 2:
            break
        time.sleep(1)
    
    print(len(messages))
    for message in messages:
      print(message.to_json())
      print("----")
    

    # verify that nmap discovery completed
    #1 because 0 is the publish marker
    assert len(messages) >= 2, "Discovery message not received"
    assert messages[1].refs["22"]["adjunct"]["product"] == "OpenSSH"
    assert "sort_index" not in messages[1].refs["22"]["adjunct"]
    udmi_client.discovery_manager.shutdown()



# ---------------------------------------------------------------------------
# Legacy `families` + named `scans` combined
# ---------------------------------------------------------------------------

def _wait_until(predicate, timeout, step=0.5):
  deadline = time.monotonic() + timeout
  while time.monotonic() < deadline:
    if predicate():
      return True
    time.sleep(step)
  return False


def _addrs_by_generation(messages):
  out = {}
  for m in messages:
    if m.addr is None:
      continue
    out.setdefault(udmi.schema.util.datetime_serializer(m.generation), []).append(m.addr)
  return {k: sorted(v) for k, v in out.items()}


def test_vendor_legacy_and_named_combined(caplog):
  """One config carrying both a legacy family and a named scan of the same
  family, with distinct target ranges, driven end-to-end through
  UDMICore.config_handler exactly as an MQTT config would be."""
  test_config = collections.defaultdict()
  test_config["mqtt"] = dict(device_id="THUNDERBIRD-2")
  test_config["udmi"] = {"discovery": dict(ipv4=False, vendor=True, ether=False, bacnet=False)}

  messages = []
  g1 = timestamp_now()
  g2 = udmi.schema.util.datetime_serializer(udmi.schema.util.current_time_utc() + datetime.timedelta(seconds=1))

  with (
      mock.patch.object(udmi.core.UDMICore, "publish_discovery", new=messages.append),
      caplog.at_level(logging.INFO),
  ):
    udmi_client = udmi.core.UDMICore(publisher=mock.MagicMock(), topic_prefix="notneeded", config=test_config)
    manager = udmi_client.discovery_manager
    assert manager.scans == {}, "no controllers before the first config"

    udmi_client.config_handler(json.dumps({
        "timestamp": timestamp_now(),
        "discovery": {
            "families": {
                "vendor": {"generation": g1, "addrs": ["1", "2", "3"], "scan_duration_sec": 10},
            },
            "scans": {
                "named_vendor": {
                    "family": "vendor",
                    # unknown-to-the-node key, must be tolerated
                    "mode": "active",
                    "generation": g2,
                    "addrs": ["101", "102", "103"],
                    "scan_duration_sec": 10,
                },
            },
        },
    }))

    def both_stopped():
      d = json.loads(udmi_client.state.to_json()).get("discovery", {})
      return (d.get("families", {}).get("vendor", {}).get("phase") == "stopped"
              and d.get("scans", {}).get("named_vendor", {}).get("phase") == "stopped")

    assert _wait_until(both_stopped, timeout=30)

    for message in messages:
      print(message.to_json())
      print("----")

    # 2. events partition by generation, each with its own markers / event_no sequence
    assert _addrs_by_generation(messages) == {g1: ["1", "2", "3"], g2: ["101", "102", "103"]}
    for g in (g1, g2):
      nos = [m.event_no for m in messages if udmi.schema.util.datetime_serializer(m.generation) == g]
      assert nos == [0, 1, 2, 3, -4], nos

    # 3. published state JSON
    udmi_client.execute_state_hooks()
    d = json.loads(udmi_client.state.to_json())["discovery"]
    assert d["families"]["vendor"] == {"generation": g1, "phase": "stopped", "active_count": 3}
    assert d["scans"]["named_vendor"] == {"generation": g2, "phase": "stopped", "active_count": 3}

    # 4. remove the named scan -> state cleaned, legacy untouched
    udmi_client.config_handler(json.dumps({
        "timestamp": timestamp_now(),
        "discovery": {
            "families": {
                "vendor": {"generation": g1, "addrs": ["1", "2", "3"], "scan_duration_sec": 10},
            },
        },
    }))
    d = json.loads(udmi_client.state.to_json())["discovery"]
    assert "scans" not in d
    assert d["families"]["vendor"] == {"generation": g1, "phase": "stopped", "active_count": 3}
    assert list(manager.scans) == [("families", "vendor")]

    # 5. every controller log line is attributable to exactly one scan
    controller_records = [r for r in caplog.records
                          if r.name.startswith("udmi.discovery.") and r.name != "udmi.discovery.manager"]
    assert controller_records
    unattributed = [r.getMessage() for r in controller_records
                    if not r.getMessage().startswith(("[families/vendor] ", "[scans/named_vendor] "))]
    assert unattributed == [], unattributed
    # unknown key tolerated with a warning
    assert any("[scans/named_vendor] ignoring unsupported scan field(s) ['mode']" in r.getMessage()
               for r in caplog.records)

    manager.shutdown()


def test_bacnet_named_scan_system():
  test_config = collections.defaultdict()
  test_config["mqtt"] = dict(device_id="THUNDERBIRD-2")
  test_config["bacnet"] = dict(ip=None, port=None, interface=None)
  test_config["udmi"] = {"discovery": dict(ipv4=False, vendor=False, ether=False, bacnet=True)}
  messages = []

  with mock.patch.object(udmi.core.UDMICore, "publish_discovery", new=messages.append):
    udmi_client = udmi.core.UDMICore(publisher=mock.MagicMock(), topic_prefix="notneeded", config=test_config)
    g = timestamp_now()
    udmi_client.config_handler(json.dumps({
        "timestamp": timestamp_now(),
        "discovery": {
            "scans": {
                "bacnet_enum": {"family": "bacnet", "generation": g, "scan_duration_sec": 20, "depth": "system"}
            }
        },
    }))

    time.sleep(15)

    for message in messages:
      print(message.to_json())
      print("----")

    assert set(m.addr for m in messages[1:] if m.family == "bacnet") == {"1"}
    assert len(messages[1].refs) == 0
    d = json.loads(udmi_client.state.to_json())["discovery"]
    assert "families" not in d
    assert d["scans"]["bacnet_enum"]["generation"] == g
    assert d["scans"]["bacnet_enum"]["phase"] in ("active", "stopped")
    udmi_client.discovery_manager.shutdown()


def test_bacnet_legacy_and_named_are_exclusive():
  """Legacy and named bacnet scans share one BAC0 stack and must never run
  concurrently: exactly one runs, the other is rejected with a status, and the
  rejected one recovers once the running one is removed from config."""
  test_config = collections.defaultdict()
  test_config["mqtt"] = dict(device_id="THUNDERBIRD-2")
  test_config["bacnet"] = dict(ip=None, port=None, interface=None)
  test_config["udmi"] = {"discovery": dict(ipv4=False, vendor=False, ether=False, bacnet=True)}
  messages = []

  with (
      mock.patch.object(udmi.core.UDMICore, "publish_discovery", new=messages.append),
      mock.patch.object(udmi.discovery.bacnet, "create_bacnet_stack",
                        wraps=udmi.discovery.bacnet.create_bacnet_stack) as create_stack,
  ):
    udmi_client = udmi.core.UDMICore(publisher=mock.MagicMock(), topic_prefix="notneeded", config=test_config)
    assert create_stack.call_count == 1
    manager = udmi_client.discovery_manager

    g = timestamp_now()
    udmi_client.config_handler(json.dumps({
        "timestamp": timestamp_now(),
        "discovery": {
            "families": {"bacnet": {"generation": g, "scan_duration_sec": 20, "depth": "system"}},
            "scans": {"bacnet_enum": {"family": "bacnet", "generation": g, "scan_duration_sec": 20, "depth": "system"}},
        },
    }))

    instances = list(manager.scans.values())
    assert len(instances) == 2

    assert _wait_until(lambda: all(
        i.internal_state in (udmi.discovery.discovery.states.STARTED, udmi.discovery.discovery.states.ERROR)
        for i in instances), timeout=15)
    started = [i for i in instances if i.internal_state == udmi.discovery.discovery.states.STARTED]
    rejected = [i for i in instances if i.internal_state == udmi.discovery.discovery.states.ERROR]
    assert len(started) == 1 and len(rejected) == 1, [(i.scan_id, i.internal_state) for i in instances]
    assert rejected[0].state.status.level == 500
    assert f"already running as {started[0].scan_id}" in rejected[0].state.status.message

    time.sleep(15)
    device_events = [m for m in messages if m.family == "bacnet" and m.addr]
    assert set(m.addr for m in device_events) == {"1"}
    # Only the running scan published results: exactly one event for device 1
    assert len(device_events) == 1, [(m.addr, m.generation) for m in device_events]
    # ...and the rejected scan never published a start marker (event_no 0)
    assert len([m for m in messages if m.event_no == 0]) == 1
    assert create_stack.call_count == 1

    # Remove the running scan -> the rejected one must recover on its next generation
    messages.clear()
    remaining_key = "families" if rejected[0].scan_id.startswith("families/") else "scans"
    remaining_block = (
        {"bacnet": {"generation": timestamp_now(), "scan_duration_sec": 20, "depth": "system"}}
        if remaining_key == "families"
        else {"bacnet_enum": {"family": "bacnet", "generation": timestamp_now(), "scan_duration_sec": 20, "depth": "system"}}
    )
    udmi_client.config_handler(json.dumps({
        "timestamp": timestamp_now(),
        "discovery": {remaining_key: remaining_block},
    }))
    assert list(manager.scans.values()) == [rejected[0]]
    assert _wait_until(lambda: {m.addr for m in messages if m.family == "bacnet" and m.addr} == {"1"}, timeout=30)
    assert rejected[0].state.status is None
    manager.shutdown()
