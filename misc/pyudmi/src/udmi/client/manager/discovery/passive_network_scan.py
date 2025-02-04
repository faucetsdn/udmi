import queue
import threading

import scapy.all
import scapy.layers.inet

from udmi.client.manager.discovery.discovery_manager import DiscoveryManager


class PassiveNetworkScan(DiscoveryManager):
    scan_family = "ipv4"

    def __init__(self, state, publisher, *, interface=None):
        self.queue = queue.SimpleQueue()
        self.interface = interface
        self.addresses_seen = set()
        self.device_records = set()
        self.devices_records_published = set()
        self.publish_interval = 5
        self.cancel_threads = threading.Event()

        # Configure Scapy
        scapy.all.conf.layers.filter([
            scapy.layers.inet.Ether,
            scapy.layers.inet.IP,
            scapy.layers.inet.ICMP,
            scapy.layers.inet.UDP,
        ])
        super().__init__(state, publisher)

    def start_discovery(self) -> None:
        pass

    def stop_discovery(self) -> None:
        pass
