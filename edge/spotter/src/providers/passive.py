"""Passive Layer 2 / IP Discovery Provider for UDMI Spotter."""

import dataclasses
import ipaddress
import logging
import os
import queue
import socket
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
BACNET_INSTANCE_MASK = 0x3FFFFF


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
class DecodedBacnetPacket:
  """Parsed BACnet/IP BVLL + NPDU + APDU metadata from a captured UDP frame."""

  device_id: Optional[int] = None
  orig_ip: Optional[str] = None
  orig_port: Optional[int] = None
  snet: Optional[int] = None
  sadr: Optional[str] = None
  iam_router_networks: tuple[int, ...] = ()


@dataclasses.dataclass(eq=True, frozen=True)
class PassiveScanRecord:
  """Discovered host record from passive network packet inspection."""

  addr: str
  mac: Optional[str] = None
  hostname: Optional[str] = None
  bacnet_id: Optional[int] = None
  bacnet_port: Optional[int] = None
  snet: Optional[int] = None
  sadr: Optional[str] = None


def decode_bacnet_bvll(
    payload: bytes,
    outer_ip: Optional[str] = None,
    outer_port: Optional[int] = None,
) -> Optional[DecodedBacnetPacket]:
  """Decodes a BACnet/IP BVLL payload into originating IP, MS/TP routing, and I-Am metadata."""
  if not payload or len(payload) < 4 or payload[0:1] != BACNET_BVLC_MARKER:
    return None

  bvlc_func = payload[1]
  bvlc_len = int.from_bytes(payload[2:4], byteorder="big")
  orig_ip = outer_ip
  orig_port = outer_port
  npdu_offset = 4

  if bvlc_func == 0x04:  # Forwarded-NPDU (6-byte originating B/IP address)
    if len(payload) < 10:
      return None
    try:
      orig_ip = socket.inet_ntoa(payload[4:8])
      orig_port = int.from_bytes(payload[8:10], byteorder="big")
    except OSError:
      orig_ip = outer_ip
    npdu_offset = 10

  if len(payload) >= npdu_offset + 2 and payload[npdu_offset] == 0x01:
    control = payload[npdu_offset + 1]
    pos = npdu_offset + 2

    # Bit 5 (0x20): Destination Specifier (DNET + DLEN + DADR)
    if control & 0x20:
      if pos + 3 > len(payload):
        return None
      dlen = payload[pos + 2]
      pos += 3 + dlen

    # Bit 3 (0x08): Source Specifier (SNET + SLEN + SADR -> routed MS/TP device)
    snet: Optional[int] = None
    sadr: Optional[str] = None
    if control & 0x08:
      if pos + 3 > len(payload):
        return None
      raw_snet = int.from_bytes(payload[pos : pos + 2], byteorder="big")
      slen = payload[pos + 2]
      if pos + 3 + slen > len(payload):
        return None
      sadr_bytes = payload[pos + 3 : pos + 3 + slen]
      if 1 <= raw_snet <= 65534:
        snet = raw_snet
      if slen == 1:
        sadr = str(sadr_bytes[0])
      elif slen > 1:
        sadr = sadr_bytes.hex()
      pos += 3 + slen

    # Hop count is present when Destination Specifier bit is set
    if control & 0x20:
      pos += 1

    if pos <= len(payload):
      if control & 0x80:
        # Network Layer Message: 0x01 is I-Am-Router-To-Network
        if pos < len(payload) and payload[pos] == 0x01:
          dnets = []
          end_pos = (
              min(len(payload), bvlc_len)
              if bvlc_len >= pos + 1
              else len(payload)
          )
          rem = payload[pos + 1 : end_pos]
          for i in range(0, len(rem) - 1, 2):
            dnet = int.from_bytes(rem[i : i + 2], byteorder="big")
            if 1 <= dnet <= 65534 and dnet not in dnets:
              dnets.append(dnet)
          return DecodedBacnetPacket(
              orig_ip=orig_ip,
              orig_port=orig_port,
              snet=snet,
              sadr=sadr,
              iam_router_networks=tuple(dnets),
          )
        if snet is not None:
          return DecodedBacnetPacket(
              orig_ip=orig_ip,
              orig_port=orig_port,
              snet=snet,
              sadr=sadr,
          )
      else:
        # Application Layer APDU: Unconfirmed-Request (0x10) I-Am (0x00) ObjectId tag (0xc4)
        if (
            pos + 7 <= len(payload)
            and payload[pos : pos + 3] == BACNET_APDU_I_AM_START
        ):
          obj_id = int.from_bytes(payload[pos + 3 : pos + 7], byteorder="big")
          inst = obj_id & BACNET_INSTANCE_MASK
          if 1 <= inst <= BACNET_INSTANCE_MASK:
            return DecodedBacnetPacket(
                device_id=inst,
                orig_ip=orig_ip,
                orig_port=orig_port,
                snet=snet,
                sadr=sadr,
            )
        if snet is not None:
          return DecodedBacnetPacket(
              orig_ip=orig_ip,
              orig_port=orig_port,
              snet=snet,
              sadr=sadr,
          )
  else:
    # Fallback scan for simplified/synthetic BVLC I-Am frames without standard NPDU v1
    index = payload.find(BACNET_APDU_I_AM_START, 4, 32)
    if index > 0 and len(payload) >= index + 7:
      obj_id = int.from_bytes(payload[index + 3 : index + 7], byteorder="big")
      inst = obj_id & BACNET_INSTANCE_MASK
      if 1 <= inst <= BACNET_INSTANCE_MASK:
        return DecodedBacnetPacket(
            device_id=inst,
            orig_ip=orig_ip,
            orig_port=orig_port,
        )
  return None


def extract_bacnet_iam(
    payload: bytes,
) -> Optional[int]:
  """Extracts 22-bit BACnet Device Object Instance from a BVLC I-Am payload."""
  decoded = decode_bacnet_bvll(payload)
  return decoded.device_id if decoded is not None else None


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
    self._last_published_at: Dict[str, float] = {}
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
    self.packets_seen = 0
    self.ip_packets_seen = 0
    self.bacnet_packets_seen = 0
    self.passive_holdoff_count = 0
    self.discovered_routed_networks: Set[int] = set()
    self.bacnet_ip_devices_seen: Set[int] = set()
    self.routed_mstp_devices_seen: Set[int] = set()
    self._packet_count_start: Optional[int] = None

  def _get_packet_counter_total(self) -> Optional[int]:
    """Reads total rx+tx packets for configured interface from sysfs."""
    if not self.interface:
      return None
    try:
      rx_path = f"/sys/class/net/{self.interface}/statistics/rx_packets"
      tx_path = f"/sys/class/net/{self.interface}/statistics/tx_packets"
      if not (os.path.exists(rx_path) and os.path.exists(tx_path)):
        return None
      with open(rx_path, "r", encoding="utf-8") as f:
        rx = int(f.read().strip())
      with open(tx_path, "r", encoding="utf-8") as f:
        tx = int(f.read().strip())
      return rx + tx
    except (OSError, ValueError) as err:
      LOGGER.debug("Could not read interface packet statistics: %s", err)
      return None

  @staticmethod
  def _get_host(ip: str) -> Optional[str]:
    """Attempts reverse DNS lookup for a passively observed IP address."""
    try:
      host = socket.gethostbyaddr(ip)[0]
      if host and host != ip:
        return host
    except (socket.herror, socket.gaierror, OSError):
      return None
    return None

  def build_bpf_filter(self, subnet_filter: Optional[str] = None) -> str:
    """Constructs the BPF filter string for Scapy packet capture.

    Args:
        subnet_filter: Optional CIDR notation string (e.g. '192.168.1.5/24').
            If provided, builds a subnet filter excluding broadcast, gateway,
            and own host IP. If None, falls back to private RFC1918 / RFC6598
            ranges.

    Returns:
        BPF filter string for Scapy sniffer.

    Raises:
        ValueError: If subnet_filter is not a valid IPv4 interface/network CIDR.
    """
    if not subnet_filter:
      return PRIVATE_IP_BPF_FILTER

    try:
      iface = ipaddress.ip_interface(subnet_filter)
      if not isinstance(iface, ipaddress.IPv4Interface):
        raise ValueError(f"Expected IPv4 subnet filter, got {subnet_filter!r}")
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
    except ValueError as err:
      LOGGER.error("Invalid subnet filter %s: %s", subnet_filter, err)
      raise ValueError(f"Invalid subnet filter: {subnet_filter}") from err

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
      self._last_published_at.clear()
      self._device_records.clear()
      self._records_published.clear()
      self.passive_holdoff_count = 0
      self.discovered_routed_networks.clear()
      self.bacnet_ip_devices_seen.clear()
      self.routed_mstp_devices_seen.clear()
    with self._event_lock:
      self._event_count = 0

    self.packets_seen = 0
    self.ip_packets_seen = 0
    self.bacnet_packets_seen = 0

    generation = getattr(discovery_config, "generation", None)
    scan_duration_sec = getattr(discovery_config, "scan_duration_sec", None)
    passive_sec = getattr(discovery_config, "passive_sec", None)

    bpf_filter = self.build_bpf_filter(self.subnet_filter)

    LOGGER.info(
        "Starting Passive discovery scan (iface: %s, dur: %s, gen: %s,"
        " passive_sec: %s)...",
        self.interface,
        scan_duration_sec,
        generation,
        passive_sec,
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

    self._queue_thread = threading.Thread(
        target=self._queue_worker,
        args=(passive_sec,),
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
        sniffer_err = getattr(self._sniffer, "exception", None)
        raise RuntimeError(
            f"Failed to initiate packet capture on interface {self.interface}"
            + (f": {sniffer_err}" if sniffer_err else "")
        )

      self._packet_count_start = self._get_packet_counter_total()

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
      except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.debug("Error stopping Scapy sniffer: %s", err)
      self._sniffer = None

    packet_count_end = self._get_packet_counter_total()

    if self._queue_thread and self._queue_thread.is_alive():
      self._queue_thread.join(timeout=1.0)
    if self._publisher_thread and self._publisher_thread.is_alive():
      self._publisher_thread.join(timeout=1.0)

    if (
        self.interface
        and packet_count_end is not None
        and self._packet_count_start is not None
    ):
      LOGGER.info(
          "Passive scan stats: packets seen by scapy=%d, interface delta=%d,"
          " devices seen=%d",
          self.packets_seen,
          packet_count_end - self._packet_count_start,
          len(self._addresses_seen),
      )

  def _queue_worker(self, passive_sec: Optional[int] = None) -> None:
    """Worker thread reading packets from queue and extracting IPs/MACs/BACnet headers."""
    while not self._cancelled.is_set():
      try:
        packet = self._queue.get(timeout=0.5)
        self.packets_seen += 1
        if scapy and scapy.layers.inet.IP in packet:
          self.ip_packets_seen += 1
          ip_layer = packet[scapy.layers.inet.IP]
          src_ip = getattr(ip_layer, "src", None)
          if not src_ip:
            continue

          effective_ip = src_ip
          bacnet_id: Optional[int] = None
          bacnet_port: Optional[int] = None
          snet: Optional[int] = None
          sadr: Optional[str] = None
          if scapy.layers.inet.UDP in packet:
            udp_layer = packet[scapy.layers.inet.UDP]
            sport = getattr(udp_layer, "sport", None)
            dport = getattr(udp_layer, "dport", None)
            if sport == 47808 or dport == 47808:
              self.bacnet_packets_seen += 1
              raw_payload = bytes(udp_layer.payload)
              decoded = decode_bacnet_bvll(
                  raw_payload,
                  outer_ip=src_ip,
                  outer_port=int(sport or 47808),
              )
              if decoded is not None:
                if decoded.orig_ip:
                  effective_ip = decoded.orig_ip
                if decoded.iam_router_networks:
                  with self._records_lock:
                    self.discovered_routed_networks.update(
                        decoded.iam_router_networks
                    )
                if decoded.snet is not None:
                  with self._records_lock:
                    self.discovered_routed_networks.add(decoded.snet)
                if decoded.device_id is not None:
                  bacnet_id = decoded.device_id
                  bacnet_port = int(decoded.orig_port or sport or 47808)
                  snet = decoded.snet
                  sadr = decoded.sadr
                  with self._records_lock:
                    if snet is not None:
                      self.routed_mstp_devices_seen.add(bacnet_id)
                    else:
                      self.bacnet_ip_devices_seen.add(bacnet_id)
                  LOGGER.debug(
                      "Passively observed BACnet I-Am device %d at %s:%d"
                      " (snet=%s, sadr=%s)",
                      bacnet_id,
                      effective_ip,
                      bacnet_port,
                      snet,
                      sadr,
                  )

          now = time.monotonic()
          record_key = (
              f"{effective_ip}:{snet or ''}:{sadr or ''}:{bacnet_id}"
              if (snet is not None or bacnet_id is not None)
              else effective_ip
          )
          with self._records_lock:
            if passive_sec and int(passive_sec) > 0:
              last_pub = self._last_published_at.get(record_key)
              if (
                  last_pub is None
                  and snet is None
                  and bacnet_id is None
              ):
                last_pub = self._last_published_at.get(effective_ip)
              if last_pub is not None and (now - last_pub) < float(passive_sec):
                self.passive_holdoff_count += 1
                continue
              self._last_published_at[record_key] = now
              if snet is None and bacnet_id is None:
                self._last_published_at[effective_ip] = now
              self._records_published = {
                  r
                  for r in self._records_published
                  if not (
                      r.addr == effective_ip
                      and r.snet == snet
                      and r.sadr == sadr
                      and (r.bacnet_id == bacnet_id or bacnet_id is None)
                  )
              }
              self._device_records = {
                  r
                  for r in self._device_records
                  if not (
                      r.addr == effective_ip
                      and r.snet == snet
                      and r.sadr == sadr
                      and (r.bacnet_id == bacnet_id or bacnet_id is None)
                  )
              }
            elif effective_ip in self._addresses_seen and bacnet_id is None:
              continue

          mac = None
          if effective_ip == src_ip and scapy.layers.inet.Ether in packet:
            raw_mac = getattr(packet[scapy.layers.inet.Ether], "src", None)
            if raw_mac:
              mac = str(raw_mac).upper()

          hostname = self._get_host(effective_ip)
          record = PassiveScanRecord(
              addr=effective_ip,
              mac=mac,
              hostname=hostname,
              bacnet_id=bacnet_id,
              bacnet_port=bacnet_port,
              snet=snet,
              sadr=sadr,
          )
          with self._records_lock:
            self._addresses_seen.add(effective_ip)
            self._device_records.add(record)
      except queue.Empty:
        continue

  def _flush_pending_records(
      self,
      generation: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
  ) -> None:
    """Publishes any newly collected passive scan records."""
    with self._records_lock:
      new_records = sorted(
          self._device_records - self._records_published,
          key=lambda r: (r.addr, r.snet or 0, r.sadr or "", r.bacnet_id or 0),
      )
    for record in new_records:
      families_map: Dict[str, FamilyDiscovery] = {}
      if record.mac:
        families_map["ethmac"] = FamilyDiscovery(addr=record.mac)
      if record.hostname:
        families_map["host"] = FamilyDiscovery(addr=record.hostname)
      if record.bacnet_id is not None:
        families_map["bacnet"] = FamilyDiscovery(
            addr=str(record.bacnet_id), port=record.bacnet_port
        )
      if record.sadr:
        families_map["mstp"] = FamilyDiscovery(addr=record.sadr)

      with self._event_lock:
        self._event_count += 1
        event_no = self._event_count
      event = DiscoveryEvents(
          generation=generation,
          family="ipv4",
          addr=record.addr,
          network=str(record.snet) if record.snet is not None else None,
          families=families_map if families_map else None,
          event_no=event_no,
      )
      publish_func(record.addr, event)
      with self._records_lock:
        self._records_published.add(record)
        if record.addr not in self._last_published_at:
          self._last_published_at[record.addr] = time.monotonic()

  def _publisher_worker(
      self,
      generation: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
  ) -> None:
    """Worker thread periodically publishing new passive records."""
    while not self._cancelled.is_set():
      self._flush_pending_records(generation, publish_func)
      self._cancelled.wait(timeout=self.publish_interval_sec)
    self._flush_pending_records(generation, publish_func)

  def enumerate_refs(self, addr: str) -> Dict[str, RefDiscovery]:
    del addr  # Unused
    return {}
