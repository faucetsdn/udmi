"""Ethernet Protocol Family Provider for UDMI Spotter."""

import concurrent.futures
import dataclasses
import ipaddress
import logging
import os
import socket
import subprocess
import threading
import time
from typing import Any, Callable, Dict, Iterator, List, Optional
import xml.etree.ElementTree as ET

from udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import (
    DiscoveryEvents,
    FamilyDiscovery,
    RefDiscovery,
    StateSystemHardware,
    System,
)

LOGGER = logging.getLogger(__name__)

VALID_ETHER_DEPTHS = frozenset({
    "buckets",
    "entries",
    "system",
    "details",
    "parts",
    "refs",
})
MIN_VLAN_ID = 1
MAX_VLAN_ID = 4094


@dataclasses.dataclass
class PortInfo:
  """Parsed network port attributes."""

  port_number: int
  protocol: str
  state: str
  service_name: Optional[str] = None
  product: Optional[str] = None
  version: Optional[str] = None
  banner: Optional[str] = None


@dataclasses.dataclass
class HostInfo:
  """Parsed network host attributes."""

  ip: str
  mac: Optional[str] = None
  mac_vendor: Optional[str] = None
  hostname: Optional[str] = None
  os_name: Optional[str] = None
  ports: List[PortInfo] = dataclasses.field(default_factory=list)


def _parse_host_element(host_elem: ET.Element) -> Optional[HostInfo]:
  """Extracts a HostInfo record from a completed <host> XML element."""
  status_elem = host_elem.find("status")
  if status_elem is not None and status_elem.get("state") != "up":
    return None

  address_elem = host_elem.find("address[@addrtype='ipv4']")
  if address_elem is None:
    address_elem = host_elem.find("address")
  if address_elem is None or not address_elem.get("addr"):
    return None
  ip = address_elem.get("addr")

  mac_elem = host_elem.find("address[@addrtype='mac']")
  mac = (
      mac_elem.get("addr").lower()
      if mac_elem is not None and mac_elem.get("addr")
      else None
  )
  mac_vendor = (
      mac_elem.get("vendor")
      if mac_elem is not None and mac_elem.get("vendor")
      else None
  )

  hostname_elem = host_elem.find("hostnames/hostname")
  if hostname_elem is None:
    hostname_elem = host_elem.find("hostname")
  hostname = (
      hostname_elem.get("name")
      if hostname_elem is not None and hostname_elem.get("name")
      else None
  )

  osmatch_elem = host_elem.find("os/osmatch")
  os_name = (
      osmatch_elem.get("name")
      if osmatch_elem is not None and osmatch_elem.get("name")
      else None
  )

  ports: List[PortInfo] = []
  ports_elem = host_elem.find("ports")
  if ports_elem is not None:
    for port_elem in ports_elem.findall("port"):
      port_id = int(port_elem.get("portid"))
      protocol = port_elem.get("protocol") or "tcp"
      state_elem = port_elem.find("state")
      state = (
          state_elem.get("state")
          if state_elem is not None and state_elem.get("state")
          else "unknown"
      )

      service_elem = port_elem.find("service")
      service_name = None
      product = None
      version = None
      if service_elem is not None:
        service_name = service_elem.get("name")
        product = service_elem.get("product")
        version = service_elem.get("version")

      banner = None
      script_elem = port_elem.find("script[@id='banner']")
      if script_elem is not None:
        banner = script_elem.get("output")

      ports.append(
          PortInfo(
              port_number=port_id,
              protocol=protocol,
              state=state,
              service_name=service_name,
              product=product,
              version=version,
              banner=banner,
          )
      )
  return HostInfo(
      ip=ip,
      mac=mac,
      mac_vendor=mac_vendor,
      hostname=hostname,
      os_name=os_name,
      ports=ports,
  )


def parse_nmap_xml(
    xml_content: str, allow_partial: bool = False
) -> List[HostInfo]:
  """Parses nmap XML output into structured HostInfo dataclasses.

  Args:
      xml_content: Nmap XML output string.
      allow_partial: If True, extracts completed <host> elements from truncated
        XML output (e.g. when Nmap is terminated at scan_duration_sec deadline).

  Raises:
      ValueError: If xml_content is not valid XML and allow_partial is False.
  """
  hosts: List[HostInfo] = []
  if not allow_partial:
    try:
      root = ET.fromstring(xml_content)
    except ET.ParseError as err:
      LOGGER.error("Failed to parse nmap XML: %s", err)
      raise ValueError(f"Failed to parse nmap XML: {err}") from err

    for host_elem in root.findall("host"):
      host_info = _parse_host_element(host_elem)
      if host_info is not None:
        hosts.append(host_info)
    return hosts

  parser = ET.XMLPullParser(events=("end",))
  try:
    parser.feed(xml_content)
  except ET.ParseError:
    pass
  try:
    for _, elem in parser.read_events():
      if elem.tag == "host":
        host_info = _parse_host_element(elem)
        if host_info is not None:
          hosts.append(host_info)
  except ET.ParseError:
    pass
  return hosts


def get_mac_for_ip(ip: str, arp_file: str = "/proc/net/arp") -> Optional[str]:
  """Attempts to resolve Layer-2 MAC address for a local IP from arp file."""
  try:
    if os.path.exists(arp_file):
      with open(arp_file, "r", encoding="utf-8") as f:
        for line in f:
          parts = line.split()
          if len(parts) >= 4 and parts[0] == ip:
            mac = parts[3].lower()
            if mac != "00:00:00:00:00:00":
              return mac
  except OSError as err:
    LOGGER.debug("Could not read ARP cache %s: %s", arp_file, err)
  return None


def get_hostname_for_ip(ip: str) -> Optional[str]:
  """Attempts reverse DNS lookup for a target IP address."""
  try:
    host = socket.gethostbyaddr(ip)[0]
    if host and host != ip:
      return host
  except (socket.herror, socket.gaierror, OSError):
    return None
  return None


def expand_ip_targets(
    targets: List[str], fallback_subnet: Optional[str] = None
) -> Iterator[str]:
  """Expands a list of IP addresses, CIDR netblocks, or VLAN IDs into host IPs."""
  for target in targets:
    target_str = str(target).strip()
    if not target_str:
      continue
    if target_str.isdigit():
      vlan_id = int(target_str)
      if MIN_VLAN_ID <= vlan_id <= MAX_VLAN_ID:
        if fallback_subnet:
          yield from expand_ip_targets([fallback_subnet])
        continue
      raise ValueError(
          f"Ether VLAN network ID {target_str!r} must be between"
          f" {MIN_VLAN_ID} and {MAX_VLAN_ID}"
      )
    if "/" in target_str:
      net = ipaddress.ip_network(target_str, strict=False)
      if net.prefixlen >= 31:
        for ip in net:
          yield str(ip)
      else:
        for host in net.hosts():
          yield str(host)
    else:
      yield str(ipaddress.ip_address(target_str))


def _resolve_nmap_targets(
    targets: List[str], fallback_subnet: Optional[str] = None
) -> List[str]:
  """Validates and resolves IP addresses, CIDRs, or VLAN IDs into Nmap target specs."""
  resolved: List[str] = []
  for target in targets:
    target_str = str(target).strip()
    if not target_str:
      continue
    if target_str.isdigit():
      vlan_id = int(target_str)
      if MIN_VLAN_ID <= vlan_id <= MAX_VLAN_ID:
        if fallback_subnet:
          resolved.append(
              str(ipaddress.ip_interface(fallback_subnet).network)
          )
        continue
      raise ValueError(
          f"Ether VLAN network ID {target_str!r} must be between"
          f" {MIN_VLAN_ID} and {MAX_VLAN_ID}"
      )
    if "/" in target_str:
      resolved.append(str(ipaddress.ip_network(target_str, strict=False)))
    else:
      resolved.append(str(ipaddress.ip_address(target_str)))
  return resolved


class EtherFamilyProvider(FamilyProvider):
  """Pluggable Ethernet protocol discovery provider (Ping & Nmap)."""

  def __init__(
      self,
      ping_concurrency: int = 4,
      subnet_filter: Optional[str] = None,
  ) -> None:
    """Initializes EtherFamilyProvider.

    Args:
        ping_concurrency: Maximum number of concurrent ping worker threads.
            Guaranteed to be at least 1.
        subnet_filter: Default CIDR subnet to scan when neither addrs nor
            networks is provided in discovery_config.
    """
    self.ping_concurrency = max(1, int(ping_concurrency))
    self.subnet_filter = subnet_filter
    self._cancelled = threading.Event()
    self._active_proc: Optional[subprocess.Popen] = None
    self._lock = threading.Lock()
    self._event_count = 0
    self._event_lock = threading.Lock()

  def _resolve_targets(self, discovery_config: Any) -> List[str]:
    """Resolves scan targets from addrs, networks, or configured subnet_filter."""
    addrs = getattr(discovery_config, "addrs", None)
    if addrs:
      return [str(a).strip() for a in addrs if str(a).strip()]
    networks = getattr(discovery_config, "networks", None)
    if networks:
      return [str(n).strip() for n in networks if str(n).strip()]
    if self.subnet_filter:
      return [str(self.subnet_filter).strip()]
    return []

  @staticmethod
  def _resolve_depth(discovery_config: Any) -> str:
    """Resolves and validates the canonical UDMI discovery depth."""
    raw_depth = getattr(discovery_config, "depth", None)
    depth_val = getattr(raw_depth, "value", raw_depth)
    if depth_val is None:
      return "entries"
    depth_str = str(depth_val).strip().lower()
    if depth_str not in VALID_ETHER_DEPTHS:
      raise ValueError(
          f"Unsupported discovery depth for ether scan: {depth_val!r}. "
          f"Expected one of {sorted(VALID_ETHER_DEPTHS)}"
      )
    return depth_str

  def start_scan(
      self,
      discovery_config: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
  ) -> None:
    """Executes bucket, ping, or nmap scan based on canonical UDMI depth."""
    self._cancelled.clear()
    with self._event_lock:
      self._event_count = 0

    generation = getattr(discovery_config, "generation", None)
    depth = self._resolve_depth(discovery_config)
    targets = self._resolve_targets(discovery_config)
    scan_duration_sec = getattr(discovery_config, "scan_duration_sec", None)
    deadline = (
        (time.time() + float(scan_duration_sec))
        if (scan_duration_sec and float(scan_duration_sec) > 0)
        else None
    )

    LOGGER.info(
        "Starting Ether discovery scan (depth: %s, generation: %s,"
        " targets: %s, duration: %s)...",
        depth,
        generation,
        targets,
        scan_duration_sec,
    )

    # Emit start event (event_no: 0)
    publish_func(
        "self",
        DiscoveryEvents(
            generation=generation,
            family="ether",
            event_no=0,
        ),
    )

    if not targets:
      LOGGER.warning("No target addresses or subnets provided for ether scan.")
      publish_func(
          "self",
          DiscoveryEvents(
              generation=generation,
              family="ether",
              event_no=-1,
          ),
      )
      return

    if depth == "buckets":
      self._run_buckets_scan(targets, generation, publish_func)
    elif depth == "entries":
      self._run_ping_scan(targets, generation, publish_func, deadline=deadline)
    elif depth == "system":
      self._run_nmap_scan(
          targets,
          depth,
          generation,
          publish_func,
          include_refs=False,
          deadline=deadline,
      )
    else:  # details, parts, refs
      self._run_nmap_scan(
          targets,
          depth,
          generation,
          publish_func,
          include_refs=True,
          deadline=deadline,
      )

    with self._event_lock:
      count = self._event_count
    publish_func(
        "self",
        DiscoveryEvents(
            generation=generation,
            family="ether",
            event_no=-(count + 1),
        ),
    )

  def stop_scan(self) -> None:
    """Stops any active ether scan subprocess immediately."""
    LOGGER.info("Stopping Ether discovery scan...")
    self._cancelled.set()
    with self._lock:
      if self._active_proc and self._active_proc.poll() is None:
        try:
          self._active_proc.terminate()
        except OSError as err:
          LOGGER.debug("Error terminating ether scan subprocess: %s", err)

  def _run_buckets_scan(
      self,
      targets: List[str],
      generation: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
  ) -> None:
    """Emits discovered Ethernet VLAN buckets per EtherFamilyProvider spec."""
    seen_vlans = set()

    for target in targets:
      if self._cancelled.is_set():
        break
      target_str = str(target).strip()
      if not target_str:
        continue

      if target_str.isdigit():
        vlan_id = int(target_str)
        if not (MIN_VLAN_ID <= vlan_id <= MAX_VLAN_ID):
          raise ValueError(
              f"Ether network VLAN ID {target_str!r} must be between"
              f" {MIN_VLAN_ID} and {MAX_VLAN_ID}"
          )
        vlan_str = str(vlan_id)
      else:
        # Validate IPv4 subnet/address target and report untagged native VLAN 1
        ipaddress.ip_network(target_str, strict=False)
        vlan_str = "1"

      if vlan_str in seen_vlans:
        continue
      seen_vlans.add(vlan_str)

      with self._event_lock:
        self._event_count += 1
        event_no = self._event_count

      event = DiscoveryEvents(
          generation=generation,
          family="ether",
          network=vlan_str,
          event_no=event_no,
      )
      publish_func(vlan_str, event)

  def _run_ping_scan(
      self,
      targets: List[str],
      generation: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
      deadline: Optional[float] = None,
  ) -> None:
    """Performs bounded concurrent ICMP ping sweeps across target IPs/CIDRs."""
    ping_bin = (
        "/usr/bin/ping" if os.path.exists("/usr/bin/ping") else "/bin/ping"
    )
    if not os.path.exists(ping_bin):
      raise RuntimeError("Ping binary not found at /usr/bin/ping or /bin/ping")

    hosts_iter = expand_ip_targets(targets, fallback_subnet=self.subnet_filter)
    max_in_flight = max(self.ping_concurrency * 2, 1)

    with concurrent.futures.ThreadPoolExecutor(
        max_workers=self.ping_concurrency
    ) as executor:
      in_flight: Dict[concurrent.futures.Future, str] = {}
      exhausted = False

      while not exhausted or in_flight:
        if self._cancelled.is_set() or (deadline and time.time() >= deadline):
          LOGGER.info(
              "Ping scan interrupted by cancellation or duration timeout."
          )
          executor.shutdown(wait=False, cancel_futures=True)
          break

        while not exhausted and len(in_flight) < max_in_flight:
          if self._cancelled.is_set() or (
              deadline and time.time() >= deadline
          ):
            break
          try:
            ip = next(hosts_iter)
          except StopIteration:
            exhausted = True
            break
          fut = executor.submit(
              self._ping_target, ip, generation, publish_func
          )
          in_flight[fut] = ip

        if not in_flight:
          break

        return_when = (
            concurrent.futures.ALL_COMPLETED
            if exhausted
            else concurrent.futures.FIRST_COMPLETED
        )
        done, _ = concurrent.futures.wait(
            in_flight.keys(), timeout=0.5, return_when=return_when
        )
        for fut in done:
          ip = in_flight.pop(fut, "unknown")
          try:
            fut.result()
          except Exception as err:  # pylint: disable=broad-exception-caught
            LOGGER.debug("Ping worker exception for %s: %s", ip, err)

  def _ping_target(
      self,
      target_ip: str,
      generation: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
  ) -> bool:
    """Pings a single target IP and emits a discovery event if reachable."""
    if self._cancelled.is_set():
      return False

    ping_bin = (
        "/usr/bin/ping" if os.path.exists("/usr/bin/ping") else "/bin/ping"
    )
    try:
      res = subprocess.run(
          [ping_bin, "-c", "1", "-W", "2", target_ip],
          stdout=subprocess.PIPE,
          stderr=subprocess.STDOUT,
          encoding="utf-8",
          check=True,
          timeout=5,
      )
      if res.returncode == 0:
        mac = get_mac_for_ip(target_ip)
        hostname = get_hostname_for_ip(target_ip)
        families_map: Dict[str, FamilyDiscovery] = {
            "ipv4": FamilyDiscovery(addr=target_ip)
        }
        if hostname:
          families_map["host"] = FamilyDiscovery(addr=hostname)
        with self._event_lock:
          self._event_count += 1
          event_no = self._event_count
        event = DiscoveryEvents(
            generation=generation,
            family="ether",
            addr=mac,
            families=families_map,
            event_no=event_no,
        )
        publish_func(target_ip, event)
        return True
    except (
        subprocess.CalledProcessError,
        subprocess.TimeoutExpired,
    ):
      return False
    except FileNotFoundError as err:
      raise RuntimeError(f"Ping binary not found at {ping_bin}") from err
    return False

  def _publish_nmap_hosts(
      self,
      hosts: List[HostInfo],
      depth: str,
      generation: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
      *,
      include_refs: bool = True,
  ) -> None:
    """Emits DiscoveryEvents for each parsed Nmap HostInfo record."""
    for host in hosts:
      refs: Dict[str, RefDiscovery] = {}
      if include_refs:
        for p in host.ports:
          adjunct = {
              "port_number": str(p.port_number),
              "protocol": str(p.protocol),
              "state": str(p.state),
          }
          if p.service_name:
            adjunct["service"] = str(p.service_name)
          if p.product:
            adjunct["product"] = str(p.product)
          if p.version:
            adjunct["version"] = str(p.version)
          if p.banner:
            adjunct["banner"] = str(p.banner)

          refs[f"{p.port_number}"] = RefDiscovery(
              name=f"port_{p.port_number}",
              description=(
                  f"{p.protocol} service"
                  f' {p.service_name or "unknown"}'
              ),
              adjunct=adjunct,
          )

      mac = host.mac or get_mac_for_ip(host.ip)
      hostname = host.hostname or get_hostname_for_ip(host.ip)
      families_map: Dict[str, FamilyDiscovery] = {
          "ipv4": FamilyDiscovery(addr=host.ip)
      }
      if hostname:
        families_map["host"] = FamilyDiscovery(addr=hostname)

      sys_block: Optional[System] = None
      if host.os_name or host.mac_vendor or hostname or depth == "system":
        ancillary = {"os": host.os_name} if host.os_name else None
        hardware = (
            StateSystemHardware(make=host.mac_vendor)
            if host.mac_vendor
            else None
        )
        sys_block = System(
            name=hostname,
            hardware=hardware,
            ancillary=ancillary,
        )

      with self._event_lock:
        self._event_count += 1
        event_no = self._event_count
      event = DiscoveryEvents(
          generation=generation,
          family="ether",
          addr=mac,
          families=families_map,
          system=sys_block,
          refs=refs if refs else None,
          event_no=event_no,
      )
      publish_func(host.ip, event)

  def _run_nmap_scan(
      self,
      targets: List[str],
      depth: str,
      generation: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
      *,
      include_refs: bool = True,
      deadline: Optional[float] = None,
  ) -> None:
    """Executes an Nmap scan subprocess across target addresses."""
    if not os.path.exists("/usr/bin/nmap"):
      raise RuntimeError("nmap binary not found at /usr/bin/nmap")

    nmap_targets = _resolve_nmap_targets(
        targets, fallback_subnet=self.subnet_filter
    )
    if not nmap_targets:
      return

    cmd = ["/usr/bin/nmap"]
    if depth in ("system", "details", "parts", "refs"):
      cmd.extend(["--script", "banner", "-sV"])
    cmd.extend(["-p-", "-T4", "--max-hostgroup", "32", "-oX", "-"])
    cmd.extend(nmap_targets)

    try:
      with self._lock:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            encoding="utf-8",
        )
        self._active_proc = proc

      stdout_chunks: List[str] = []
      timed_out = False
      with proc:
        while True:
          if self._cancelled.is_set():
            LOGGER.info("Nmap scan cancelled; terminating subprocess.")
            proc.terminate()
            try:
              proc.wait(timeout=2.0)
            except subprocess.TimeoutExpired:
              proc.kill()
            return
          if deadline and time.time() >= deadline:
            LOGGER.info(
                "Nmap scan reached scan_duration_sec deadline; flushing"
                " completed hosts and terminating subprocess."
            )
            timed_out = True
            proc.terminate()
            try:
              rem_stdout, _ = proc.communicate(timeout=2.0)
              if rem_stdout:
                stdout_chunks.append(rem_stdout)
            except subprocess.TimeoutExpired:
              proc.kill()
            break
          wait_sec = (
              min(1.0, max(0.01, deadline - time.time()))
              if deadline
              else 1.0
          )
          try:
            stdout, _ = proc.communicate(timeout=wait_sec)
            if stdout:
              stdout_chunks.append(stdout)
            break
          except subprocess.TimeoutExpired as exc:
            if getattr(exc, "output", None):
              chunk = (
                  exc.output.decode("utf-8", errors="replace")
                  if isinstance(exc.output, bytes)
                  else str(exc.output)
              )
              stdout_chunks.append(chunk)
            continue

        if self._cancelled.is_set():
          return

        if not timed_out and proc.returncode != 0:
          raise RuntimeError(
              f"Nmap process failed with exit code {proc.returncode}"
          )

        full_stdout = "".join(stdout_chunks)
        if full_stdout:
          hosts = parse_nmap_xml(full_stdout, allow_partial=timed_out)
          self._publish_nmap_hosts(
              hosts,
              depth,
              generation,
              publish_func,
              include_refs=include_refs,
          )

    except FileNotFoundError as err:
      LOGGER.error("nmap binary not found at /usr/bin/nmap: %s", err)
      raise RuntimeError("nmap binary not found at /usr/bin/nmap") from err
    except Exception as err:  # pylint: disable=broad-exception-caught
      LOGGER.error("Nmap scan failed: %s", err)
      raise

  def enumerate_refs(self, addr: str) -> Dict[str, RefDiscovery]:
    """Enumerates references for target address (unused for ether)."""
    del addr
    return {}
