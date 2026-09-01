"""Unit tests for Spotter Protocol Family Providers."""

import unittest
from unittest.mock import MagicMock, Mock, patch

from udmi.schema import (
    DiscoveryEvents,
    FamilyDiscoveryConfig,
    FamilyDiscovery,
)

from edge.spotter.src.providers.bacnet import BacnetFamilyProvider
from edge.spotter.src.providers.ether import EtherFamilyProvider, parse_nmap_xml
from edge.spotter.src.providers.passive import PassiveFamilyProvider, PassiveScanRecord


class TestBacnetFamilyProvider(unittest.TestCase):
    """Unit tests for BacnetFamilyProvider."""

    @patch("edge.spotter.src.providers.bacnet.BAC0")
    def test_global_bacnet_scan(self, mock_bac0):
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
        config = FamilyDiscoveryConfig(generation="2026-09-01T12:00:00Z", depth="system")

        published_events = []
        def publish_callback(dev_id, event):
            published_events.append((dev_id, event))

        # Run discovery
        event = provider.discover_device("192.168.1.50:47808", 1234, config)

        self.assertEqual(event.addr, "1234")
        self.assertEqual(event.family, "bacnet")
        self.assertEqual(event.families["ipv4"].addr, "192.168.1.50")
        self.assertEqual(event.families["bacnet"].port, 47808)
        self.assertEqual(event.families["bacnet"].addr, "192.168.1.50:47808")
        self.assertEqual(event.system.name, "Main-AHU")
        self.assertEqual(event.system.hardware.make, "Delta")
        self.assertEqual(event.system.hardware.model, "DSC-1212")
        self.assertEqual(event.system.serial_no, "SN-9999")

    @patch("edge.spotter.src.providers.bacnet.BAC0")
    def test_bacnet_refs_enumeration(self, mock_bac0):
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
        mock_res = MagicMock()
        mock_res.returncode = 0
        mock_subproc_run.return_value = mock_res

        provider = EtherFamilyProvider(ping_concurrency=2)
        config = FamilyDiscoveryConfig(generation="2026-09-01T12:00:00Z", depth="ping", addrs=["10.0.0.1"])

        published = []
        provider.start_scan(config, lambda dev_id, evt: published.append((dev_id, evt)))

        self.assertEqual(len(published), 1)
        dev_id, event = published[0]
        self.assertEqual(dev_id, "10.0.0.1")
        self.assertEqual(event.family, "ether")
        self.assertEqual(event.families["ipv4"].addr, "10.0.0.1")

    def test_parse_nmap_xml(self):
        sample_xml = """<?xml version="1.0"?>
        <nmaprun>
          <host>
            <status state="up"/>
            <address addr="192.168.1.10" addrtype="ipv4"/>
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
        self.assertEqual(len(hosts[0].ports), 2)
        self.assertEqual(hosts[0].ports[0].port_number, 80)
        self.assertEqual(hosts[0].ports[0].service_name, "http")


class TestPassiveFamilyProvider(unittest.TestCase):
    """Unit tests for PassiveFamilyProvider."""

    def test_passive_record_deduplication(self):
        r1 = PassiveScanRecord(addr="10.0.0.5", mac="00:11:22:33:44:55")
        r2 = PassiveScanRecord(addr="10.0.0.5", mac="00:11:22:33:44:55")
        records = {r1, r2}
        self.assertEqual(len(records), 1)


if __name__ == "__main__":
    unittest.main()
