# pylint: disable=protected-access
"""Tests for DiscoveryController.exclusive (family-scoped Semaphore(1))."""

import datetime
import logging
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


class ExclusiveNumberDiscovery(udmi.discovery.numbers.NumberDiscovery):
  """vendor scan with the same exclusivity contract as bacnet."""
  exclusive = True


class ExclusiveOtherFamily(udmi.discovery.numbers.NumberDiscovery):
  family = "othervendor"
  exclusive = True


def ts(seconds_from_now=0):
  return udmi.schema.util.datetime_serializer(
      udmi.schema.util.current_time_utc() + datetime.timedelta(seconds=seconds_from_now)
  )


def make(cls=ExclusiveNumberDiscovery, scan_id="scans/a", addrs=("1", "2", "3", "4", "5", "6", "7", "8")):
  c = cls(state.Discovery(), mock.MagicMock(), scan_id=scan_id)
  c.config = DiscoveryFamily(generation=ts(), addrs=list(addrs))
  return c


def wait_until(predicate, timeout=15, step=0.05):
  deadline = time.monotonic() + timeout
  while time.monotonic() < deadline:
    if predicate():
      return True
    time.sleep(step)
  return False


def test_second_exclusive_start_is_rejected():
  a, b = make(scan_id="scans/a"), make(scan_id="scans/b")
  a._start()
  assert a.internal_state == discovery.states.STARTED
  with mock.patch.object(b, "start_discovery", wraps=b.start_discovery) as b_start:
    b._start()
    b_start.assert_not_called()
  assert b.internal_state == discovery.states.ERROR
  assert b.state.phase == state.Phase.stopped
  assert b.state.status.level == 500
  assert "vendor scan already running as scans/a" in b.state.status.message
  # A unaffected
  assert a.internal_state == discovery.states.STARTED
  assert a.state.status is None
  a._stop()


def test_permit_released_on_natural_finish():
  a, b = make(scan_id="scans/a", addrs=("1",)), make(scan_id="scans/b", addrs=("9",))
  a._start()
  assert wait_until(lambda: a.internal_state == discovery.states.FINISHED)
  b._start()
  assert b.internal_state == discovery.states.STARTED
  b._stop()


def test_permit_released_on_stop():
  a, b = make(scan_id="scans/a"), make(scan_id="scans/b")
  a._start()
  a._stop()
  assert a.internal_state == discovery.states.CANCELLED
  b._start()
  assert b.internal_state == discovery.states.STARTED
  b._stop()


def test_permit_released_when_start_discovery_raises():
  a, b = make(scan_id="scans/a"), make(scan_id="scans/b")
  with mock.patch.object(a, "start_discovery", side_effect=RuntimeError("boom")):
    a._start()
  assert a.internal_state == discovery.states.ERROR
  assert not a._holds_exclusive
  b._start()
  assert b.internal_state == discovery.states.STARTED
  b._stop()


def test_permit_released_on_manager_removal():
  top = state.State()
  with mock.patch.dict(manager.FAMILY_CONTROLLERS, {"vendor": ExclusiveNumberDiscovery}):
    m = manager.DiscoveryManager(top, mock.MagicMock(), enabled_families={"vendor"}, family_kwargs={})
    m.apply_config({"discovery": {"scans": {"a": {"family": "vendor", "generation": ts(), "addrs": ["1", "2", "3", "4", "5", "6", "7", "8"]}}}})
    a = m.scans[("scans", "a")]
    assert wait_until(lambda: a.internal_state == discovery.states.STARTED)
    assert discovery.DiscoveryController._exclusive_owner["vendor"] == "scans/a"
    m.apply_config({"discovery": {"scans": {"b": {"family": "vendor", "generation": ts(), "addrs": ["9"]}}}})
    b = m.scans[("scans", "b")]
    assert wait_until(lambda: b.internal_state in (discovery.states.STARTED, discovery.states.FINISHED))
    assert b.state.status is None
    m.shutdown()


def test_non_exclusive_family_is_unaffected():
  a = make(udmi.discovery.numbers.NumberDiscovery, scan_id="scans/a")
  b = make(udmi.discovery.numbers.NumberDiscovery, scan_id="scans/b")
  a._start(); b._start()
  assert a.internal_state == b.internal_state == discovery.states.STARTED
  a._stop(); b._stop()


def test_legacy_and_named_exclusive_contend():
  top = state.State()
  g = ts()
  with mock.patch.dict(manager.FAMILY_CONTROLLERS, {"vendor": ExclusiveNumberDiscovery}):
    m = manager.DiscoveryManager(top, mock.MagicMock(), enabled_families={"vendor"}, family_kwargs={})
    with mock.patch.object(ExclusiveNumberDiscovery, "start_discovery", autospec=True) as start:
      m.apply_config({"discovery": {
          "families": {"vendor": {"generation": g}},
          "scans": {"x": {"family": "vendor", "generation": g}},
      }})
      instances = list(m.scans.values())
      assert wait_until(lambda: all(i.internal_state in (discovery.states.STARTED, discovery.states.ERROR) for i in instances))
      assert start.call_count == 1
    started = [i for i in instances if i.internal_state == discovery.states.STARTED]
    rejected = [i for i in instances if i.internal_state == discovery.states.ERROR]
    assert len(started) == 1 and len(rejected) == 1
    assert f"already running as {started[0].scan_id}" in rejected[0].state.status.message
    m.shutdown()  # must not deadlock
    assert m.scans == {}


def test_periodic_scan_retries_next_interval():
  # A runs for ~3s; B is periodic every 2s with a 1s duration.
  # B's first generation collides with A and is skipped; a later one succeeds.
  a = make(scan_id="scans/a", addrs=tuple(str(i) for i in range(1, 4)))
  b = ExclusiveNumberDiscovery(state.Discovery(), mock.MagicMock(), scan_id="scans/b")
  a._start()
  seen = {}
  b_cfg = DiscoveryFamily(generation=ts(), scan_interval_sec=2, scan_duration_sec=1, addrs=["9"])
  b.apply_config(b_cfg)

  def observe():
    if b.state.generation is not None:
      gen = udmi.schema.util.datetime_serializer(b.state.generation)
      if b.internal_state == discovery.states.ERROR:
        seen[gen] = "rejected"
      elif b.internal_state in (discovery.states.STARTED, discovery.states.FINISHED) and seen.get(gen) != "rejected":
        seen[gen] = "ran"
    return "rejected" in seen.values() and "ran" in seen.values()

  assert wait_until(observe, timeout=20), seen
  b.shutdown()
  a._stop()


def test_exclusive_registry_is_per_family():
  a = make(scan_id="scans/a")
  o = make(ExclusiveOtherFamily, scan_id="scans/o")
  a._start()
  o._start()
  assert a.internal_state == o.internal_state == discovery.states.STARTED
  a._stop(); o._stop()
