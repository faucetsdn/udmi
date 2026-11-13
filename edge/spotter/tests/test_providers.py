#!/usr/bin/env python3
"""Unit tests for discovery family providers: BACnet, Ether, and Passive."""

import subprocess
import tempfile
import threading
import time
import unittest
from unittest.mock import MagicMock, patch

from BAC0.core.devices.Device import DeviceDisconnected
from BAC0.core.io.IOExceptions import SegmentationNotSupported
from edge.spotter.src.providers.bacnet import BacnetFamilyProvider
from edge.spotter.src.providers.ether import EtherFamilyProvider
from edge.spotter.src.providers.ether import expand_ip_targets
from edge.spotter.src.providers.ether import get_mac_for_ip
from edge.spotter.src.providers.ether import parse_nmap_xml
from edge.spotter.src.providers.passive import PassiveFamilyProvider
from edge.spotter.src.providers.passive import PassiveScanRecord
from edge.spotter.src.providers.passive import PRIVATE_IP_BPF_FILTER
from edge.spotter.src.providers.passive import extract_bacnet_iam
from udmi.schema import FamilyDiscoveryConfig


class TestBacnetFamilyProvider(unittest.TestCase):
  """Unit tests for BacnetFamilyProvider across UDMI BACnet spec depths."""

  @patch("edge.spotter.src.providers.bacnet.BAC0")
  def test_bacnet_depth_buckets(self, mock_bac0):
    """Verifies buckets depth emits network and UDP port without device addr."""
    mock_client = MagicMock()
    mock_bac0.lite.return_value = mock_client
    mock_client.known_network_numbers = [9288, 6100]

    provider = BacnetFamilyProvider(bacnet_ip="127.0.0.1", bacnet_port=47808)
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T12:00:00Z", depth="buckets"
    )

    published = []
    provider.start_scan(
        config, lambda dev_id, evt: published.append((dev_id, evt))
    )

    # Start (0), 2 buckets (1, 2), Stop (-3)
    self.assertEqual(len(published), 4)
    self.assertEqual(published[0][1].event_no, 0)
    b1 = published[1][1]
    self.assertEqual(b1.family, "bacnet")
    self.assertEqual(b1.network, "6100")
    self.assertEqual(b1.families["ipv4"].port, 47808)
    self.assertIsNone(b1.addr)
    self.assertIsNone(b1.system)
    self.assertIsNone(b1.refs)

    b2 = published[2][1]
    self.assertEqual(b2.network, "9288")
    self.assertEqual(b2.families["ipv4"].port, 47808)
    self.assertIsNone(b2.addr)
    self.assertEqual(published[3][1].event_no, -3)

  @patch("edge.spotter.src.providers.bacnet._get_mac_for_ip")
  @patch("edge.spotter.src.providers.bacnet.BAC0")
  def test_bacnet_depth_entries_ipv4_port_and_ethmac(
      self, mock_bac0, mock_get_mac
  ):
    """Verifies entries depth populates addr, ipv4 addr/port, and ethmac."""
    mock_client = MagicMock()
    mock_bac0.lite.return_value = mock_client
    mock_get_mac.return_value = "82:CC:18:9A:45:1C"

    provider = BacnetFamilyProvider(bacnet_ip="127.0.0.1", bacnet_port=47808)
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T12:00:00Z", depth="entries"
    )

    event = provider.discover_device("192.168.10.45", 291842, config)

    self.assertEqual(event.addr, "291842")
    self.assertEqual(event.family, "bacnet")
    self.assertEqual(event.families["ipv4"].addr, "192.168.10.45")
    self.assertEqual(event.families["ipv4"].port, 47808)
    self.assertEqual(event.families["ethmac"].addr, "82:CC:18:9A:45:1C")
    self.assertIsNone(event.system)
    self.assertIsNone(event.refs)
    mock_client.readMultiple.assert_not_called()

  @patch("edge.spotter.src.providers.bacnet.BAC0")
  def test_bacnet_depth_entries_routed_mstp(self, mock_bac0):
    """Verifies routed MS/TP address '6100:10' sets network and mstp family."""
    mock_client = MagicMock()
    mock_bac0.lite.return_value = mock_client

    provider = BacnetFamilyProvider()
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T12:00:00Z", depth="entries"
    )

    event = provider.discover_device("6100:10", 200011, config)

    self.assertEqual(event.addr, "200011")
    self.assertEqual(event.network, "6100")
    self.assertNotIn("ipv4", event.families)
    self.assertNotIn("bacnet", event.families)
    self.assertEqual(event.families["mstp"].addr, "10")

  @patch("edge.spotter.src.providers.bacnet.BAC0")
  def test_global_bacnet_scan_system_depth(self, mock_bac0):
    """Verifies BAC0 device discovery and spec-compliant system properties."""
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
        8,
        1,
        14,
    )

    provider = BacnetFamilyProvider(bacnet_ip="127.0.0.1", bacnet_port=47808)
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T12:00:00Z", depth="system"
    )

    event = provider.discover_device("192.168.1.50:47808", 1234, config)

    self.assertEqual(event.addr, "1234")
    self.assertEqual(event.family, "bacnet")
    self.assertEqual(event.families["ipv4"].addr, "192.168.1.50")
    self.assertEqual(event.families["ipv4"].port, 47808)
    self.assertEqual(event.system.name, "Main-AHU")
    self.assertEqual(event.system.description, "Air Handler")
    self.assertEqual(event.system.serial_no, "SN-9999")
    self.assertEqual(event.system.hardware.make, "Delta")
    self.assertEqual(event.system.hardware.model, "DSC-1212")
    self.assertEqual(event.system.ancillary["firmware-revision"], "v1.2.3")
    self.assertEqual(
        event.system.ancillary["application-software-version"], "App-4.0"
    )
    self.assertEqual(event.system.ancillary["location"], "Roof")
    self.assertEqual(event.system.ancillary["vendor-identifier"], 8)
    self.assertEqual(event.system.ancillary["protocol-version"], 1)
    self.assertEqual(event.system.ancillary["protocol-revision"], 14)
    self.assertNotIn("name", event.system.ancillary)
    self.assertNotIn("description", event.system.ancillary)

  @patch("edge.spotter.src.providers.bacnet.BAC0")
  def test_bacnet_system_segmentation_fallback(self, mock_bac0):
    """Verifies ReadPropertyMultiple falls back to ReadProperty on segmentation error."""
    mock_client = MagicMock()
    mock_bac0.lite.return_value = mock_client
    mock_client.readMultiple.side_effect = SegmentationNotSupported(
        "segmentation-not-supported"
    )
    prop_values = {
        "objectName": "AHU-Seg",
        "vendorName": "Trane",
        "firmwareRevision": "2.0",
        "modelName": "UC400",
        "serialNumber": "SN-400",
        "description": "Segmented Controller",
        "location": "Basement",
        "applicationSoftwareVersion": "1.1",
        "vendorIdentifier": 2,
        "protocolVersion": 1,
        "protocolRevision": 12,
    }
    mock_client.read.side_effect = (
        lambda req: prop_values.get(req.split()[-1])
    )

    provider = BacnetFamilyProvider()
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T12:00:00Z", depth="system"
    )
    event = provider.discover_device("192.168.1.60", 4001, config)

    self.assertIsNone(event.status)
    self.assertEqual(event.system.name, "AHU-Seg")
    self.assertEqual(event.system.hardware.make, "Trane")
    self.assertEqual(event.system.hardware.model, "UC400")
    self.assertEqual(event.system.ancillary["firmware-revision"], "2.0")
    self.assertEqual(event.system.ancillary["vendor-identifier"], 2)

  @patch("edge.spotter.src.providers.bacnet.BAC0")
  def test_bacnet_details_refs_enumeration(self, mock_bac0):
    """Verifies BACnet point reference extraction at details depth."""
    mock_client = MagicMock()
    mock_bac0.lite.return_value = mock_client

    mock_dev = MagicMock()
    av_point = MagicMock()
    av_point.properties.name = "zone_temp_sp"
    av_point.properties.description = "Zone Temperature Setpoint"
    av_point.properties.type = "analogValue"
    av_point.properties.address = "1"
    av_point.properties.units_state = "degrees-celsius"
    av_point.properties.writable = None
    av_point.properties.bacnet_properties = {
        "outOfService": False,
        "priorityArray": [None] * 16,
    }
    av_point.lastValue = 22.5

    bi_point = MagicMock()
    bi_point.properties.name = "fan_status"
    bi_point.properties.description = "Supply Fan Status"
    bi_point.properties.type = "binaryInput"
    bi_point.properties.address = "2"
    bi_point.properties.units_state = ("Off", "On")
    bi_point.properties.writable = None
    bi_point.properties.bacnet_properties = {}
    bi_point.lastValue = "On"

    bv_point = MagicMock()
    bv_point.properties.name = "damper_cmd"
    bv_point.properties.description = "Damper Command"
    bv_point.properties.type = "binaryValue"
    bv_point.properties.address = "3"
    bv_point.properties.units_state = (None, None)
    bv_point.properties.writable = None
    bv_point.properties.bacnet_properties = {"priorityArray": None}
    bv_point.lastValue = "active"

    mock_dev.points = [av_point, bi_point, bv_point]
    mock_bac0.device.return_value = mock_dev

    provider = BacnetFamilyProvider()
    refs = provider.enumerate_refs("192.168.1.50 1234")

    self.assertIn("AV/1", refs)
    self.assertEqual(refs["AV/1"].name, "zone_temp_sp")
    self.assertEqual(refs["AV/1"].type, "analog-value")
    self.assertEqual(refs["AV/1"].units, "degrees-celsius")
    self.assertTrue(refs["AV/1"].writable)
    self.assertEqual(refs["AV/1"].ancillary["present-value"], 22.5)
    self.assertIs(refs["AV/1"].ancillary["out-of-service"], False)

    self.assertIn("BI/2", refs)
    self.assertEqual(refs["BI/2"].name, "fan_status")
    self.assertEqual(refs["BI/2"].type, "binary-input")
    self.assertFalse(refs["BI/2"].writable)
    self.assertEqual(refs["BI/2"].possible_values, ["Off", "On"])
    self.assertEqual(refs["BI/2"].ancillary["present-value"], "On")

    self.assertIn("BV/3", refs)
    self.assertEqual(refs["BV/3"].type, "binary-value")
    # BV/3 has priorityArray=None so it is non-commandable (writable=False)
    self.assertFalse(refs["BV/3"].writable)
    self.assertEqual(refs["BV/3"].possible_values, ["inactive", "active"])

  @patch("edge.spotter.src.providers.bacnet.BAC0")
  def test_bacnet_addrs_instance_and_range_filtering(self, mock_bac0):
    """Verifies BACnet addrs instance and range filtering and self-exclusion."""
    mock_client = MagicMock()
    mock_bac0.lite.return_value = mock_client
    mock_client.discoveredDevices = {
        ("192.168.1.10", 1005): "Device",
        ("192.168.1.11", 2500): "Device",
        ("192.168.1.12", 291842): "Device",
        ("192.168.1.99", 4194300): "SelfDevice",
    }

    provider = BacnetFamilyProvider(unchanged_threshold_sec=1)
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T12:00:00Z",
        depth="entries",
        addrs=["1000-1999", "291842", "4194300"],
    )

    published = []
    provider.start_scan(
        config, lambda dev_id, evt: published.append((dev_id, evt))
    )

    addrs_found = [evt.addr for _, evt in published if evt.addr is not None]
    self.assertEqual(addrs_found, ["1005", "291842"])

  @patch("edge.spotter.src.providers.bacnet.BAC0")
  def test_bacnet_invalid_addrs_and_networks_fail_fast(self, mock_bac0):
    """Verifies invalid BACnet addrs or networks raise ValueError."""
    provider = BacnetFamilyProvider()
    for bad_addr in ("0123", "2000-1000", "192.168.1.1", "5000000"):
      cfg = FamilyDiscoveryConfig(
          generation="2026-11-10T12:00:00Z", addrs=[bad_addr]
      )
      with self.assertRaises(ValueError):
        provider.start_scan(cfg, MagicMock())

    for bad_net in ("0", "65535", "010", "net-1"):
      cfg = FamilyDiscoveryConfig(
          generation="2026-11-10T12:00:00Z", networks=[bad_net]
      )
      with self.assertRaises(ValueError):
        provider.start_scan(cfg, MagicMock())

  @patch("edge.spotter.src.providers.bacnet.BAC0")
  def test_bacnet_refs_disconnected_fails_fast(self, mock_bac0):
    """Verifies silent BAC0.device timeouts surface as discovery.device.enumerate status."""
    mock_client = MagicMock()
    mock_bac0.lite.return_value = mock_client
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

    disconnected_dev = DeviceDisconnected.__new__(DeviceDisconnected)
    disconnected_dev.points = []
    disconnected_dev.properties = MagicMock()
    disconnected_dev.properties.objects_list = []

    empty_objlist_dev = MagicMock()
    empty_objlist_dev.points = []
    empty_objlist_dev.properties.objects_list = []

    provider = BacnetFamilyProvider()
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T12:00:00Z", depth="details"
    )

    for timed_out_dev in (disconnected_dev, empty_objlist_dev):
      mock_bac0.device.return_value = timed_out_dev
      event = provider.discover_device("192.168.1.50", 12341, config)
      self.assertIsNone(event.refs)
      self.assertIsNotNone(event.status)
      self.assertEqual(event.status.category, "discovery.device.enumerate")
      self.assertEqual(event.status.level, 500)
      self.assertIsNotNone(event.status.timestamp)
      self.assertIn("Point enumeration failed", event.status.message)

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
        generation="2026-11-10T12:00:00Z", depth="system"
    )

    published = []
    provider.start_scan(
        config, lambda dev_id, evt: published.append((dev_id, evt))
    )

    mock_client.discover.assert_called_once_with(
        global_broadcast=True, reset=True
    )
    self.assertEqual(len(published), 3)
    self.assertEqual(published[0][1].event_no, 0)
    self.assertEqual(published[1][0], "1234")
    self.assertEqual(published[1][1].addr, "1234")
    self.assertEqual(published[1][1].event_no, 1)
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
        generation="2026-11-10T12:00:00Z",
        depth="entries",
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

  @patch("edge.spotter.src.providers.ether.get_hostname_for_ip")
  @patch("subprocess.run")
  def test_ping_scan_success(self, mock_subproc_run, mock_hostname):
    """Verifies ICMP ping sweep emission of discovery events at entries depth."""
    mock_res = MagicMock()
    mock_res.returncode = 0
    mock_subproc_run.return_value = mock_res
    mock_hostname.return_value = None

    provider = EtherFamilyProvider(ping_concurrency=2)
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T12:00:00Z",
        depth="entries",
        addrs=["10.0.0.1"],
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

  @patch("edge.spotter.src.providers.ether.get_hostname_for_ip")
  @patch("subprocess.run")
  def test_ping_cidr_expansion_and_subnet_fallback(
      self, mock_subproc_run, mock_hostname
  ):
    """Verifies CIDR netblock expansion and subnet_filter fallback."""
    mock_res = MagicMock()
    mock_res.returncode = 0
    mock_subproc_run.return_value = mock_res
    mock_hostname.return_value = None

    self.assertEqual(
        list(expand_ip_targets(["192.168.1.0/30", "10.0.0.5"])),
        ["192.168.1.1", "192.168.1.2", "10.0.0.5"],
    )

    # Fallback to configured subnet_filter when addrs and networks are omitted
    provider = EtherFamilyProvider(
        ping_concurrency=2, subnet_filter="192.168.2.0/30"
    )
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T12:00:00Z", depth="entries"
    )
    published = []
    provider.start_scan(
        config, lambda dev_id, evt: published.append((dev_id, evt))
    )
    found_ips = sorted(
        evt.families["ipv4"].addr
        for _, evt in published
        if evt.families and "ipv4" in evt.families
    )
    self.assertEqual(found_ips, ["192.168.2.1", "192.168.2.2"])

  def test_ether_buckets_depth(self):
    """Verifies buckets depth emits numeric VLAN network IDs per EtherFamilyProvider."""
    provider = EtherFamilyProvider(subnet_filter="10.20.30.5/24")
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T12:00:00Z", depth="buckets"
    )
    published = []
    provider.start_scan(
        config, lambda dev_id, evt: published.append((dev_id, evt))
    )
    self.assertEqual(len(published), 3)
    self.assertEqual(published[1][1].network, "1")
    self.assertIsNone(published[1][1].addr)

    # VLAN ID in networks sets event.network to the VLAN ID
    vlan_cfg = FamilyDiscoveryConfig(
        generation="2026-11-10T12:05:00Z",
        depth="buckets",
        networks=["100"],
    )
    vlan_pub = []
    provider.start_scan(
        vlan_cfg, lambda dev_id, evt: vlan_pub.append((dev_id, evt))
    )
    self.assertEqual(len(vlan_pub), 3)
    self.assertEqual(vlan_pub[1][1].network, "100")

    # Invalid VLAN ID (> 4094) fails fast
    bad_vlan_cfg = FamilyDiscoveryConfig(
        generation="2026-11-10T12:10:00Z",
        depth="buckets",
        networks=["5000"],
    )
    with self.assertRaises(ValueError):
      provider.start_scan(bad_vlan_cfg, MagicMock())

  def test_ether_rejects_legacy_non_canonical_depths(self):
    """Verifies legacy depths ('ping', 'ports', 'services') fail fast."""
    provider = EtherFamilyProvider()
    for bad_depth in ("ping", "ports", "services", "invalid"):
      config = FamilyDiscoveryConfig(
          generation="2026-11-10T12:00:00Z",
          depth=bad_depth,
          addrs=["10.0.0.1"],
      )
      with self.assertRaises(ValueError):
        provider.start_scan(config, MagicMock())

  @patch("edge.spotter.src.providers.ether.get_hostname_for_ip")
  @patch("edge.spotter.src.providers.ether.get_mac_for_ip")
  @patch("subprocess.run")
  def test_ping_scan_with_arp_resolution(
      self, mock_subproc_run, mock_get_mac, mock_hostname
  ):
    """Verifies ARP resolution attaches MAC address to ether discovery."""
    mock_res = MagicMock()
    mock_res.returncode = 0
    mock_subproc_run.return_value = mock_res
    mock_get_mac.return_value = "00:50:b6:ed:5f:77"
    mock_hostname.return_value = "ahu-1.local"

    provider = EtherFamilyProvider(ping_concurrency=2)
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T12:00:00Z",
        depth="entries",
        addrs=["10.0.0.1"],
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
    self.assertEqual(event.families["host"].addr, "ahu-1.local")
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
    """Verifies nmap XML output parsing for host IP, MAC, OS, and open ports."""
    sample_xml = """<?xml version="1.0"?>
    <nmaprun>
      <host>
        <status state="up"/>
        <address addr="192.168.1.10" addrtype="ipv4"/>
        <address addr="00:11:22:33:44:55" addrtype="mac" vendor="Delta"/>
        <hostnames><hostname name="controller-1.local"/></hostnames>
        <os><osmatch name="Linux 5.15"/></os>
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
    self.assertEqual(hosts[0].mac_vendor, "Delta")
    self.assertEqual(hosts[0].hostname, "controller-1.local")
    self.assertEqual(hosts[0].os_name, "Linux 5.15")
    self.assertEqual(len(hosts[0].ports), 2)
    self.assertEqual(hosts[0].ports[0].port_number, 80)
    self.assertEqual(hosts[0].ports[0].service_name, "http")
    self.assertEqual(hosts[0].ports[0].product, "Apache httpd")
    self.assertEqual(hosts[0].ports[0].version, "2.4.41")
    self.assertEqual(hosts[0].ports[0].banner, "Apache/2.4.41")

    with self.assertRaises(ValueError):
      parse_nmap_xml("<malformed_xml")

    truncated_xml = (
        '<?xml version="1.0"?><nmaprun>'
        '<host><status state="up"/><address addr="10.0.0.15" addrtype="ipv4"/></host>'
        '<host><status state="up"/>'
    )
    partial_hosts = parse_nmap_xml(truncated_xml, allow_partial=True)
    self.assertEqual(len(partial_hosts), 1)
    self.assertEqual(partial_hosts[0].ip, "10.0.0.15")

  @patch("os.path.exists", return_value=True)
  @patch("subprocess.Popen")
  def test_nmap_scan_deadline_flushes_partial_hosts(
      self, mock_popen, _mock_exists
  ):
    """Verifies Nmap scan flushes completed hosts when scan_duration_sec expires."""
    truncated_xml = (
        '<?xml version="1.0"?><nmaprun>'
        '<host><status state="up"/><address addr="10.0.0.25" addrtype="ipv4"/>'
        '<address addr="aa:bb:cc:dd:ee:ff" addrtype="mac"/></host>'
        '<host><status state="up"/>'
    )
    mock_proc = MagicMock()
    mock_proc.__enter__.return_value = mock_proc
    calls = []

    def fake_communicate(timeout=1.0):
      calls.append(timeout)
      if len(calls) == 1:
        time.sleep(0.02)
        raise subprocess.TimeoutExpired(cmd="nmap", timeout=timeout)
      return (truncated_xml, "")

    mock_proc.communicate.side_effect = fake_communicate
    mock_popen.return_value = mock_proc

    provider = EtherFamilyProvider()
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T12:00:00Z",
        depth="system",
        addrs=["10.0.0.25", "10.0.0.26"],
        scan_duration_sec=0.01,
    )
    published = []
    provider.start_scan(
        config, lambda dev_id, evt: published.append((dev_id, evt))
    )
    mock_proc.terminate.assert_called()
    self.assertEqual(len(published), 3)
    self.assertEqual(published[1][1].addr, "aa:bb:cc:dd:ee:ff")
    self.assertEqual(published[1][1].families["ipv4"].addr, "10.0.0.25")
    self.assertEqual(published[2][1].event_no, -2)

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
        generation="2026-11-10T12:00:00Z",
        depth="details",
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

  def test_extract_bacnet_iam(self):
    """Verifies BACnet I-Am 22-bit instance extraction from BVLC UDP payload."""
    # BVLC 0x81, Original-Broadcast-NPDU, APDU I-Am \x10\x00\xc4, ObjectIdentifier (device=8, instance=291842 = 0x047402)
    obj_id_bytes = ((8 << 22) | 291842).to_bytes(4, byteorder="big")
    payload = b"\x81\x0b\x00\x0c\x01\x20\xff\xff\x00\xff\x10\x00\xc4" + obj_id_bytes
    self.assertEqual(extract_bacnet_iam(payload), 291842)
    self.assertIsNone(extract_bacnet_iam(b"\x00\x01\x02"))

  def test_decode_bacnet_bvll_bbmd_and_routed_mstp(self):
    """Verifies Forwarded-NPDU real IP extraction, routed MS/TP SNET/SADR, and I-Am-Router DNETs."""
    from edge.spotter.src.providers.passive import decode_bacnet_bvll

    obj_id_bytes = ((8 << 22) | 200011).to_bytes(4, byteorder="big")
    # 1. Forwarded-NPDU (0x04) from BBMD 10.0.0.1 with real originating IP 192.168.50.12:47808
    #    and routed MS/TP SNET=6100, SLEN=1, SADR=10 (control=0x28: DNET=0xFFFF, DLEN=0 + SNET/SADR + HopCount)
    forwarded_routed_payload = (
        b"\x81\x04\x00\x1d"
        + bytes([192, 168, 50, 12])
        + (47808).to_bytes(2, "big")
        + b"\x01\x28\xff\xff\x00"
        + (6100).to_bytes(2, "big")
        + b"\x01\x0a\xff"
        + b"\x10\x00\xc4"
        + obj_id_bytes
    )
    decoded = decode_bacnet_bvll(
        forwarded_routed_payload, outer_ip="10.0.0.1", outer_port=47808
    )
    self.assertIsNotNone(decoded)
    self.assertEqual(decoded.device_id, 200011)
    self.assertEqual(decoded.orig_ip, "192.168.50.12")
    self.assertEqual(decoded.orig_port, 47808)
    self.assertEqual(decoded.snet, 6100)
    self.assertEqual(decoded.sadr, "10")

    # 2. I-Am-Router-To-Network (NPDU control=0x80, msg_type=0x01)
    router_payload = (
        b"\x81\x0b\x00\x0b\x01\x80\x01"
        + (6100).to_bytes(2, "big")
        + (6200).to_bytes(2, "big")
    )
    decoded_router = decode_bacnet_bvll(
        router_payload, outer_ip="192.168.1.1", outer_port=47808
    )
    self.assertIsNotNone(decoded_router)
    self.assertIsNone(decoded_router.device_id)
    self.assertEqual(decoded_router.iam_router_networks, (6100, 6200))

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

  def test_build_bpf_filter_invalid_fails_fast(self):
    """Verifies ValueError raised on invalid subnet string."""
    provider = PassiveFamilyProvider(subnet_filter="invalid_subnet")
    with self.assertRaises(ValueError):
      provider.build_bpf_filter(provider.subnet_filter)

  @patch("edge.spotter.src.providers.passive.scapy")
  def test_passive_scan_markers_and_lifecycle(self, mock_scapy):
    """Verifies start event (0) and finish marker emission in passive scan."""
    mock_sniffer = MagicMock()
    mock_scapy.sendrecv.AsyncSniffer.return_value = mock_sniffer

    provider = PassiveFamilyProvider(interface="eth0")
    config = FamilyDiscoveryConfig(
        generation="2026-11-10T09:00:00Z", scan_duration_sec=1
    )

    published = []

    def mock_publish(addr, event):
      published.append((addr, event))
      if event.event_no == 0:
        provider.stop_scan()

    provider.start_scan(config, mock_publish)

    self.assertGreaterEqual(len(published), 2)
    self.assertEqual(published[0][0], "self")
    self.assertEqual(published[0][1].event_no, 0)
    self.assertEqual(published[0][1].family, "ipv4")

    self.assertEqual(published[-1][0], "self")
    self.assertEqual(published[-1][1].event_no, -1)
    self.assertEqual(published[-1][1].family, "ipv4")

  @patch("edge.spotter.src.providers.passive.scapy")
  def test_passive_re_emission_after_holdoff(self, mock_scapy):
    """Verifies passive_sec holdoff suppression and re-emission after expiry."""
    provider = PassiveFamilyProvider(interface="eth0")
    provider._get_host = MagicMock(return_value=None)

    class FakePacket:

      def __contains__(self, layer):
        return layer is mock_scapy.layers.inet.IP

      def __getitem__(self, layer):
        ip_mock = MagicMock()
        ip_mock.src = "10.0.0.77"
        return ip_mock

    pkt = FakePacket()
    published = []

    provider._queue.put(pkt)
    provider._queue.put(pkt)

    def stop_after_queue():
      while not provider._queue.empty():
        time.sleep(0.01)
      time.sleep(0.05)
      provider._cancelled.set()

    stopper = threading.Thread(target=stop_after_queue)
    stopper.start()
    provider._queue_worker(passive_sec=1)
    stopper.join()
    provider._flush_pending_records(
        "2026-11-10T09:00:00Z", lambda addr, evt: published.append(evt)
    )

    self.assertEqual(len(published), 1)
    self.assertEqual(published[0].addr, "10.0.0.77")
    self.assertEqual(provider.passive_holdoff_count, 1)

    provider._cancelled.clear()
    provider._last_published_at["10.0.0.77"] = time.monotonic() - 2.0
    provider._queue.put(pkt)
    stopper2 = threading.Thread(target=stop_after_queue)
    stopper2.start()
    provider._queue_worker(passive_sec=1)
    stopper2.join()
    provider._flush_pending_records(
        "2026-11-10T09:00:00Z", lambda addr, evt: published.append(evt)
    )

    self.assertEqual(len(published), 2)
    self.assertEqual(published[1].addr, "10.0.0.77")
    self.assertEqual(published[1].event_no, 2)

  @patch("edge.spotter.src.providers.passive.scapy", None)
  def test_passive_scan_fails_fast_when_scapy_missing(self):
    """Verifies start_scan raises RuntimeError if scapy is not installed."""
    provider = PassiveFamilyProvider(interface="eth0")
    config = FamilyDiscoveryConfig(generation="2026-11-10T09:00:00Z")
    with self.assertRaises(RuntimeError) as ctx:
      provider.start_scan(config, MagicMock())
    self.assertIn("Scapy library is not installed", str(ctx.exception))


if __name__ == "__main__":
  unittest.main()
