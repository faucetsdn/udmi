"""Passive Layer 2 / IP Discovery Provider for UDMI Spotter."""

import dataclasses
import ipaddress
import logging
import queue
import threading
import time
from typing import Any, Callable, Dict, Optional, Set

try:
    import scapy.all
    import scapy.layers.inet
    import scapy.sendrecv
except ImportError:
    scapy = None

from udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import (
    DiscoveryEvents,
    FamilyDiscovery,
    RefDiscovery,
)

LOGGER = logging.getLogger(__name__)

BACNET_BVLC_MARKER = b"\x81"
BACNET_APDU_I_AM_START = b"\x10\x00\xc4"


@dataclasses.dataclass(eq=True, frozen=True)
class PassiveScanRecord:
    addr: str
    mac: Optional[str] = None
    hostname: Optional[str] = None


class PassiveFamilyProvider(FamilyProvider):
    """Pluggable passive packet capture discovery provider using Scapy."""

    def __init__(
        self,
        interface: Optional[str] = None,
        subnet_filter: Optional[str] = None,
        publish_interval_sec: float = 2.0,
    ) -> None:
        self.interface = interface
        self.subnet_filter = subnet_filter
        self.publish_interval_sec = publish_interval_sec

        self._queue: queue.SimpleQueue = queue.SimpleQueue()
        self._addresses_seen: Set[str] = set()
        self._device_records: Set[PassiveScanRecord] = set()
        self._records_published: Set[PassiveScanRecord] = set()

        self._cancelled = threading.Event()
        self._sniffer = None
        self._queue_thread: Optional[threading.Thread] = None
        self._publisher_thread: Optional[threading.Thread] = None

    def start_scan(
        self,
        discovery_config: Any,
        publish_func: Callable[[str, DiscoveryEvents], None],
    ) -> None:
        """Starts passive packet capture and emits discovered devices."""
        if scapy is None:
            LOGGER.error("Scapy is not installed. Passive discovery unavailable.")
            return

        self._cancelled.clear()
        self._addresses_seen.clear()
        self._device_records.clear()
        self._records_published.clear()

        generation = getattr(discovery_config, "generation", None)
        scan_duration_sec = getattr(discovery_config, "scan_duration_sec", None)

        LOGGER.info(
            "Starting Passive discovery scan (interface: %s, duration: %s, generation: %s)...",
            self.interface,
            scan_duration_sec,
            generation,
        )

        bpf_filter = "ip"
        if self.subnet_filter:
            try:
                iface = ipaddress.ip_interface(self.subnet_filter)
                network = iface.network
                bpf_filter = f"ip and src net {network}"
            except ValueError:
                LOGGER.warning("Invalid subnet filter: %s", self.subnet_filter)

        self._queue_thread = threading.Thread(
            target=self._queue_worker,
            name="Passive-QueueWorker",
            daemon=True,
        )
        self._queue_thread.start()

        self._publisher_thread = threading.Thread(
            target=self._publisher_worker,
            args=(generation, publish_func),
            name="Passive-PublisherWorker",
            daemon=True,
        )
        self._publisher_thread.start()

        try:
            self._sniffer = scapy.sendrecv.AsyncSniffer(
                prn=self._queue.put,
                store=False,
                iface=self.interface,
                filter=bpf_filter,
            )
            self._sniffer.start()

            if scan_duration_sec and int(scan_duration_sec) > 0:
                self._cancelled.wait(timeout=float(scan_duration_sec))
            else:
                self._cancelled.wait()

        except Exception as err:  # pylint: disable=broad-exception-caught
            LOGGER.error("Failed to start Scapy sniffer: %s", err)
        finally:
            self.stop_scan()

    def stop_scan(self) -> None:
        """Stops the active passive sniffer."""
        self._cancelled.set()
        if self._sniffer:
            try:
                self._sniffer.stop()
            except Exception:
                pass
            self._sniffer = None

        if self._queue_thread and self._queue_thread.is_alive():
            self._queue_thread.join(timeout=1.0)
        if self._publisher_thread and self._publisher_thread.is_alive():
            self._publisher_thread.join(timeout=1.0)

    def _queue_worker(self) -> None:
        while not self._cancelled.is_set():
            try:
                packet = self._queue.get(timeout=0.5)
                if scapy and scapy.layers.inet.IP in packet:
                    ip_layer = packet[scapy.layers.inet.IP]
                    src_ip = ip_layer.src
                    if src_ip and src_ip not in self._addresses_seen:
                        mac = None
                        if scapy.layers.inet.Ether in packet:
                            mac = packet[scapy.layers.inet.Ether].src
                        self._addresses_seen.add(src_ip)
                        self._device_records.add(
                            PassiveScanRecord(addr=src_ip, mac=mac)
                        )
            except queue.Empty:
                continue

    def _publisher_worker(
        self,
        generation: Any,
        publish_func: Callable[[str, DiscoveryEvents], None],
    ) -> None:
        while not self._cancelled.is_set():
            new_records = self._device_records - self._records_published
            for record in new_records:
                event = DiscoveryEvents(
                    generation=generation,
                    family="ipv4",
                    addr=record.addr,
                    families=(
                        {"ether": FamilyDiscovery(addr=record.mac)}
                        if record.mac
                        else None
                    ),
                )
                publish_func(record.addr, event)
                self._records_published.add(record)

            time.sleep(self.publish_interval_sec)

    def enumerate_refs(self, addr: str) -> Dict[str, RefDiscovery]:
        return {}
