"""Ethernet Protocol Family Provider for UDMI Spotter."""

import concurrent.futures
import dataclasses
import logging
import os
import subprocess
import threading
import time
from typing import Any, Callable, Dict, List, Optional
import xml.etree.ElementTree as ET

from udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import (
    DiscoveryEvents,
    FamilyDiscovery,
    RefDiscovery,
)

LOGGER = logging.getLogger(__name__)


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
  ports: List[PortInfo] = dataclasses.field(default_factory=list)


def parse_nmap_xml(xml_content: str) -> List[HostInfo]:
  """Parses nmap XML output into structured HostInfo dataclasses."""
  hosts = []
  try:
    root = ET.fromstring(xml_content)
    for host_elem in root.findall("host"):
      status_elem = host_elem.find("status")
      if status_elem is not None and status_elem.get("state") != "up":
        continue

      address_elem = host_elem.find("address[@addrtype='ipv4']")
      if address_elem is None:
        continue
      ip = address_elem.get("addr")

      mac_elem = host_elem.find("address[@addrtype='mac']")
      mac = (
          mac_elem.get("addr").lower()
          if mac_elem is not None and mac_elem.get("addr")
          else None
      )

      ports = []
      ports_elem = host_elem.find("ports")
      if ports_elem is not None:
        for port_elem in ports_elem.findall("port"):
          port_id = int(port_elem.get("portid"))
          protocol = port_elem.get("protocol")
          state_elem = port_elem.find("state")
          state = (
              state_elem.get("state")
              if state_elem is not None
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
      hosts.append(HostInfo(ip=ip, mac=mac, ports=ports))
  except Exception as err:  # pylint: disable=broad-exception-caught
    LOGGER.error("Failed to parse nmap XML: %s", err)
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
  except Exception:  # pylint: disable=broad-exception-caught
    pass
  return None


class EtherFamilyProvider(FamilyProvider):
  """Pluggable Ethernet protocol discovery provider (Ping & Nmap)."""

  def __init__(self, ping_concurrency: int = 4) -> None:
    """Initializes EtherFamilyProvider.

    Args:
        ping_concurrency: Maximum number of concurrent ping worker threads.
            Guaranteed to be at least 1.
    """
    self.ping_concurrency = max(1, int(ping_concurrency))
    self._cancelled = threading.Event()
    self._active_proc: Optional[subprocess.Popen] = None
    self._lock = threading.Lock()
    self._event_count = 0
    self._event_lock = threading.Lock()

  def start_scan(
      self,
      discovery_config: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
  ) -> None:
    """Executes ping or nmap scan based on depth configuration."""
    self._cancelled.clear()
    with self._event_lock:
      self._event_count = 0

    generation = getattr(discovery_config, "generation", None)
    raw_depth = getattr(discovery_config, "depth", "entries")
    depth_val = getattr(raw_depth, "value", raw_depth)
    depth = str(depth_val).lower() if depth_val else "entries"
    addrs = getattr(discovery_config, "addrs", None) or []
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
        addrs,
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

    if not addrs:
      LOGGER.warning("No target addresses provided for ether scan.")
      publish_func(
          "self",
          DiscoveryEvents(
              generation=generation,
              family="ether",
              event_no=-1,
          ),
      )
      return

    if depth in ("entries", "ping"):
      self._run_ping_scan(addrs, generation, publish_func, deadline=deadline)
    elif depth in ("details", "ports", "services", "parts"):
      self._run_nmap_scan(
          addrs, depth, generation, publish_func, deadline=deadline
      )
    else:
      LOGGER.warning(
          "Unrecognized ether scan depth: '%s'. Defaulting to ping.", depth
      )
      self._run_ping_scan(addrs, generation, publish_func, deadline=deadline)

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
        except Exception:  # pylint: disable=broad-exception-caught
          pass

  def _run_ping_scan(
      self,
      targets: List[str],
      generation: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
      deadline: Optional[float] = None,
  ) -> None:
    """Performs concurrent ICMP ping sweeps across target IP addresses."""
    ping_bin = (
        "/usr/bin/ping" if os.path.exists("/usr/bin/ping") else "/bin/ping"
    )
    if not os.path.exists(ping_bin):
      raise RuntimeError("Ping binary not found at /usr/bin/ping or /bin/ping")

    with concurrent.futures.ThreadPoolExecutor(
        max_workers=self.ping_concurrency
    ) as executor:
      futures = {
          executor.submit(
              self._ping_target, ip, generation, publish_func
          ): ip
          for ip in targets
      }
      for future in concurrent.futures.as_completed(futures):
        if self._cancelled.is_set() or (deadline and time.time() >= deadline):
          LOGGER.info(
              "Ping scan interrupted by cancellation or duration timeout."
          )
          executor.shutdown(wait=False, cancel_futures=True)
          break
        try:
          future.result()
        except Exception as e:  # pylint: disable=broad-exception-caught
          LOGGER.debug(
              "Ping worker exception for %s: %s", futures[future], e
          )

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
        with self._event_lock:
          self._event_count += 1
          event_no = self._event_count
        event = DiscoveryEvents(
            generation=generation,
            family="ether",
            addr=mac,
            families={"ipv4": FamilyDiscovery(addr=target_ip)},
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

  def _run_nmap_scan(
      self,
      targets: List[str],
      depth: str,
      generation: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
      *,
      deadline: Optional[float] = None,
  ) -> None:
    """Executes an Nmap port/service scan subprocess across target addresses."""
    if not os.path.exists("/usr/bin/nmap"):
      raise RuntimeError("nmap binary not found at /usr/bin/nmap")

    cmd = ["/usr/bin/nmap"]
    if depth in ("services", "parts"):
      cmd.extend(["--script", "banner", "-sV"])
    cmd.extend(["-p-", "-T3", "-oX", "-"])
    cmd.extend(targets)

    try:
      with self._lock:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            encoding="utf-8",
        )
        self._active_proc = proc

      stdout_chunks = []
      with proc:
        while True:
          if self._cancelled.is_set() or (deadline and time.time() >= deadline):
            LOGGER.info(
                "Nmap scan timed out or cancelled; terminating subprocess."
            )
            proc.terminate()
            try:
              proc.wait(timeout=2.0)
            except subprocess.TimeoutExpired:
              proc.kill()
            return
          try:
            stdout, _ = proc.communicate(timeout=1.0)
            if stdout:
              stdout_chunks.append(stdout)
            break
          except subprocess.TimeoutExpired:
            continue

        if self._cancelled.is_set():
          return

        if proc.returncode != 0:
          raise RuntimeError(
              f"Nmap process failed with exit code {proc.returncode}"
          )

        full_stdout = "".join(stdout_chunks)
        if full_stdout:
          hosts = parse_nmap_xml(full_stdout)
          for host in hosts:
            refs = {}
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
            with self._event_lock:
              self._event_count += 1
              event_no = self._event_count
            event = DiscoveryEvents(
                generation=generation,
                family="ether",
                addr=mac,
                families={"ipv4": FamilyDiscovery(addr=host.ip)},
                refs=refs if refs else None,
                event_no=event_no,
            )
            publish_func(host.ip, event)

    except FileNotFoundError as err:
      LOGGER.error("nmap binary not found at /usr/bin/nmap: %s", err)
      raise RuntimeError("nmap binary not found at /usr/bin/nmap") from err
    except Exception as e:  # pylint: disable=broad-exception-caught
      LOGGER.error("Nmap scan failed: %s", e)
      raise

  def enumerate_refs(self, addr: str) -> Dict[str, RefDiscovery]:
    """Enumerates references for target address (unused for ether)."""
    del addr
    return {}

