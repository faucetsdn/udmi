# pylint: disable=protected-access
"""Tests for DiscoveryManager: legacy `families` + named `scans` reconciliation."""

import datetime
import json
import logging
import threading
import time
from unittest import mock

import pytest
import udmi.discovery.discovery as discovery
import udmi.discovery.manager as manager
import udmi.discovery.numbers
import udmi.schema.state as state
import udmi.schema.util
from udmi.schema.config import DiscoveryFamily

logging.root.setLevel(logging.DEBUG)

VENDOR_ONLY = dict(enabled_families={"vendor"}, family_kwargs={"vendor": {"range": None}})


def ts(seconds_from_now=0):
  return udmi.schema.util.datetime_serializer(
      udmi.schema.util.current_time_utc() + datetime.timedelta(seconds=seconds_from_now)
  )


def make_manager(publisher=None, **kwargs):
  top = state.State()
  args = dict(VENDOR_ONLY)
  args.update(kwargs)
  m = manager.DiscoveryManager(top, publisher or mock.MagicMock(), **args)
  return top, m


def state_json(top):
  return json.loads(top.to_json())


def wait_until(predicate, timeout=15, step=0.1):
  deadline = time.monotonic() + timeout
  while time.monotonic() < deadline:
    if predicate():
      return True
    time.sleep(step)
  return False


def addrs_by_generation(publisher):
  """Returns {generation_str: sorted list of addrs} excluding markers."""
  out = {}
  for (event,), _ in publisher.call_args_list:
    if event.addr is None:
      continue
    out.setdefault(udmi.schema.util.datetime_serializer(event.generation), []).append(event.addr)
  return {k: sorted(v) for k, v in out.items()}


# ----------------------------------------------------------------- creation

def test_no_controllers_exist_before_first_config():
  top, m = make_manager(enabled_families={"vendor", "ether", "ipv4"},
                        family_kwargs={"vendor": {}, "ether": {}, "ipv4": {}})
  assert m.scans == {}
  assert "discovery" not in state_json(top)

  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery"):
    m.apply_config({"discovery": {"families": {"vendor": {"generation": ts()}}}})
  assert list(m.scans) == [("families", "vendor")]
  assert isinstance(m.scans[("families", "vendor")], udmi.discovery.numbers.NumberDiscovery)


def test_legacy_families_block_unchanged():
  top, m = make_manager()
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery") as start:
    m.apply_config({"discovery": {"families": {"vendor": {"generation": ts()}}}})
    assert wait_until(lambda: start.called)
  js = state_json(top)
  assert js["discovery"]["families"]["vendor"]["phase"] in ("pending", "active")
  assert "scans" not in js["discovery"]


def test_named_scan_creates_instance_and_state():
  top, m = make_manager()
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery") as start:
    m.apply_config({"discovery": {"scans": {"x": {"family": "vendor", "generation": ts()}}}})
    assert wait_until(lambda: start.called)
  js = state_json(top)
  assert js["discovery"]["scans"]["x"]["phase"] in ("pending", "active")
  assert "families" not in js["discovery"]
  assert m.scans[("scans", "x")].scan_id == "scans/x"


def test_legacy_family_goes_through_same_path_as_named():
  top, m = make_manager()
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "apply_config") as apply:
    g = ts()
    m.apply_config({"discovery": {
        "families": {"vendor": {"generation": g, "addrs": ["1"]}},
        "scans": {"x": {"family": "vendor", "generation": g, "addrs": ["1"]}},
    }})
  assert type(m.scans[("families", "vendor")]) is type(m.scans[("scans", "x")])
  assert m.scans[("families", "vendor")].scan_id == "families/vendor"
  assert m.scans[("scans", "x")].scan_id == "scans/x"
  configs = [c.args[0] for c in apply.call_args_list]
  assert configs == [DiscoveryFamily(generation=g, addrs=["1"])] * 2


def test_legacy_and_named_coexist():
  top, m = make_manager()
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery"):
    m.apply_config({"discovery": {
        "families": {"vendor": {"generation": ts()}},
        "scans": {"x": {"family": "vendor", "generation": ts()}},
    }})
  assert set(m.scans) == {("families", "vendor"), ("scans", "x")}


# ------------------------------------------------- real vendor scans, ranges

def test_legacy_and_named_vendor_distinct_ranges():
  publisher = mock.MagicMock()
  top, m = make_manager(publisher)
  g1, g2 = ts(), ts(1)
  m.apply_config({"discovery": {
      "families": {"vendor": {"generation": g1, "addrs": ["1", "2", "3"], "scan_duration_sec": 6}},
      "scans": {"named_vendor": {"family": "vendor", "generation": g2,
                                 "addrs": ["101", "102", "103"], "scan_duration_sec": 6}},
  }})

  def both_stopped():
    js = state_json(top).get("discovery", {})
    return (js.get("families", {}).get("vendor", {}).get("phase") == "stopped"
            and js.get("scans", {}).get("named_vendor", {}).get("phase") == "stopped")
  assert wait_until(both_stopped, timeout=20)

  assert addrs_by_generation(publisher) == {g1: ["1", "2", "3"], g2: ["101", "102", "103"]}

  # Each scan has its own marker/event_no sequence
  for g in (g1, g2):
    nos = [e.event_no for (e,), _ in publisher.call_args_list
           if udmi.schema.util.datetime_serializer(e.generation) == g]
    assert nos == [0, 1, 2, 3, -4], nos

  m.on_state_update_hook()
  js = state_json(top)["discovery"]
  assert js["families"]["vendor"]["active_count"] == 3
  assert js["scans"]["named_vendor"]["active_count"] == 3
  assert js["families"]["vendor"]["generation"] == g1
  assert js["scans"]["named_vendor"]["generation"] == g2


def test_legacy_and_named_vendor_independent_lifecycle():
  publisher = mock.MagicMock()
  top, m = make_manager(publisher)
  g1, g2 = ts(), ts(1)
  m.apply_config({"discovery": {
      "families": {"vendor": {"generation": g1, "addrs": ["1", "2", "3"], "scan_duration_sec": 5}},
      "scans": {"named_vendor": {"family": "vendor", "generation": g2,
                                 "addrs": ["101", "102", "103"], "scan_duration_sec": 5}},
  }})
  assert wait_until(lambda: len(addrs_by_generation(publisher).get(g1, [])) == 3
                    and len(addrs_by_generation(publisher).get(g2, [])) == 3)
  named = m.scans[("scans", "named_vendor")]

  # Remove the named scan, regenerate the legacy one
  g3 = ts()
  m.apply_config({"discovery": {
      "families": {"vendor": {"generation": g3, "addrs": ["1", "2", "3"], "scan_duration_sec": 5}},
  }})
  assert ("scans", "named_vendor") not in m.scans
  assert named.config is None
  assert "scans" not in state_json(top)["discovery"]
  assert wait_until(lambda: len(addrs_by_generation(publisher).get(g3, [])) == 3)
  assert addrs_by_generation(publisher) == {
      g1: ["1", "2", "3"], g2: ["101", "102", "103"], g3: ["1", "2", "3"]}


def test_vendor_addrs_override_local_range():
  publisher = mock.MagicMock()
  n = udmi.discovery.numbers.NumberDiscovery(state.Discovery(), publisher, scan_id="scans/t", range="7,8,9")
  n.config = DiscoveryFamily(generation=ts(), addrs=["1", "2"])
  n._start(); n.task_thread.join(10); n._stop()
  assert [e.addr for (e,), _ in publisher.call_args_list if e.addr] == ["1", "2"]

  publisher.reset_mock()
  n.config = DiscoveryFamily(generation=ts())
  n._start(); n.task_thread.join(10); n._stop()
  assert [e.addr for (e,), _ in publisher.call_args_list if e.addr] == ["7", "8", "9"]


# --------------------------------------------------------------- lifecycle

def test_identical_config_is_noop():
  top, m = make_manager()
  cfg = {"discovery": {
      "families": {"vendor": {"generation": ts()}},
      "scans": {"x": {"family": "vendor", "generation": ts()}},
  }}
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery") as start:
    for _ in range(5):
      m.apply_config(cfg)
      time.sleep(0.3)
    assert wait_until(lambda: start.call_count == 2)
    time.sleep(1)
    assert start.call_count == 2


def test_changing_one_scan_restarts_only_that_scan():
  top, m = make_manager()
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery") as start:
    m.apply_config({"discovery": {"scans": {
        "a": {"family": "vendor", "generation": ts()},
        "b": {"family": "vendor", "generation": ts()},
    }}})
    assert wait_until(lambda: start.call_count == 2)
    a, b = m.scans[("scans", "a")], m.scans[("scans", "b")]
    with mock.patch.object(a, "_stop", wraps=a._stop) as a_stop, \
         mock.patch.object(b, "_stop", wraps=b._stop) as b_stop:
      m.apply_config({"discovery": {"scans": {
          "a": {"family": "vendor", "generation": a.config.generation},
          "b": {"family": "vendor", "generation": ts(2)},
      }}})
      assert wait_until(lambda: start.call_count == 3)
      a_stop.assert_not_called()
      b_stop.assert_called()
  assert m.scans[("scans", "a")] is a


def test_removing_named_scan_stops_it_and_clears_state():
  top, m = make_manager()
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery"):
    m.apply_config({"discovery": {"scans": {
        "a": {"family": "vendor", "generation": ts()},
        "b": {"family": "vendor", "generation": ts()},
    }}})
    b = m.scans[("scans", "b")]
    with mock.patch.object(b, "shutdown", wraps=b.shutdown) as b_shutdown:
      m.apply_config({"discovery": {"scans": {"a": {"family": "vendor", "generation": ts()}}}})
      b_shutdown.assert_called_once()
  assert set(m.scans) == {("scans", "a")}
  assert "b" not in state_json(top)["discovery"]["scans"]
  assert "a" in state_json(top)["discovery"]["scans"]


def test_removing_legacy_family_resets_state():
  top, m = make_manager()
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery"):
    m.apply_config({"discovery": {"families": {"vendor": {"generation": ts()}}}})
    assert wait_until(lambda: "vendor" in state_json(top).get("discovery", {}).get("families", {}))
    m.apply_config({"discovery": {"families": {}}})
  assert m.scans == {}
  # Entry is nulled (purged on serialisation), exactly as before this change
  assert state_json(top).get("discovery", {}).get("families", {}) == {}
  assert top.discovery.families["vendor"].phase is None


def test_shutdown_stops_all():
  top, m = make_manager()
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery"):
    m.apply_config({"discovery": {
        "families": {"vendor": {"generation": ts()}},
        "scans": {"x": {"family": "vendor", "generation": ts()}},
    }})
    instances = list(m.scans.values())
    m.shutdown()
  assert m.scans == {}
  assert all(i.config is None for i in instances)
  assert all(not (i.scheduler_thread and i.scheduler_thread.is_alive()) for i in instances)


def test_immediate_reconfigure_does_not_deadlock():
  """Regression: apply_config() joins the scheduler thread while holding the
  controller mutex; the scheduler must never block unboundedly on that mutex.
  Stress the race by reconfiguring immediately after scheduling a scan that is
  due to start right now."""
  top, m = make_manager()
  done = threading.Event()

  def storm():
    with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery"):
      for i in range(200):
        m.apply_config({"discovery": {"scans": {"x": {"family": "vendor", "generation": ts(-1 - (i % 3))}}}})
      m.shutdown()
    done.set()

  t = threading.Thread(target=storm, daemon=True)
  t.start()
  assert done.wait(60), "apply_config/shutdown deadlocked"
  assert m.scans == {}


def test_config_storm_is_consistent():
  top, m = make_manager()
  baseline = threading.active_count()
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery"):
    for i in range(50):
      name = "a" if i % 2 else "b"
      m.apply_config({"discovery": {"scans": {name: {"family": "vendor", "generation": ts(5)}}}})
    assert set(m.scans) == {("scans", "a")}
    assert wait_until(lambda: set(state_json(top).get("discovery", {}).get("scans", {})) == {"a"})
    m.shutdown()
  assert wait_until(lambda: threading.active_count() <= baseline)


# -------------------------------------------------------------- validation

def test_unknown_fields_are_ignored_with_warning(caplog):
  top, m = make_manager()
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery") as start, \
       caplog.at_level(logging.WARNING):
    m.apply_config({"discovery": {"scans": {"nightly_passive_sweep": {
        "family": "vendor", "generation": ts(), "mode": "passive",
        "passive_sec": 300, "networks": ["192.168.1.0/24"]}}}})
    assert wait_until(lambda: start.called)
  assert m.scans[("scans", "nightly_passive_sweep")].config == DiscoveryFamily(generation=mock.ANY)
  warnings = [r.getMessage() for r in caplog.records if r.levelno == logging.WARNING]
  assert any("[scans/nightly_passive_sweep] ignoring unsupported scan field(s) ['mode', 'networks', 'passive_sec']" in w
             for w in warnings), warnings


@pytest.mark.parametrize("block, expected", [
    ({"generation": "2026-01-01T00:00:00Z"}, "family is required"),
    ({"family": "zigbee", "generation": "2026-01-01T00:00:00Z"}, "family 'zigbee' is not enabled"),
    ({"family": "vendor"}, "generation is required"),
    ({}, "non-empty object"),
])
def test_invalid_named_scan_is_error_status(block, expected):
  top, m = make_manager()
  m.apply_config({"discovery": {"scans": {"x": block}}})
  assert m.scans == {}
  entry = state_json(top)["discovery"]["scans"]["x"]
  assert entry["phase"] == "stopped"
  assert entry["status"]["level"] == 500
  assert expected in entry["status"]["message"]


def test_invalid_scan_name_is_error_status():
  top, m = make_manager()
  m.apply_config({"discovery": {"scans": {"Bad-Name": {"family": "vendor", "generation": ts()}}}})
  assert m.scans == {}
  assert "invalid scan name" in state_json(top)["discovery"]["scans"]["Bad-Name"]["status"]["message"]


def test_unsupported_family_does_not_affect_siblings():
  top, m = make_manager()
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery") as start:
    m.apply_config({"discovery": {"scans": {
        "good": {"family": "vendor", "generation": ts()},
        "bad": {"family": "zigbee", "generation": ts()},
    }}})
    assert wait_until(lambda: start.called)
  assert set(m.scans) == {("scans", "good")}
  js = state_json(top)["discovery"]["scans"]
  assert js["bad"]["status"]["level"] == 500
  assert "status" not in js["good"]


def test_family_not_enabled_is_rejected():
  top, m = make_manager()
  m.apply_config({"discovery": {"families": {"ether": {"generation": ts()}}}})
  assert m.scans == {}
  assert "not enabled" in state_json(top)["discovery"]["families"]["ether"]["status"]["message"]


def test_error_scan_replaced_by_valid_config_recovers():
  top, m = make_manager()
  m.apply_config({"discovery": {"scans": {"x": {"generation": ts()}}}})
  assert state_json(top)["discovery"]["scans"]["x"]["status"]["level"] == 500
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery") as start:
    m.apply_config({"discovery": {"scans": {"x": {"family": "vendor", "generation": ts()}}}})
    assert wait_until(lambda: start.called)
  assert "status" not in state_json(top)["discovery"]["scans"]["x"]


def test_unknown_enabled_family_fails_fast():
  with pytest.raises(ValueError, match="zigbee"):
    manager.DiscoveryManager(state.State(), mock.MagicMock(),
                             enabled_families={"zigbee"}, family_kwargs={})


# ----------------------------------------------------------------- logging

def controller_records(caplog):
  return [r for r in caplog.records if r.name.startswith("udmi.discovery.") and r.name != "udmi.discovery.manager"]


def test_idle_controllers_do_not_log_on_config(caplog):
  top, m = make_manager()
  with caplog.at_level(logging.DEBUG):
    cfg = {"discovery": {"families": {"vendor": {}}}}
    m.apply_config(cfg)
    caplog.clear()
    for _ in range(5):
      m.apply_config(cfg)
  assert controller_records(caplog) == []
  assert not any("Stopping discovery" in r.getMessage() or "stopping discovery" in r.getMessage()
                 for r in caplog.records)


def test_log_lines_carry_scan_id(caplog):
  top, m = make_manager()
  with caplog.at_level(logging.DEBUG), \
       mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery") as start:
    m.apply_config({"discovery": {"scans": {
        "a": {"family": "vendor", "generation": ts()},
        "b": {"family": "vendor", "generation": ts()},
    }}})
    assert wait_until(lambda: start.call_count == 2)
  records = controller_records(caplog)
  assert records
  assert all(r.getMessage().startswith(("[scans/a] ", "[scans/b] ")) for r in records), \
      [r.getMessage() for r in records if not r.getMessage().startswith("[scans/")]


def test_stop_logs_only_when_running(caplog):
  publisher = mock.MagicMock()
  top, m = make_manager(publisher)
  with caplog.at_level(logging.INFO):
    m.apply_config({"discovery": {"scans": {
        "running": {"family": "vendor", "generation": ts(), "addrs": ["1", "2", "3", "4", "5", "6", "7", "8"]},
        "idle": {"family": "vendor", "generation": ts(3600)},
    }}})
    assert wait_until(lambda: m.scans[("scans", "running")].internal_state == discovery.states.STARTED)
    m.apply_config({"discovery": {}})
  stopping = [r.getMessage() for r in caplog.records if "stopping discovery" in r.getMessage()]
  stopped = [r.getMessage() for r in caplog.records if "] stopped (was" in r.getMessage()]
  assert stopping == ["[scans/running] stopping discovery (was started)"]
  assert len(stopped) == 1 and stopped[0].startswith("[scans/running] stopped (was started) in ")


# --------------------------------------------------- regression / review fixes

def test_changing_named_scan_family_replaces_controller():
  top, m = make_manager(
      enabled_families={"vendor", "ether"},
      family_kwargs={"vendor": {"range": None}, "ether": {}},
  )
  with mock.patch.object(udmi.discovery.numbers.NumberDiscovery, "start_discovery"), \
       mock.patch.object(udmi.discovery.ether.EtherDiscovery, "start_discovery"):
    g = ts()
    m.apply_config({"discovery": {"scans": {"x": {"family": "vendor", "generation": g}}}})
    first = m.scans[("scans", "x")]
    assert isinstance(first, udmi.discovery.numbers.NumberDiscovery)

    m.apply_config({"discovery": {"scans": {"x": {
        "family": "ether", "generation": g, "depth": "ping", "addrs": ["127.0.0.1"]
    }}}})
    second = m.scans[("scans", "x")]
    assert isinstance(second, udmi.discovery.ether.EtherDiscovery)
    assert first.config is None


def test_error_legacy_family_cleared_by_empty_block():
  top, m = make_manager()
  m.apply_config({"discovery": {"families": {"vendor": {"depth": "ping"}}}})
  assert state_json(top)["discovery"]["families"]["vendor"]["status"]["level"] == 500

  m.apply_config({"discovery": {"families": {"vendor": {}}}})
  assert state_json(top).get("discovery", {}).get("families", {}) == {}


@pytest.mark.parametrize("block, expected", [
    ({"family": "vendor", "generation": "not-a-timestamp"}, "invalid generation"),
    ({"family": "vendor", "generation": 123}, "generation is required and must be a string"),
    ({"family": "vendor", "generation": "2026-01-01T00:00:00Z", "scan_interval_sec": -1}, "scan_interval_sec must be a non-negative integer"),
    ({"family": "vendor", "generation": "2026-01-01T00:00:00Z", "scan_duration_sec": -5}, "scan_duration_sec must be a non-negative integer"),
    ({"family": "vendor", "generation": "2026-01-01T00:00:00Z", "scan_interval_sec": True}, "scan_interval_sec must be a non-negative integer"),
    ({"family": "vendor", "generation": "2026-01-01T00:00:00Z", "scan_interval_sec": 10, "scan_duration_sec": 20}, "cannot exceed scan_interval_sec"),
    ({"family": "vendor", "generation": "2026-01-01T00:00:00Z", "addrs": "10.0.0.1"}, "addrs must be a list of strings"),
    ({"family": "vendor", "generation": "2026-01-01T00:00:00Z", "addrs": [123]}, "addrs must be a list of strings"),
])
def test_invalid_config_fields_rejected(block, expected):
  top, m = make_manager()
  m.apply_config({"discovery": {"scans": {"x": block}}})
  assert m.scans == {}
  entry = state_json(top)["discovery"]["scans"]["x"]
  assert entry["phase"] == "stopped"
  assert entry["status"]["level"] == 500
  assert expected in entry["status"]["message"]


def test_bacnet_exception_in_consumer_sets_error_state():
  publisher = mock.MagicMock()
  fake_stack = mock.MagicMock()
  b = udmi.discovery.bacnet.GlobalBacnetDiscovery(
      state.Discovery(), publisher, scan_id="scans/b", bacnet=fake_stack
  )
  b.config = DiscoveryFamily(generation=ts())
  b.generation = udmi.schema.util.current_time_utc()
  b.count_events = 0
  b.cancelled = False

  def boom():
    raise RuntimeError("bacnet boom")

  b.devices_consumer(boom)
  assert b.internal_state == discovery.states.ERROR
  assert b.state.phase == state.Phase.stopped
  assert b.state.status.level == 500
  assert "bacnet boom" in b.state.status.message
  assert publisher.call_count == 0


def test_passive_scan_emits_end_marker_and_datetime_generation():
  publisher = mock.MagicMock()
  p = udmi.discovery.passive.PassiveNetworkDiscovery(
      state.Discovery(), publisher, scan_id="scans/passive"
  )
  p.publish_interval = 0.05
  gen_dt = udmi.schema.util.current_time_utc()
  p.config = DiscoveryFamily(generation=udmi.schema.util.datetime_serializer(gen_dt))
  p.generation = gen_dt
  p.count_events = 0
  p.device_records.add(udmi.discovery.passive.PassiveScanRecord(addr="10.0.0.5", mac="aa:bb:cc:dd:ee:ff"))
  p.cancel_threads.set()

  p.discovery_service()
  events = [c.args[0] for c in publisher.call_args_list]
  assert len(events) == 2
  assert events[0].addr == "10.0.0.5"
  assert events[0].event_no == 1
  assert events[0].generation == gen_dt
  assert events[1].addr is None
  assert events[1].event_no == -2
  assert events[1].generation == gen_dt
  assert p.state.phase == state.Phase.stopped


def test_state_published_periodically_and_on_change():
  import udmi.core

  publisher = mock.MagicMock()
  cfg = {
      "udmi": {
          "discovery": {"bacnet": False, "ipv4": False, "ether": False, "vendor": True},
          "state_interval_sec": 0.25,
      }
  }
  with mock.patch.object(udmi.core.UDMICore, "_STATE_MONITOR_INTERVAL", 0.05):
    core = udmi.core.UDMICore(publisher=publisher, topic_prefix="/devices/DEV-1", config=cfg)
    assert core.state_interval_sec == 0.25
    # Periodic publish happens multiple times even without any state change
    assert wait_until(lambda: publisher.publish_message.call_count >= 3, timeout=3, step=0.05)
    count_before_change = publisher.publish_message.call_count
    # State change triggers an immediate publish before the next 0.25s interval
    core.state.system.last_config = "2026-01-01T00:00:00Z"
    assert wait_until(
        lambda: publisher.publish_message.call_count > count_before_change,
        timeout=0.2,
        step=0.02,
    )
    core.discovery_manager.shutdown()


@pytest.mark.parametrize("bad_interval", [0, -10, False, "600"])
def test_invalid_state_interval_sec_rejected(bad_interval):
  import udmi.core

  with pytest.raises(ValueError, match="state_interval_sec"):
    udmi.core.UDMICore(
        publisher=mock.MagicMock(),
        topic_prefix="/devices/DEV-1",
        config={"udmi": {"state_interval_sec": bad_interval}},
    )



