"""BACnet Protocol Family Provider for UDMI Spotter."""

import concurrent.futures
import copy
import logging
import re
import threading
import time
from typing import Any, Callable, Dict, Iterable, Optional, Set, Tuple

try:
  import BAC0
  import BAC0.core.io.IOExceptions
except ImportError:
  BAC0 = None

from udmi.core.managers.providers.family_provider import FamilyProvider
from udmi.schema import (
    DiscoveryEvents,
    Entry,
    FamilyDiscovery,
    RefDiscovery,
    StateSystemHardware,
    System,
)

LOGGER = logging.getLogger(__name__)

BACNET_DEVICE_ID = 4194300
_IP_ADDRESS_REGEX = r"([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})"

if BAC0:
  BAC0.log_level("silence")
  for name in logging.getLogger().manager.loggerDict:
    if name.startswith("BAC0"):
      logging.getLogger(name).setLevel(logging.CRITICAL)


BACNET_ACRONYMS = {
    "analogInput": "AI",
    "analogOutput": "AO",
    "analogValue": "AV",
    "binaryInput": "BI",
    "binaryOutput": "BO",
    "binaryValue": "BV",
    "loop": "LP",
    "multiStateInput": "MSI",
    "multiStateOutput": "MSO",
    "multiStateValue": "MSV",
    "characterstringValue": "CSV",
}


def _future_wait_and_count_outstanding(
    futures: Iterable[concurrent.futures.Future], timeout: int = 1
) -> int:
  _, outstanding = concurrent.futures.wait(futures, timeout)
  return len(outstanding)


class BacnetFamilyProvider(FamilyProvider):
  """Pluggable BACnet protocol discovery provider for Spotter."""

  def __init__(
      self,
      bacnet_ip: Optional[str] = None,
      bacnet_port: Optional[int] = None,
      bacnet_device_id: int = BACNET_DEVICE_ID,
  ) -> None:
    self.bacnet_ip = bacnet_ip
    self.bacnet_port = bacnet_port
    self.bacnet_device_id = bacnet_device_id
    self._bacnet = None
    self._lock = threading.Lock()

    self._devices_published: Set[Tuple[str, Any]] = set()
    self._targeted_devices_found: Set[Tuple[str, Any]] = set()
    self._cancelled = threading.Event()
    self._scan_thread: Optional[threading.Thread] = None
    self._resolver_thread: Optional[threading.Thread] = None
    self._event_count = 0

  def _ensure_bacnet_client(self) -> Any:
    with self._lock:
      if self._bacnet is None:
        if BAC0 is None:
          raise RuntimeError("BAC0 library is not installed or available.")
        LOGGER.info(
            "Initializing BAC0 client (IP: %s, Port: %s, DeviceId: %d)...",
            self.bacnet_ip,
            self.bacnet_port,
            self.bacnet_device_id,
        )
        self._bacnet = BAC0.lite(
            ip=self.bacnet_ip,
            port=self.bacnet_port,
            deviceId=self.bacnet_device_id,
        )
      return self._bacnet

  def start_scan(
      self,
      discovery_config: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
  ) -> None:
    """Starts a discovery scan for the BACnet protocol family."""
    self._cancelled.clear()
    self._devices_published.clear()
    self._targeted_devices_found.clear()
    self._event_count = 0

    client = self._ensure_bacnet_client()
    generation = getattr(discovery_config, "generation", None)
    depth = getattr(discovery_config, "depth", "system")
    addrs = getattr(discovery_config, "addrs", None)
    scan_duration_sec = getattr(discovery_config, "scan_duration_sec", 5)

    LOGGER.info(
        "Starting BACnet discovery scan (generation: %s, depth: %s,"
        " addrs: %s)...",
        generation,
        depth,
        addrs,
    )

    # Emit start event (event_no: 0)
    publish_func(
        "self",
        DiscoveryEvents(
            generation=generation,
            family="bacnet",
            event_no=0,
        ),
    )

    if not addrs:
      client.discover(global_broadcast=True)
      device_producer = self._global_device_producer
    else:
      for addr in addrs:
        if not re.match(_IP_ADDRESS_REGEX, addr.split(":", maxsplit=1)[0]):
          raise RuntimeError(
              f"Unsupported BACnet target address format: {addr}"
          )

      self._resolver_thread = threading.Thread(
          target=self._resolve_targeted_ips,
          args=(copy.copy(addrs),),
          name="BACnet-Resolver",
          daemon=True,
      )
      self._resolver_thread.start()
      device_producer = self._targeted_device_producer

    self._scan_thread = threading.Thread(
        target=self._devices_consumer,
        args=(device_producer, discovery_config, publish_func),
        name="BACnet-Consumer",
        daemon=True,
    )
    self._scan_thread.start()

    wait_time = float(scan_duration_sec) if scan_duration_sec else 5.0
    self._cancelled.wait(timeout=wait_time)
    self.stop_scan()

    # Emit finish event (event_no: -(event_count + 1))
    publish_func(
        "self",
        DiscoveryEvents(
            generation=generation,
            family="bacnet",
            event_no=-(self._event_count + 1),
        ),
    )

  def stop_scan(self) -> None:
    """Stops any active BACnet scan immediately."""
    LOGGER.info("Stopping BACnet discovery scan...")
    self._cancelled.set()
    if self._resolver_thread and self._resolver_thread.is_alive():
      self._resolver_thread.join(timeout=1.0)
    if self._scan_thread and self._scan_thread.is_alive():
      self._scan_thread.join(timeout=1.0)

  def _resolve_targeted_ips(self, target_ips: list[str]) -> None:
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
      futures = [
          executor.submit(self._resolve_single_ip, ip) for ip in target_ips
      ]
      while _future_wait_and_count_outstanding(futures, timeout=1) > 0:
        if self._cancelled.is_set():
          executor.shutdown(wait=True, cancel_futures=True)
          break

  def _resolve_single_ip(self, ip_address: str) -> None:
    client = self._ensure_bacnet_client()
    try:
      _, dev_id = client.read(
          f"{ip_address} device 4194303 objectIdentifier", None, 0, None, 3
      )
      if dev_id:
        LOGGER.debug("Resolved BACnet device %s at %s", dev_id, ip_address)
        self._targeted_devices_found.add((ip_address, dev_id))
    except (  # pylint: disable=broad-exception-caught
        BAC0.core.io.IOExceptions.NoResponseFromController,
        Exception,
    ):
      pass

  def _global_device_producer(self) -> Set[Tuple[str, Any]]:
    client = self._ensure_bacnet_client()
    if client.discoveredDevices is not None:
      return set(client.discoveredDevices.keys()) - self._devices_published
    return set()

  def _targeted_device_producer(self) -> Set[Tuple[str, Any]]:
    return self._targeted_devices_found - self._devices_published

  def _devices_consumer(
      self,
      producer: Callable[[], Set[Tuple[str, Any]]],
      discovery_config: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
  ) -> None:
    while not self._cancelled.is_set():
      try:
        new_devices = producer() - self._devices_published
        if not new_devices:
          time.sleep(0.5)
          continue

        for device in new_devices:
          if self._cancelled.is_set():
            return
          address, device_id = device
          self._event_count += 1
          event = self.discover_device(address, device_id, discovery_config)
          event.event_no = self._event_count
          publish_func(str(device_id), event)
          self._devices_published.add(device)

      except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.error(
            "Error during BACnet device consumption: %s", err, exc_info=True
        )
        return

  def discover_device(
      self, device_address: str, device_id: Any, discovery_config: Any
  ) -> DiscoveryEvents:
    """Discovers and inspects a single BACnet device."""
    client = self._ensure_bacnet_client()
    generation = getattr(discovery_config, "generation", None)
    depth = getattr(discovery_config, "depth", "system")
    depth_val = getattr(depth, "value", depth)

    raw_addr = str(device_address)
    ip_part = (
        raw_addr.split(":", maxsplit=1)[0] if ":" in raw_addr else raw_addr
    )
    port_part = None
    if ":" in raw_addr:
      try:
        port_part = int(raw_addr.split(":", maxsplit=1)[1])
      except ValueError:
        port_part = None

    families_map = {"ipv4": FamilyDiscovery(addr=ip_part)}
    if port_part and port_part != 47808:
      families_map["bacnet"] = FamilyDiscovery(addr=raw_addr, port=port_part)

    event = DiscoveryEvents(
        generation=generation,
        family="bacnet",
        addr=str(device_id),
        families=families_map,
    )

    if depth_val in ("system", "refs", "details", "parts"):
      try:
        (
            object_name,
            vendor_name,
            firmware_version,
            model_name,
            serial_number,
            description,
            location,
            application_version,
        ) = client.readMultiple(
            f"{device_address} device {device_id}"
            " objectName"
            " vendorName"
            " firmwareRevision"
            " modelName"
            " serialNumber"
            " description"
            " location"
            " applicationSoftwareVersion"
        )

        ancillary_dict = {}
        if description:
          ancillary_dict["description"] = str(description)
        if location:
          ancillary_dict["location"] = str(location)
        if application_version:
          ancillary_dict["application_version"] = str(application_version)
        if firmware_version:
          ancillary_dict["firmware"] = str(firmware_version)
        if object_name:
          ancillary_dict["name"] = str(object_name)

        event.system = System(
            serial_no=str(serial_number) if serial_number else None,
            hardware=StateSystemHardware(
                make=str(vendor_name) if vendor_name else "Unknown",
                model=str(model_name) if model_name else "Unknown",
            ),
            ancillary=ancillary_dict if ancillary_dict else None,
        )
      except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.warning(
            "Error reading BACnet device properties (%s/%s): %s",
            device_address,
            device_id,
            err,
        )
        event.status = Entry(
            category="discovery.error",
            level=500,
            message=f"Property read failed: {err}",
        )
        return event

    if depth_val in ("refs", "details", "parts"):
      refs = self.enumerate_refs(f"{device_address} {device_id}")
      if refs:
        event.refs = refs

    return event

  def enumerate_refs(self, addr: str) -> Dict[str, RefDiscovery]:
    """Enumerates point references for a target BACnet device address."""
    client = self._ensure_bacnet_client()
    refs: Dict[str, RefDiscovery] = {}
    parts = addr.split()
    if len(parts) < 2:
      return refs

    device_address, device_id = parts[0], parts[1]
    try:
      dev = BAC0.device(device_address, int(device_id), client, poll=0)
      for point in dev.points:
        ref = RefDiscovery(
            name=point.properties.name,
            description=point.properties.description,
            type=point.properties.type,
        )
        if isinstance(point.properties.units_state, list):
          ref.possible_values = point.properties.units_state
        elif isinstance(point.properties.units_state, str):
          ref.units = point.properties.units_state

        point_acronym = BACNET_ACRONYMS.get(
            point.properties.type, point.properties.type
        )
        point_id = f"{point_acronym}:{point.properties.address}"
        refs[point_id] = ref
    except Exception as err:  # pylint: disable=broad-exception-caught
      LOGGER.warning(
          "Error enumerating points for BACnet device (%s): %s", addr, err
      )

    return refs

  def close(self) -> None:
    """Closes the active BAC0 client connection."""
    with self._lock:
      if self._bacnet:
        try:
          self._bacnet.disconnect()
        except Exception:  # pylint: disable=broad-exception-caught
          pass
        self._bacnet = None

  def __del__(self) -> None:
    self.close()

