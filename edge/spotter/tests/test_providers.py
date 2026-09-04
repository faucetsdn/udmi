#!/usr/bin/env python3
"""Unit tests for discovery family providers: BACnet, Ether, and Passive."""

import tempfile
import unittest
from unittest.mock import MagicMock, patch

from edge.spotter.src.providers.bacnet import BacnetFamilyProvider
from edge.spotter.src.providers.ether import EtherFamilyProvider
from edge.spotter.src.providers.ether import get_mac_for_ip
from edge.spotter.src.providers.ether import parse_nmap_xml
from edge.spotter.src.providers.passive import PassiveScanRecord
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
    mock_dev.points = [mock_point]
    mock_bac0.device.return_value = mock_dev

    provider = BacnetFamilyProvider()
    refs = provider.enumerate_refs("192.168.1.50 1234")

    self.assertIn("AV:1", refs)
    self.assertEqual(refs["AV:1"].name, "zone_temp")
    self.assertEqual(refs["AV:1"].units, "degC")


class TestEtherFamilyProvider(unittest.TestCase):
  """Unit tests for EtherFamilyProvider."""

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

    self.assertEqual(len(published), 1)
    dev_id, event = published[0]
    self.assertEqual(dev_id, "10.0.0.1")
    self.assertEqual(event.family, "ether")
    self.assertEqual(event.families["ipv4"].addr, "10.0.0.1")
    self.assertIsNone(event.addr)

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

    self.assertEqual(len(published), 1)
    dev_id, event = published[0]
    self.assertEqual(dev_id, "10.0.0.1")
    self.assertEqual(event.family, "ether")
    self.assertEqual(event.addr, "00:50:b6:ed:5f:77")
    self.assertEqual(event.families["ipv4"].addr, "10.0.0.1")

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
            <service name="http"/>
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


class TestPassiveFamilyProvider(unittest.TestCase):
  """Unit tests for PassiveFamilyProvider."""

  def test_passive_record_deduplication(self):
    """Verifies that PassiveScanRecord instances deduplicate in sets."""
    r1 = PassiveScanRecord(addr="10.0.0.5", mac="00:11:22:33:44:55")
    r2 = PassiveScanRecord(addr="10.0.0.5", mac="00:11:22:33:44:55")
    records = {r1, r2}
    self.assertEqual(len(records), 1)


if __name__ == "__main__":
  unittest.main()

