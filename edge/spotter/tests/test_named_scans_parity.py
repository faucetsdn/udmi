#!/usr/bin/env python3
"""Unit tests for Spotter Named Scans (#1498) and Discovery Node (#1449) parity."""

from datetime import datetime, timedelta, timezone
import os
import signal
import subprocess
import threading
import time
import unittest
from unittest.mock import MagicMock, patch

from edge.spotter.src.manager.discovery import SpotterDiscoveryManager
from edge.spotter.src.providers.ether import EtherFamilyProvider
from udmi.schema import (
    Config,
    DiscoveryConfig,
    DiscoveryEvents,
    ScanDiscoveryConfig,
    State,
    TraceDiscoveryConfig,
)
from udmi.schema.common import Depth
from udmi.schema.state_discovery_family import Phase as DiscoveryPhase


class _BlockingProvider:
  """Fake FamilyProvider that blocks on an Event so concurrency/exclusivity can be tested."""

  def __init__(
      self,
      family: str,
      started_event: threading.Event,
      release_event: threading.Event,
  ) -> None:
    self.family = family
    self.started_event = started_event
    self.release_event = release_event
    self.stopped = False
    self.run_count = 0

  def start_scan(self, config, publish_func):
    self.run_count += 1
    self.started_event.set()
    self.release_event.wait(timeout=5.0)
    publish_func(
        "dev-1",
        DiscoveryEvents(
            generation=getattr(config, "generation", None),
            family=self.family,
            addr="10.0.0.1",
            event_no=1,
        ),
    )

  def stop_scan(self):
    self.stopped = True
    self.release_event.set()


class TestNamedScansAndExclusivity(unittest.TestCase):
  """Tests for config.discovery.scans.<name>, state.discovery.scans, and exclusivity."""

  def setUp(self):
    self.mock_device = MagicMock()
    self.mock_device.get_manager.return_value = None
    self.mock_dispatcher = MagicMock()
    self.published_events = []

    def _capture_publish(channel, msg, qos=1):
      del qos
      self.published_events.append((channel, msg))

    self.mock_dispatcher.publish_event.side_effect = _capture_publish

  def _make_manager(self, provider_factories=None, discovery_mode="full"):
    mgr = SpotterDiscoveryManager(
        discovery_mode=discovery_mode,
        provider_factories=provider_factories,
    )
    mgr._device = self.mock_device
    mgr._dispatcher = self.mock_dispatcher
    return mgr

  def test_named_scans_reconcile_and_validate_family_and_name(self):
    """Validates named scans create state.discovery.scans entries and reject bad names/missing family."""
    mgr = self._make_manager(
        provider_factories={"ether": MagicMock, "bacnet": MagicMock}
    )
    future_gen = (
        datetime.now(timezone.utc) + timedelta(minutes=5)
    ).strftime("%Y-%m-%dT%H:%M:%SZ")

    cfg = Config(
        discovery=DiscoveryConfig(
            scans={
                "ether_ping": ScanDiscoveryConfig(
                    family="ether", generation=future_gen, depth=Depth.entries
                ),
                "missing_fam": ScanDiscoveryConfig(
                    generation=future_gen, depth=Depth.entries
                ),
                "Bad-Name": ScanDiscoveryConfig(
                    family="ether", generation=future_gen, depth=Depth.entries
                ),
            }
        )
    )
    mgr.handle_config(cfg)

    state = State()
    mgr.update_state(state)
    self.assertIsNotNone(state.discovery)
    self.assertIsNotNone(state.discovery.scans)
    self.assertEqual(
        state.discovery.scans["ether_ping"].phase, DiscoveryPhase.pending
    )
    self.assertEqual(
        state.discovery.scans["missing_fam"].phase, DiscoveryPhase.stopped
    )
    self.assertEqual(state.discovery.scans["missing_fam"].status.level, 500)
    self.assertEqual(
        state.discovery.scans["Bad-Name"].phase, DiscoveryPhase.stopped
    )
    self.assertEqual(state.discovery.scans["Bad-Name"].status.level, 500)

    # Removing a scan from config removes it from state.discovery.scans
    cfg2 = Config(
        discovery=DiscoveryConfig(
            scans={
                "ether_ping": ScanDiscoveryConfig(
                    family="ether", generation=future_gen, depth=Depth.entries
                ),
            }
        )
    )
    mgr.handle_config(cfg2)
    self.assertEqual(set(mgr._discovery_state.scans.keys()), {"ether_ping"})

  def test_concurrent_named_ether_scans_tag_events_and_isolate_providers(self):
    """Two concurrent named ether scans get isolated provider instances and tag events.scan."""
    created_providers = []
    started_a = threading.Event()
    started_b = threading.Event()
    release_all = threading.Event()

    def _ether_factory():
      idx = len(created_providers)
      started = started_a if idx == 0 else started_b
      prov = _BlockingProvider("ether", started, release_all)
      created_providers.append(prov)
      return prov

    mgr = self._make_manager(provider_factories={"ether": _ether_factory})
    now_gen = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    cfg = Config(
        discovery=DiscoveryConfig(
            scans={
                "quick_ping": ScanDiscoveryConfig(
                    family="ether", generation=now_gen, depth=Depth.entries
                ),
                "deep_scan": ScanDiscoveryConfig(
                    family="ether", generation=now_gen, depth=Depth.system
                ),
            }
        )
    )
    mgr.handle_config(cfg)
    mgr._check_scheduled_scans()

    self.assertTrue(started_a.wait(timeout=2.0))
    self.assertTrue(started_b.wait(timeout=2.0))
    # Provider factory created 2 distinct instances (one per ScanKey)
    self.assertEqual(len(created_providers), 2)
    self.assertIsNot(created_providers[0], created_providers[1])

    release_all.set()
    deadline = time.monotonic() + 3.0
    while time.monotonic() < deadline:
      if (
          mgr._discovery_state.scans["quick_ping"].phase
          == DiscoveryPhase.stopped
          and mgr._discovery_state.scans["deep_scan"].phase
          == DiscoveryPhase.stopped
      ):
        break
      time.sleep(0.05)

    self.assertEqual(
        mgr._discovery_state.scans["quick_ping"].phase, DiscoveryPhase.stopped
    )
    self.assertEqual(
        mgr._discovery_state.scans["deep_scan"].phase, DiscoveryPhase.stopped
    )
    self.assertEqual(mgr._discovery_state.scans["quick_ping"].active_count, 1)
    self.assertEqual(mgr._discovery_state.scans["deep_scan"].active_count, 1)

    scan_tags = {
        msg.scan for ch, msg in self.published_events if ch == "events/discovery"
    }
    self.assertEqual(scan_tags, {"quick_ping", "deep_scan"})

  def test_bacnet_exclusivity_lock_skips_second_scan_with_500(self):
    """Second concurrent bacnet scan is skipped with status 500 while first holds the lock."""
    started_first = threading.Event()
    release_first = threading.Event()
    providers = []

    def _bacnet_factory():
      p = _BlockingProvider("bacnet", started_first, release_first)
      providers.append(p)
      return p

    mgr = self._make_manager(provider_factories={"bacnet": _bacnet_factory})
    now_gen = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    cfg_a = ScanDiscoveryConfig(
        family="bacnet", generation=now_gen, depth=Depth.entries
    )
    cfg_b = ScanDiscoveryConfig(
        family="bacnet", generation=now_gen, depth=Depth.system
    )
    mgr.handle_config(
        Config(discovery=DiscoveryConfig(scans={"scan_a": cfg_a, "scan_b": cfg_b}))
    )

    # Start scan_a in background thread
    t_a = threading.Thread(
        target=mgr._run_scan_key,
        args=(("scans", "scan_a"), "bacnet", _bacnet_factory(), cfg_a),
    )
    t_a.start()
    self.assertTrue(started_first.wait(timeout=2.0))

    # Attempt scan_b while scan_a holds the bacnet exclusivity lock -> must skip with 500
    prov_b = _bacnet_factory()
    mgr._run_scan_key(("scans", "scan_b"), "bacnet", prov_b, cfg_b)
    self.assertEqual(prov_b.run_count, 0)
    state_b = mgr._discovery_state.scans["scan_b"]
    self.assertEqual(state_b.phase, DiscoveryPhase.stopped)
    self.assertEqual(state_b.status.level, 500)
    self.assertIn("busy", state_b.status.message.lower())

    # Release scan_a and verify scan_b can run once lock is freed
    release_first.set()
    t_a.join(timeout=2.0)
    self.assertEqual(
        mgr._discovery_state.scans["scan_a"].phase, DiscoveryPhase.stopped
    )
    self.assertIsNone(mgr._discovery_state.scans["scan_a"].status)

    prov_b2 = _BlockingProvider("bacnet", threading.Event(), threading.Event())
    prov_b2.release_event.set()
    mgr._run_scan_key(("scans", "scan_b"), "bacnet", prov_b2, cfg_b)
    self.assertEqual(prov_b2.run_count, 1)
    self.assertIsNone(mgr._discovery_state.scans["scan_b"].status)

  @patch("edge.spotter.src.manager.discovery.check_raw_socket_permission")
  @patch("edge.spotter.src.manager.discovery.run_bacnet_trace_whois_rounds")
  @patch("edge.spotter.src.manager.discovery.capture_packets")
  def test_named_trace_scan_tags_upload_discovery_scan(
      self, mock_capture, mock_whois, mock_perm
  ):
    """Named trace scan tags DiscoveryUpload.scan and writes state.discovery.scans.<name>."""
    del mock_whois, mock_perm
    mock_capture.return_value = [b"PCAP_BYTES"]
    mgr = self._make_manager(discovery_mode="capture_only")
    now_gen = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    trace_cfg = ScanDiscoveryConfig(
        family="bacnet",
        generation=now_gen,
        depth=Depth.trace,
        scan_duration_sec=5,
        trace=TraceDiscoveryConfig(interface="eth0", max_bytes=1024),
    )
    mgr.handle_config(
        Config(discovery=DiscoveryConfig(scans={"bacnet_pcap": trace_cfg}))
    )
    mgr._run_scan_key(("scans", "bacnet_pcap"), "bacnet", None, trace_cfg)

    uploads = [
        msg for ch, msg in self.published_events if ch == "upload/discovery"
    ]
    self.assertEqual(len(uploads), 3)
    for msg in uploads:
      self.assertEqual(msg.scan, "bacnet_pcap")
      self.assertEqual(msg.family, "bacnet")
    self.assertEqual(
        mgr._discovery_state.scans["bacnet_pcap"].phase, DiscoveryPhase.stopped
    )

  @patch("subprocess.Popen")
  def test_nmap_uses_tempfile_stats_os_and_killpg(self, mock_popen):
    """Verifies nmap parity: -oX <tempfile>, --stats-every 60s, -O, start_new_session, killpg."""
    real_exists = os.path.exists
    captured_cmd = []
    captured_kwargs = {}

    sample_xml = """<?xml version="1.0"?>
<nmaprun>
  <host>
    <status state="up"/>
    <address addr="10.0.0.42" addrtype="ipv4"/>
    <address addr="AA:BB:CC:DD:EE:FF" addrtype="mac" vendor="Acme"/>
    <os><osmatch name="Linux 5.15" accuracy="98"/></os>
  </host>
</nmaprun>
"""

    def fake_popen(cmd, **kwargs):
      captured_cmd.extend(cmd)
      captured_kwargs.update(kwargs)
      xml_idx = cmd.index("-oX")
      xml_path = cmd[xml_idx + 1]
      with open(xml_path, "w", encoding="utf-8") as f:
        f.write(sample_xml)
      proc = MagicMock()
      proc.__enter__.return_value = proc
      proc.__exit__.return_value = False
      proc.returncode = 0
      proc.pid = 99999
      proc.communicate.return_value = ("Stats: 0:01:00 elapsed\n", "")
      return proc

    mock_popen.side_effect = fake_popen
    published = []
    provider = EtherFamilyProvider(subnet_filter="10.0.0.0/24")
    with patch(
        "os.path.exists",
        side_effect=lambda p: True if p == "/usr/bin/nmap" else real_exists(p),
    ):
      provider._run_nmap_scan(
          ["10.0.0.42"],
          "system",
          "2026-11-26T00:00:00Z",
          lambda addr, ev: published.append((addr, ev)),
      )

    self.assertIn("-O", captured_cmd)
    self.assertIn("--stats-every", captured_cmd)
    self.assertIn("60s", captured_cmd)
    xml_idx = captured_cmd.index("-oX")
    self.assertNotEqual(captured_cmd[xml_idx + 1], "-")
    # Temp XML file must be cleaned up after run
    self.assertFalse(real_exists(captured_cmd[xml_idx + 1]))
    self.assertTrue(captured_kwargs.get("start_new_session"))
    self.assertEqual(len(published), 1)
    self.assertEqual(published[0][1].system.ancillary["os"], "Linux 5.15")

    # Verify killpg is called when cancelling an active nmap scan
    from edge.spotter.src.providers.ether import _signal_process_group

    fake_proc = MagicMock()
    fake_proc.pid = 12345
    with patch("os.getpgid", return_value=12345) as mock_pgid, patch(
        "os.killpg"
    ) as mock_killpg:
      _signal_process_group(fake_proc, signal.SIGTERM)
      mock_pgid.assert_called_once_with(12345)
      mock_killpg.assert_called_once_with(12345, signal.SIGTERM)


if __name__ == "__main__":
  unittest.main()
