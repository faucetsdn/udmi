#!/usr/bin/env python3
"""Unit tests for discovery family providers: BACnet, Ether, and Passive."""

import tempfile
import unittest
from unittest.mock import MagicMock, patch

from edge.spotter.src.providers.bacnet import BacnetFamilyProvider
from edge.spotter.src.providers.ether import EtherFamilyProvider
from edge.spotter.src.providers.ether import get_mac_for_ip
from edge.spotter.src.providers.ether import parse_nmap_xml
from edge.spotter.src.providers.passive import PassiveFamilyProvider
from edge.spotter.src.providers.passive import PassiveScanRecord
from edge.spotter.src.providers.passive import PRIVATE_IP_BPF_FILTER
from udmi.schema import FamilyDiscoveryConfig


class TestBacnetFamilyProvider(unittest.TestCase):
  """Unit tests for BacnetFamilyProvider."""

  @patch("edge.spotter.src.providers.bacnet.BAC0")
  def test_global_bacnet_scan(self, mock_bac0):
    """Verifies BAC0 device discovery and metadata attribute extraction."""
    mock_client = MagicMock()
    mock_bac0.lite.return_value = mock_client
    mock_client.discoveredDevices = {("192.168.1.50", 1234): "Device"}
    mock_client.readMultiple.return_value = (
        "Main-AHU",
        "Delta",
        "v1.2.3",
        "DSC-1212",
        "SN-9999",
        "Air Handler",
        "Roof",
        "App-4.0",
    )

    provider = BacnetFamilyProvider(bacnet_ip="127.0.0.1", bacnet_port=47808)
    config = FamilyDiscoveryConfig(
        generation="2026-09-01T12:00:00Z", depth="system"
    )

    # Run discovery
    event = provider.discover_device("192.168.1.50:47808", 1234, config)

    self.assertEqual(event.addr, "1234")
    self.assertEqual(event.family, "bacnet")
    self.assertEqual(event.families["ipv4"].addr, "192.168.1.50")
    self.assertEqual(event.system.name, "Main-AHU")
    self.assertEqual(event.system.description, "Air Handler")
    self.assertEqual(event.system.ancillary["name"], "Main-AHU")
    self.assertEqual(event.system.ancillary["description"], "Air Handler")
    self.assertEqual(event.system.ancillary["location"], "Roof")
    self.assertEqual(event.system.ancillary["application_version"], "App-4.0")
    self.assertEqual(event.system.hardware.make, "Delta")
    self.assertEqual(event.system.hardware.model, "DSC-1212")
    self.assertEqual(event.system.serial_no, "SN-9999")

  @patch("edge.spotter.src.providers.bacnet.BAC0")
  def test_bacnet_refs_enumeration(self, mock_bac0):
    """Verifies BACnet point reference extraction."""
    mock_client = MagicMock()
    mock_bac0.lite.return_value = mock_client

    mock_dev = MagicMock()
    mock_point = MagicMock()
    mock_point.properties.name = "zone_temp"
    mock_point.properties.description = "Zone Temperature"
    mock_point.properties.type = "analogValue"
    mock_point.properties.address = "1"
    mock_point.properties.units_state = "degC"
    mock_point.lastValue = 22.5
    mock_dev.points = [mock_point]
    mock_bac0.device.return_value = mock_dev

    provider = BacnetFamilyProvider()
    refs = provider.enumerate_refs("192.168.1.50 1234")

    self.assertIn("AV:1", refs)
    self.assertEqual(refs["AV:1"].name, "zone_temp")
    self.assertEqual(refs["AV:1"].units, "degC")
    self.assertIsNotNone(refs["AV:1"].ancillary)
    self.assertEqual(refs["AV:1"].ancillary["present_value"], "22.5")

  @patch("edge.spotter.src.providers.bacnet.BAC0")
  def test_bacnet_scan_unchanged_threshold(self, mock_bac0):
    """Verifies BACnet scan terminates when no new devices appear."""
    mock_client = MagicMock()
    mock_bac0.lite.return_value = mock_client
    mock_client.discoveredDevices = {("192.168.1.50", 1234): "Device"}
    mock_client.readMultiple.return_value = [
        "Main-AHU",
        "Delta",
        "v1.2.3",
        "DSC-1212",
        "SN-9999",
        "Air Handler",
        "Roof",
        "App-4.0",
    ]

    provider = BacnetFamilyProvider(unchanged_threshold_sec=1)
    config = FamilyDiscoveryConfig(
        generation="2026-09-01T12:00:00Z", depth="system"
    )

    published = []
    provider.start_scan(
        config, lambda dev_id, evt: published.append((dev_id, evt))
    )

    self.assertEqual(len(published), 3)
    # Start marker
    self.assertEqual(published[0][1].event_no, 0)
    # Discovered device
    self.assertEqual(published[1][0], "1234")
    self.assertEqual(published[1][1].addr, "1234")
    self.assertEqual(published[1][1].event_no, 1)
    # Finish marker
    self.assertEqual(published[2][1].event_no, -2)


class TestEtherFamilyProvider(unittest.TestCase):
  """Unit tests for EtherFamilyProvider."""

  @patch("subprocess.run")
  def test_ping_scan_duration_timeout(self, mock_subproc_run):
    """Verifies ICMP ping sweep honors scan_duration_sec timeout."""
    mock_res = MagicMock()
    mock_res.returncode = 0
    mock_subproc_run.return_value = mock_res

    provider = EtherFamilyProvider(ping_concurrency=1)
    config = FamilyDiscoveryConfig(
        generation="2026-09-01T12:00:00Z",
        depth="ping",
        addrs=["10.0.0.1", "10.0.0.2", "10.0.0.3"],
        scan_duration_sec=0.001,
    )

    published = []
    provider.start_scan(
        config, lambda dev_id, evt: published.append((dev_id, evt))
    )

    self.assertGreaterEqual(len(published), 2)
    self.assertEqual(published[0][1].event_no, 0)
    self.assertLess(published[-1][1].event_no, 0)

  @patch("subprocess.run")
  def test_ping_scan_success(self, mock_subproc_run):
    """Verifies ICMP ping sweep emission of discovery events."""
    mock_res = MagicMock()
    mock_res.returncode = 0
    mock_subproc_run.return_value = mock_res

    provider = EtherFamilyProvider(ping_concurrency=2)
    config = FamilyDiscoveryConfig(
        generation="2026-09-01T12:00:00Z", depth="ping", addrs=["10.0.0.1"]
    )

    published = []
    provider.start_scan(
        config, lambda dev_id, evt: published.append((dev_id, evt))
    )

    self.assertEqual(len(published), 3)
    self.assertEqual(published[0][1].event_no, 0)
    dev_id, event = published[1]
    self.assertEqual(dev_id, "10.0.0.1")
    self.assertEqual(event.family, "ether")
    self.assertEqual(event.families["ipv4"].addr, "10.0.0.1")
    self.assertIsNone(event.addr)
    self.assertEqual(event.event_no, 1)
    self.assertEqual(published[2][1].event_no, -2)

  @patch("edge.spotter.src.providers.ether.get_mac_for_ip")
  @patch("subprocess.run")
  def test_ping_scan_with_arp_resolution(
      self, mock_subproc_run, mock_get_mac
  ):
    """Verifies ARP resolution attaches MAC address to ether discovery."""
    mock_res = MagicMock()
    mock_res.returncode = 0
    mock_subproc_run.return_value = mock_res
    mock_get_mac.return_value = "00:50:b6:ed:5f:77"

    provider = EtherFamilyProvider(ping_concurrency=2)
    config = FamilyDiscoveryConfig(
        generation="2026-09-01T12:00:00Z", depth="ping", addrs=["10.0.0.1"]
    )

    published = []
    provider.start_scan(
        config, lambda dev_id, evt: published.append((dev_id, evt))
    )

    self.assertEqual(len(published), 3)
    self.assertEqual(published[0][1].event_no, 0)
    dev_id, event = published[1]
    self.assertEqual(dev_id, "10.0.0.1")
    self.assertEqual(event.family, "ether")
    self.assertEqual(event.addr, "00:50:b6:ed:5f:77")
    self.assertEqual(event.families["ipv4"].addr, "10.0.0.1")
    self.assertEqual(event.event_no, 1)
    self.assertEqual(published[2][1].event_no, -2)

  def test_get_mac_for_ip(self):
    """Verifies ARP cache file parsing and MAC address lookup."""
    with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
      f.write(
          "IP address       HW type     Flags       HW address            Mask"
          "     Device\n"
      )
      f.write(
          "192.168.1.5      0x1         0x2         00:50:B6:ED:5F:77     *"
          "        eth0\n"
      )
      f.write(
          "192.168.1.6      0x1         0x0         00:00:00:00:00:00     *"
          "        eth0\n"
      )
      f.flush()

      self.assertEqual(
          get_mac_for_ip("192.168.1.5", arp_file=f.name), "00:50:b6:ed:5f:77"
      )
      self.assertIsNone(get_mac_for_ip("192.168.1.6", arp_file=f.name))
      self.assertIsNone(get_mac_for_ip("192.168.1.99", arp_file=f.name))

  def test_parse_nmap_xml(self):
    """Verifies nmap XML output parsing for host IP, MAC, and open ports."""
    sample_xml = """<?xml version="1.0"?>
    <nmaprun>
      <host>
        <status state="up"/>
        <address addr="192.168.1.10" addrtype="ipv4"/>
        <address addr="00:11:22:33:44:55" addrtype="mac"/>
        <ports>
          <port protocol="tcp" portid="80">
            <state state="open"/>
            <service name="http" product="Apache httpd" version="2.4.41"/>
            <script id="banner" output="Apache/2.4.41"/>
          </port>
          <port protocol="tcp" portid="443">
            <state state="open"/>
            <service name="https"/>
          </port>
        </ports>
      </host>
    </nmaprun>
    """
    hosts = parse_nmap_xml(sample_xml)
    self.assertEqual(len(hosts), 1)
    self.assertEqual(hosts[0].ip, "192.168.1.10")
    self.assertEqual(hosts[0].mac, "00:11:22:33:44:55")
    self.assertEqual(len(hosts[0].ports), 2)
    self.assertEqual(hosts[0].ports[0].port_number, 80)
    self.assertEqual(hosts[0].ports[0].service_name, "http")
    self.assertEqual(hosts[0].ports[0].product, "Apache httpd")
    self.assertEqual(hosts[0].ports[0].version, "2.4.41")
    self.assertEqual(hosts[0].ports[0].banner, "Apache/2.4.41")

  def test_ping_concurrency_clamping(self):
    """Verifies ping_concurrency clamps to at least 1."""
    provider_zero = EtherFamilyProvider(ping_concurrency=0)
    self.assertEqual(provider_zero.ping_concurrency, 1)

    provider_neg = EtherFamilyProvider(ping_concurrency=-5)
    self.assertEqual(provider_neg.ping_concurrency, 1)

    provider_custom = EtherFamilyProvider(ping_concurrency=8)
    self.assertEqual(provider_custom.ping_concurrency, 8)

  @patch("os.path.exists")
  def test_nmap_scan_fails_fast_when_binary_missing(self, mock_exists):
    """Verifies that nmap scan raises RuntimeError if nmap binary is missing."""
    mock_exists.return_value = False
    provider = EtherFamilyProvider()
    config = FamilyDiscoveryConfig(
        generation="2026-09-01T12:00:00Z",
        depth="ports",
        addrs=["10.0.0.1"],
    )
    with self.assertRaises(RuntimeError) as ctx:
      provider.start_scan(config, MagicMock())
    self.assertIn("nmap binary not found", str(ctx.exception))


class TestPassiveFamilyProvider(unittest.TestCase):
  """Unit tests for PassiveFamilyProvider."""

  def test_passive_record_deduplication(self):
    """Verifies that PassiveScanRecord instances deduplicate in sets."""
    r1 = PassiveScanRecord(addr="10.0.0.5", mac="00:11:22:33:44:55")
    r2 = PassiveScanRecord(addr="10.0.0.5", mac="00:11:22:33:44:55")
    records = {r1, r2}
    self.assertEqual(len(records), 1)

  def test_build_bpf_filter_default(self):
    """Verifies that default BPF filter matches private IP subnets."""
    provider = PassiveFamilyProvider()
    bpf = provider.build_bpf_filter()
    self.assertEqual(bpf, PRIVATE_IP_BPF_FILTER)

  def test_build_bpf_filter_with_subnet(self):
    """Verifies subnet BPF filter excludes network, broadcast, and gateway."""
    provider = PassiveFamilyProvider(subnet_filter="192.168.1.50/24")
    bpf = provider.build_bpf_filter(provider.subnet_filter)

    self.assertIn("src net 192.168.1.0/24", bpf)
    self.assertIn("and not src host 192.168.1.0", bpf)
    self.assertIn("and not src host 192.168.1.255", bpf)
    self.assertIn("and not src host 192.168.1.1", bpf)
    self.assertIn("and not src host 192.168.1.50", bpf)

  def test_build_bpf_filter_invalid(self):
    """Verifies graceful fallback on invalid subnet string."""
    provider = PassiveFamilyProvider(subnet_filter="invalid_subnet")
    bpf = provider.build_bpf_filter(provider.subnet_filter)
    self.assertIn("src net invalid_subnet", bpf)

  @patch("edge.spotter.src.providers.passive.scapy")
  def test_passive_scan_markers_and_lifecycle(self, mock_scapy):
    """Verifies start event (0) and finish marker emission in passive scan."""
    mock_sniffer = MagicMock()
    mock_scapy.sendrecv.AsyncSniffer.return_value = mock_sniffer

    provider = PassiveFamilyProvider(interface="eth0")
    config = FamilyDiscoveryConfig(
        generation="2026-09-11T09:00:00Z", scan_duration_sec=1
    )

    published = []

    def mock_publish(addr, event):
      published.append((addr, event))
      # Stop immediately on start event to test finish marker
      if event.event_no == 0:
        provider.stop_scan()

    provider.start_scan(config, mock_publish)

    self.assertGreaterEqual(len(published), 2)
    # Start marker
    self.assertEqual(published[0][0], "self")
    self.assertEqual(published[0][1].event_no, 0)
    self.assertEqual(published[0][1].family, "ipv4")

    # Finish marker
    self.assertEqual(published[-1][0], "self")
    self.assertEqual(published[-1][1].event_no, -1)
    self.assertEqual(published[-1][1].family, "ipv4")

  @patch("edge.spotter.src.providers.passive.scapy", None)
  def test_passive_scan_fails_fast_when_scapy_missing(self):
    """Verifies start_scan raises RuntimeError if scapy is not installed."""
    provider = PassiveFamilyProvider(interface="eth0")
    config = FamilyDiscoveryConfig(generation="2026-09-11T09:00:00Z")
    with self.assertRaises(RuntimeError) as ctx:
      provider.start_scan(config, MagicMock())
    self.assertIn("Scapy library is not installed", str(ctx.exception))


if __name__ == "__main__":
  unittest.main()

