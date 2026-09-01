"""Ethernet Protocol Family Provider for UDMI Spotter."""

import concurrent.futures
import dataclasses
import logging
import os
import subprocess
import threading
import xml.etree.ElementTree as ET
from typing import Any, Callable, Dict, List, Optional

from udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import (
    DiscoveryEvents,
    FamilyDiscovery,
    RefDiscovery,
)

LOGGER = logging.getLogger(__name__)


@dataclasses.dataclass
class PortInfo:
    port_number: int
    protocol: str
    state: str
    service_name: Optional[str] = None


@dataclasses.dataclass
class HostInfo:
    ip: str
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

            ports = []
            ports_elem = host_elem.find("ports")
            if ports_elem is not None:
                for port_elem in ports_elem.findall("port"):
                    port_id = int(port_elem.get("portid"))
                    protocol = port_elem.get("protocol")
                    state_elem = port_elem.find("state")
                    state = state_elem.get("state") if state_elem is not None else "unknown"

                    service_elem = port_elem.find("service")
                    service_name = service_elem.get("name") if service_elem is not None else None

                    ports.append(
                        PortInfo(
                            port_number=port_id,
                            protocol=protocol,
                            state=state,
                            service_name=service_name,
                        )
                    )
            hosts.append(HostInfo(ip=ip, ports=ports))
    except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.error("Failed to parse nmap XML: %s", err)
    return hosts


class EtherFamilyProvider(FamilyProvider):
    """Pluggable Ethernet protocol discovery provider (Ping & Nmap)."""

    def __init__(self, ping_concurrency: int = 4) -> None:
        self.ping_concurrency = ping_concurrency
        self._cancelled = threading.Event()
        self._active_proc: Optional[subprocess.Popen] = None
        self._lock = threading.Lock()

    def start_scan(
        self,
        discovery_config: Any,
        publish_func: Callable[[str, DiscoveryEvents], None],
    ) -> None:
        """Executes ping or nmap scan based on depth configuration."""
        self._cancelled.clear()
        generation = getattr(discovery_config, "generation", None)
        depth = str(getattr(discovery_config, "depth", "ping")).lower()
        addrs = getattr(discovery_config, "addrs", None) or []

        LOGGER.info(
            "Starting Ether discovery scan (depth: %s, generation: %s, targets: %s)...",
            depth,
            generation,
            addrs,
        )

        if not addrs:
            LOGGER.warning("No target addresses provided for ether scan.")
            return

        if depth == "ping":
            self._run_ping_scan(addrs, generation, publish_func)
        elif depth in ("ports", "services", "details", "parts"):
            self._run_nmap_scan(addrs, depth, generation, publish_func)
        else:
            LOGGER.warning("Unrecognized ether scan depth: '%s'. Defaulting to ping.", depth)
            self._run_ping_scan(addrs, generation, publish_func)

    def stop_scan(self) -> None:
        """Stops any active ether scan subprocess immediately."""
        LOGGER.info("Stopping Ether discovery scan...")
        self._cancelled.set()
        with self._lock:
            if self._active_proc and self._active_proc.poll() is None:
                try:
                    self._active_proc.terminate()
                except Exception:
                    pass

    def _run_ping_scan(
        self,
        targets: List[str],
        generation: Any,
        publish_func: Callable[[str, DiscoveryEvents], None],
    ) -> None:
        with concurrent.futures.ThreadPoolExecutor(max_workers=self.ping_concurrency) as executor:
            futures = {
                executor.submit(self._ping_target, ip, generation, publish_func): ip
                for ip in targets
            }
            for future in concurrent.futures.as_completed(futures):
                if self._cancelled.is_set():
                    executor.shutdown(wait=False, cancel_futures=True)
                    break
                try:
                    future.result()
                except Exception as e:
                    LOGGER.debug("Ping worker exception for %s: %s", futures[future], e)

    def _ping_target(
        self,
        target_ip: str,
        generation: Any,
        publish_func: Callable[[str, DiscoveryEvents], None],
    ) -> bool:
        if self._cancelled.is_set():
            return False

        try:
            res = subprocess.run(
                ["/usr/bin/ping", "-c", "1", "-W", "2", target_ip],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                encoding="utf-8",
                check=True,
                timeout=5,
            )
            if res.returncode == 0:
                event = DiscoveryEvents(
                    generation=generation,
                    family="ether",
                    addr=target_ip,
                    families={"ipv4": FamilyDiscovery(addr=target_ip)},
                )
                publish_func(target_ip, event)
                return True
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, FileNotFoundError):
            return False
        return False

    def _run_nmap_scan(
        self,
        targets: List[str],
        depth: str,
        generation: Any,
        publish_func: Callable[[str, DiscoveryEvents], None],
    ) -> None:
        cmd = ["/usr/bin/nmap"]
        if depth in ("services", "parts"):
            cmd.extend(["--script", "banner", "-sV"])
        cmd.extend(["-p-", "-T3", "-oX", "-"])
        cmd.extend(targets)

        try:
            with self._lock:
                self._active_proc = subprocess.Popen(
                    cmd,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    encoding="utf-8",
                )

            stdout, stderr = self._active_proc.communicate()
            if self._cancelled.is_set():
                return

            if stdout:
                hosts = parse_nmap_xml(stdout)
                for host in hosts:
                    refs = {
                        f"{p.port_number}": RefDiscovery(
                            name=f"port_{p.port_number}",
                            description=f"{p.protocol} service {p.service_name or 'unknown'}",
                        )
                        for p in host.ports
                    }
                    event = DiscoveryEvents(
                        generation=generation,
                        family="ether",
                        addr=host.ip,
                        families={"ipv4": FamilyDiscovery(addr=host.ip)},
                        refs=refs if refs else None,
                    )
                    publish_func(host.ip, event)

        except FileNotFoundError:
            LOGGER.error("nmap binary not found at /usr/bin/nmap")
        except Exception as e:  # pylint: disable=broad-exception-caught
            LOGGER.error("Nmap scan failed: %s", e)

    def enumerate_refs(self, addr: str) -> Dict[str, RefDiscovery]:
        return {}
