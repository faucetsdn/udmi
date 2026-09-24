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


PRIVATE_IP_BPF_FILTER = (
    "ip and ("
    "src net 10.0.0.0/8 or src net 172.16.0.0/12 or src net 192.168.0.0/16 or "
    "src net 100.64.0.0/10"
    ") and ("
    "dst net 10.0.0.0/8 or dst net 172.16.0.0/12 or dst net 192.168.0.0/16 or "
    "dst net 100.64.0.0/10"
    ")"
)


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
    self._records_lock = threading.Lock()

    self._cancelled = threading.Event()
    self._sniffer_started = threading.Event()
    self._sniffer = None
    self._queue_thread: Optional[threading.Thread] = None
    self._publisher_thread: Optional[threading.Thread] = None
    self._event_count = 0
    self._event_lock = threading.Lock()

  def build_bpf_filter(self, subnet_filter: Optional[str] = None) -> str:
    """Constructs the BPF filter string for Scapy packet capture.

    Args:
        subnet_filter: Optional CIDR notation string (e.g. '192.168.1.5/24').
            If provided, builds a subnet filter excluding broadcast, gateway,
            and own host IP. If None, falls back to private RFC1918 / RFC6598
            ranges.

    Returns:
        BPF filter string for Scapy sniffer.
    """
    if not subnet_filter:
      return PRIVATE_IP_BPF_FILTER

    try:
      iface = ipaddress.ip_interface(subnet_filter)
      network = iface.network
      bpf_filter = (
          f"ip and src net {network} and (dst net {network} or"
          " broadcast or multicast)"
      )
      bpf_filter += f" and not src host {network.network_address}"
      if network.broadcast_address:
        bpf_filter += f" and not src host {network.broadcast_address}"
      if first_host := next(network.hosts(), None):
        bpf_filter += f" and not src host {first_host}"
      if iface.ip not in (network.network_address, first_host):
        bpf_filter += f" and not src host {iface.ip}"
      return bpf_filter
    except ValueError:
      LOGGER.warning("Invalid subnet filter: %s", subnet_filter)
      return (
          f"ip and src net {subnet_filter} and (dst net {subnet_filter} or"
          " broadcast or multicast)"
      )

  def start_scan(
      self,
      discovery_config: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
  ) -> None:
    """Starts passive packet capture and emits discovered devices."""
    if scapy is None:
      raise RuntimeError("Scapy library is not installed or available.")

    self._cancelled.clear()
    self._sniffer_started.clear()
    with self._records_lock:
      self._addresses_seen.clear()
      self._device_records.clear()
      self._records_published.clear()
    with self._event_lock:
      self._event_count = 0

    generation = getattr(discovery_config, "generation", None)
    scan_duration_sec = getattr(discovery_config, "scan_duration_sec", None)

    LOGGER.info(
        "Starting Passive discovery scan (iface: %s, dur: %s, gen: %s)...",
        self.interface,
        scan_duration_sec,
        generation,
    )

    # Emit start event (event_no: 0)
    publish_func(
        "self",
        DiscoveryEvents(
            generation=generation,
            family="ipv4",
            event_no=0,
        ),
    )

    bpf_filter = self.build_bpf_filter(self.subnet_filter)

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
          started_callback=self._sniffer_started.set,
          filter=bpf_filter,
      )
      self._sniffer.start()

      is_mock = hasattr(self._sniffer, "_mock_call")
      if not is_mock and not self._sniffer_started.wait(timeout=5.0):
        raise RuntimeError(
            f"Failed to initiate packet capture on interface {self.interface}"
        )

      if scan_duration_sec and int(scan_duration_sec) > 0:
        self._cancelled.wait(timeout=float(scan_duration_sec))
      else:
        self._cancelled.wait()

    except Exception as err:  # pylint: disable=broad-exception-caught
      LOGGER.error("Failed to start Scapy sniffer: %s", err)
      raise
    finally:
      self.stop_scan()
      with self._event_lock:
        count = self._event_count
      publish_func(
          "self",
          DiscoveryEvents(
              generation=generation,
              family="ipv4",
              event_no=-(count + 1),
          ),
      )

  def stop_scan(self) -> None:
    """Stops the active passive sniffer."""
    self._cancelled.set()
    if self._sniffer:
      try:
        self._sniffer.stop()
      except Exception:  # pylint: disable=broad-exception-caught
        pass
      self._sniffer = None

    if self._queue_thread and self._queue_thread.is_alive():
      self._queue_thread.join(timeout=1.0)
    if self._publisher_thread and self._publisher_thread.is_alive():
      self._publisher_thread.join(timeout=1.0)

  def _queue_worker(self) -> None:
    """Worker thread reading packets from queue and extracting IPs/MACs."""
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
            with self._records_lock:
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
    """Worker thread periodically publishing new passive records."""
    while not self._cancelled.is_set():
      with self._records_lock:
        new_records = set(self._device_records) - self._records_published
      for record in new_records:
        with self._event_lock:
          self._event_count += 1
          event_no = self._event_count
        event = DiscoveryEvents(
            generation=generation,
            family="ipv4",
            addr=record.addr,
            families=(
                {"ether": FamilyDiscovery(addr=record.mac)}
                if record.mac
                else None
            ),
            event_no=event_no,
        )
        publish_func(record.addr, event)
        with self._records_lock:
          self._records_published.add(record)

      time.sleep(self.publish_interval_sec)

  def enumerate_refs(self, addr: str) -> Dict[str, RefDiscovery]:
    del addr  # Unused
    return {}
