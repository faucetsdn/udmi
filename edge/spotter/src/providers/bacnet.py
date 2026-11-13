"""BACnet Protocol Family Provider for UDMI Spotter."""

import concurrent.futures
from datetime import datetime, timezone
import ipaddress
import logging
import os
import re
import threading
import time
from typing import Any, Callable, Dict, Iterable, List, Optional, Set, Tuple

try:
  import BAC0
  from BAC0.core.devices.Device import DeviceDisconnected
  from BAC0.core.io.IOExceptions import (
      NoResponseFromController,
      SegmentationNotSupported,
  )
except ImportError:
  BAC0 = None
  DeviceDisconnected = ()
  NoResponseFromController = RuntimeError
  SegmentationNotSupported = RuntimeError

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
MAX_BACNET_DEVICE_ID = 4194303
MAX_BACNET_NETWORK = 65534
DEFAULT_BACNET_PORT = 47808
_POSITIVE_INT_REGEX = re.compile(r"^[1-9][0-9]*$")

if BAC0:
  BAC0.log_level("silence")
  for _logger_name in logging.getLogger().manager.loggerDict:
    if _logger_name.startswith("BAC0"):
      logging.getLogger(_logger_name).setLevel(logging.CRITICAL)


BACNET_ACRONYMS: Dict[str, str] = {
    "analogInput": "AI",
    "analog-input": "AI",
    "analogOutput": "AO",
    "analog-output": "AO",
    "analogValue": "AV",
    "analog-value": "AV",
    "binaryInput": "BI",
    "binary-input": "BI",
    "binaryOutput": "BO",
    "binary-output": "BO",
    "binaryValue": "BV",
    "binary-value": "BV",
    "loop": "LP",
    "multiStateInput": "MSI",
    "multi-state-input": "MSI",
    "multiStateOutput": "MSO",
    "multi-state-output": "MSO",
    "multiStateValue": "MSV",
    "multi-state-value": "MSV",
    "characterstringValue": "CSV",
    "characterstring-value": "CSV",
    "datetimeValue": "DTV",
    "datetime-value": "DTV",
    "largeAnalogValue": "LAV",
    "large-analog-value": "LAV",
    "integerValue": "IV",
    "integer-value": "IV",
    "positiveIntegerValue": "PIV",
    "positive-integer-value": "PIV",
}

BACNET_STANDARD_TYPES: Dict[str, str] = {
    "analogInput": "analog-input",
    "analogOutput": "analog-output",
    "analogValue": "analog-value",
    "binaryInput": "binary-input",
    "binaryOutput": "binary-output",
    "binaryValue": "binary-value",
    "loop": "loop",
    "multiStateInput": "multi-state-input",
    "multiStateOutput": "multi-state-output",
    "multiStateValue": "multi-state-value",
    "characterstringValue": "characterstring-value",
    "datetimeValue": "datetime-value",
    "largeAnalogValue": "large-analog-value",
    "integerValue": "integer-value",
    "positiveIntegerValue": "positive-integer-value",
}

OUTPUT_OBJECT_ACRONYMS = frozenset({
    "AO",
    "BO",
    "MSO",
})

VALUE_OBJECT_ACRONYMS = frozenset({
    "AV",
    "BV",
    "MSV",
    "CSV",
    "DTV",
    "LAV",
    "IV",
    "PIV",
})

WRITABLE_OBJECT_ACRONYMS = OUTPUT_OBJECT_ACRONYMS | VALUE_OBJECT_ACRONYMS

VALID_BACNET_DEPTHS = frozenset({
    "buckets",
    "entries",
    "system",
    "details",
    "refs",
    "parts",
    "trace",
})

BACNET_ANCILLARY_PROP_MAP: Dict[str, str] = {
    "statusFlags": "status-flags",
    "status-flags": "status-flags",
    "status_flags": "status-flags",
    "eventState": "event-state",
    "event-state": "event-state",
    "event_state": "event-state",
    "outOfService": "out-of-service",
    "out-of-service": "out-of-service",
    "out_of_service": "out-of-service",
    "reliability": "reliability",
    "covIncrement": "cov-increment",
    "cov-increment": "cov-increment",
    "cov_increment": "cov-increment",
    "minPresValue": "min-pres-value",
    "min-pres-value": "min-pres-value",
    "min_pres_value": "min-pres-value",
    "maxPresValue": "max-pres-value",
    "max-pres-value": "max-pres-value",
    "max_pres_value": "max-pres-value",
    "resolution": "resolution",
}

DEFAULT_UNCHANGED_THRESHOLD_SEC = 300


def _now_rfc3339() -> str:
  """Returns the current UTC time formatted as RFC 3339 string."""
  return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _camel_to_kebab(name: str) -> str:
  """Converts a camelCase BACnet identifier into standard hyphenated form."""
  if not name:
    return name
  return re.sub(r"([a-z0-9])([A-Z])", r"\1-\2", name).lower()


def _get_mac_for_ip(ip: str, arp_file: str = "/proc/net/arp") -> Optional[str]:
  """Resolves Layer-2 MAC address for a local IPv4 address from ARP table."""
  try:
    if os.path.exists(arp_file):
      with open(arp_file, "r", encoding="utf-8") as f:
        for line in f:
          parts = line.split()
          if len(parts) >= 4 and parts[0] == ip:
            mac = parts[3].upper()
            if mac != "00:00:00:00:00:00":
              return mac
  except OSError as err:
    LOGGER.debug("Failed to read ARP cache %s: %s", arp_file, err)
  return None


def _parse_bacnet_networks(
    networks: Optional[Iterable[Any]],
) -> List[int]:
  """Validates and parses BACnet network numbers ('1'..'65534')."""
  if not networks:
    return []
  parsed: List[int] = []
  for net in networks:
    net_str = str(net).strip()
    if not _POSITIVE_INT_REGEX.match(net_str):
      raise ValueError(
          f"Invalid BACnet network number {net!r}: must be 1..{MAX_BACNET_NETWORK} with no leading zeroes"
      )
    net_val = int(net_str)
    if net_val < 1 or net_val > MAX_BACNET_NETWORK:
      raise ValueError(
          f"BACnet network number {net_val} out of range (1..{MAX_BACNET_NETWORK})"
      )
    if net_val not in parsed:
      parsed.append(net_val)
  return parsed


def _parse_bacnet_addrs(
    addrs: Optional[Iterable[Any]],
) -> List[Tuple[int, int]]:
  """Validates and parses BACnet Device Object Instance numbers or ranges.

  Per docs/specs/discovery_bacnet.md Section 2, each element in `addrs` must be:
  - An exact Device Object Instance number ('<instance>', 1..4194303), or
  - An inclusive hyphen-separated range ('<low>-<high>', 1 <= low <= high <= 4194303)
  with no leading zeroes.
  """
  if not addrs:
    return []
  ranges: List[Tuple[int, int]] = []
  for addr in addrs:
    addr_str = str(addr).strip()
    if "-" in addr_str:
      parts = addr_str.split("-", 1)
      if len(parts) != 2 or not (
          _POSITIVE_INT_REGEX.match(parts[0])
          and _POSITIVE_INT_REGEX.match(parts[1])
      ):
        raise ValueError(
            f"Invalid BACnet target address range {addr!r}: expected '<low>-<high>' (1..{MAX_BACNET_DEVICE_ID})"
        )
      low, high = int(parts[0]), int(parts[1])
      if low < 1 or high > MAX_BACNET_DEVICE_ID or low > high:
        raise ValueError(
            f"Invalid BACnet target address range {addr!r}: must satisfy 1 <= low <= high <= {MAX_BACNET_DEVICE_ID}"
        )
      ranges.append((low, high))
    else:
      if not _POSITIVE_INT_REGEX.match(addr_str):
        raise ValueError(
            f"Invalid BACnet target device instance {addr!r}: must be 1..{MAX_BACNET_DEVICE_ID} with no leading zeroes"
        )
      inst = int(addr_str)
      if inst < 1 or inst > MAX_BACNET_DEVICE_ID:
        raise ValueError(
            f"BACnet device instance {inst} out of range (1..{MAX_BACNET_DEVICE_ID})"
        )
      ranges.append((inst, inst))
  return ranges


def _parse_bacnet_device_address(
    device_address: Any,
    default_port: int = DEFAULT_BACNET_PORT,
    arp_file: str = "/proc/net/arp",
) -> Tuple[Optional[str], Dict[str, FamilyDiscovery]]:
  """Parses BAC0 device address into (network, families_map).

  Distinguishes BACnet/IP ('<ipv4>' or '<ipv4>:<port>') from routed BACnet
  MS/TP or subnetwork addresses ('<net>:<mac>', e.g. '6100:10').
  """
  raw_addr = str(device_address).strip()
  if not raw_addr:
    return None, {}

  host_part = raw_addr.split(":", 1)[0] if ":" in raw_addr else raw_addr
  suffix = raw_addr.split(":", 1)[1] if ":" in raw_addr else None

  # Case 1: BACnet/IP address (<ipv4> or <ipv4>:<port>)
  try:
    ipaddress.IPv4Address(host_part)
    port = default_port
    if suffix is not None:
      try:
        port = int(suffix)
      except ValueError:
        port = default_port
    families_map: Dict[str, FamilyDiscovery] = {
        "ipv4": FamilyDiscovery(addr=host_part, port=port)
    }
    mac = _get_mac_for_ip(host_part, arp_file=arp_file)
    if mac:
      families_map["ethmac"] = FamilyDiscovery(addr=mac)
    return None, families_map
  except ValueError:
    pass

  # Case 2: Routed BACnet address (<network>:<mac>, e.g. '6100:10')
  if suffix is not None:
    network = None
    if _POSITIVE_INT_REGEX.match(host_part):
      net_val = int(host_part)
      if 1 <= net_val <= MAX_BACNET_NETWORK:
        network = host_part
    families_map = {"mstp": FamilyDiscovery(addr=suffix)}
    return network, families_map

  return None, {}


class BacnetFamilyProvider(FamilyProvider):
  """Pluggable BACnet protocol discovery provider for Spotter."""

  def __init__(
      self,
      bacnet_ip: Optional[str] = None,
      bacnet_port: Optional[int] = None,
      bacnet_device_id: int = BACNET_DEVICE_ID,
      unchanged_threshold_sec: int = DEFAULT_UNCHANGED_THRESHOLD_SEC,
  ) -> None:
    """Initializes BacnetFamilyProvider.

    Args:
        bacnet_ip: Local IP address to bind BAC0 client.
        bacnet_port: Port to bind BAC0 client.
        bacnet_device_id: BACnet device object instance ID for local client.
        unchanged_threshold_sec: Inactivity duration before terminating scan.
    """
    self.bacnet_ip = bacnet_ip
    self.bacnet_port = bacnet_port
    self.bacnet_device_id = bacnet_device_id
    self.unchanged_threshold_sec = unchanged_threshold_sec
    self._bacnet = None
    self._lock = threading.Lock()

    self._devices_published: Set[Tuple[str, Any]] = set()
    self._cancelled = threading.Event()
    self._scan_thread: Optional[threading.Thread] = None
    self._event_count = 0
    self._active_instance_ranges: List[Tuple[int, int]] = []
    self._active_networks: List[int] = []

  def _ensure_bacnet_client(self) -> Any:
    """Returns cached or new BAC0 client instance in a thread-safe manner."""
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

  @staticmethod
  def _resolve_depth(discovery_config: Any) -> str:
    """Resolves and validates the canonical BACnet scan depth."""
    raw_depth = getattr(discovery_config, "depth", None)
    depth_val = getattr(raw_depth, "value", raw_depth)
    addrs = getattr(discovery_config, "addrs", None)
    if depth_val is None:
      return "details" if addrs else "entries"
    depth_str = str(depth_val).strip().lower()
    if depth_str not in VALID_BACNET_DEPTHS:
      raise ValueError(
          f"Unsupported BACnet discovery depth {depth_val!r}: expected one of {sorted(VALID_BACNET_DEPTHS)}"
      )
    return depth_str

  def start_scan(
      self,
      discovery_config: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
  ) -> None:
    """Starts a discovery scan for the BACnet protocol family."""
    self._cancelled.clear()
    self._devices_published.clear()
    self._event_count = 0

    generation = getattr(discovery_config, "generation", None)
    depth_val = self._resolve_depth(discovery_config)
    addrs = getattr(discovery_config, "addrs", None)
    networks = getattr(discovery_config, "networks", None)
    scan_duration_sec = getattr(discovery_config, "scan_duration_sec", None)

    self._active_networks = _parse_bacnet_networks(networks)
    self._active_instance_ranges = _parse_bacnet_addrs(addrs)

    client = self._ensure_bacnet_client()

    LOGGER.info(
        "Starting BACnet discovery scan (generation: %s, depth: %s,"
        " networks: %s, addrs: %s)...",
        generation,
        depth_val,
        self._active_networks,
        addrs,
    )

    scan_start_mono = time.monotonic()

    # Emit start event (event_no: 0)
    publish_func(
        "self",
        DiscoveryEvents(
            generation=generation,
            family="bacnet",
            event_no=0,
        ),
    )

    if depth_val == "buckets":
      self._discover_buckets(client, generation, publish_func)
      self.stop_scan()
      publish_func(
          "self",
          DiscoveryEvents(
              generation=generation,
              family="bacnet",
              event_no=-(self._event_count + 1),
          ),
      )
      return

    # Issue Who-Is discovery according to network and instance range filters
    if not self._active_instance_ranges:
      if self._active_networks:
        client.discover(
            networks=self._active_networks,
            global_broadcast=True,
            reset=True,
        )
      else:
        client.discover(global_broadcast=True, reset=True)
    else:
      target_nets: Any = (
          self._active_networks if self._active_networks else "known"
      )
      min_low = min(low for low, _ in self._active_instance_ranges)
      max_high = max(high for _, high in self._active_instance_ranges)
      client.discover(
          networks=target_nets,
          limits=(min_low, max_high),
          global_broadcast=True,
          reset=True,
      )

    self._scan_thread = threading.Thread(
        target=self._devices_consumer,
        args=(self._global_device_producer, discovery_config, publish_func),
        name="BACnet-Consumer",
        daemon=True,
    )
    self._scan_thread.start()

    if scan_duration_sec and float(scan_duration_sec) > 0:
      elapsed = time.monotonic() - scan_start_mono
      remaining = max(0.5, float(scan_duration_sec) - elapsed)
      if self._scan_thread:
        self._scan_thread.join(timeout=remaining)
    else:
      if self._scan_thread:
        self._scan_thread.join()

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

  def _discover_buckets(
      self,
      client: Any,
      generation: Optional[str],
      publish_func: Callable[[str, DiscoveryEvents], None],
  ) -> None:
    """Discovers available BACnet network buckets and correlated UDP ports."""
    for probe_fn in ("what_is_network_number", "whois_router_to_network"):
      fn = getattr(client, probe_fn, None)
      if callable(fn):
        try:
          fn()
        except Exception as err:  # pylint: disable=broad-exception-caught
          LOGGER.debug("BACnet bucket probe %s failed: %s", probe_fn, err)

    discovered_nets: List[int] = []
    known = getattr(client, "known_network_numbers", None)
    if isinstance(known, (list, set, tuple)):
      for net in known:
        try:
          net_int = int(net)
          if 1 <= net_int <= MAX_BACNET_NETWORK and net_int not in discovered_nets:
            discovered_nets.append(net_int)
        except (ValueError, TypeError):
          continue

    if self._active_networks:
      if discovered_nets:
        discovered_nets = [
            n for n in discovered_nets if n in self._active_networks
        ]
      else:
        discovered_nets = list(self._active_networks)

    default_port = int(self.bacnet_port or DEFAULT_BACNET_PORT)
    for net_num in sorted(discovered_nets):
      if self._cancelled.is_set():
        break
      self._event_count += 1
      event = DiscoveryEvents(
          generation=generation,
          family="bacnet",
          network=str(net_num),
          event_no=self._event_count,
          families={"ipv4": FamilyDiscovery(port=default_port)},
      )
      publish_func(f"network-{net_num}", event)

  def stop_scan(self) -> None:
    """Stops any active BACnet scan immediately."""
    LOGGER.info("Stopping BACnet discovery scan...")
    self._cancelled.set()
    if self._scan_thread and self._scan_thread.is_alive():
      self._scan_thread.join(timeout=1.0)

  def _matches_active_filters(self, address: Any, device_id: Any) -> bool:
    """Checks if a discovered BACnet device matches configured scope filters."""
    try:
      dev_id_int = int(device_id)
    except (ValueError, TypeError):
      return False

    # Never report Spotter's own BACnet client instance ID as a discovered peer
    if dev_id_int == int(self.bacnet_device_id):
      return False

    if dev_id_int < 1 or dev_id_int > MAX_BACNET_DEVICE_ID:
      return False

    if self._active_instance_ranges:
      if not any(
          low <= dev_id_int <= high
          for low, high in self._active_instance_ranges
      ):
        return False

    if self._active_networks:
      net_str, _ = _parse_bacnet_device_address(address)
      if net_str is not None and int(net_str) not in self._active_networks:
        return False

    return True

  def _global_device_producer(self) -> Set[Tuple[str, Any]]:
    """Returns newly discovered devices matching configured scope filters."""
    client = self._ensure_bacnet_client()
    if client.discoveredDevices is not None:
      candidates = set(client.discoveredDevices.keys()) - self._devices_published
      return {
          (str(addr), dev_id)
          for addr, dev_id in candidates
          if self._matches_active_filters(addr, dev_id)
      }
    return set()

  def _devices_consumer(
      self,
      producer: Callable[[], Set[Tuple[str, Any]]],
      discovery_config: Any,
      publish_func: Callable[[str, DiscoveryEvents], None],
  ) -> None:
    """Consumes new devices, polls properties, and publishes events."""
    unchanged_seconds = 0
    while not self._cancelled.is_set():
      try:
        new_devices = producer() - self._devices_published
        if not new_devices:
          unchanged_seconds += 1
          if unchanged_seconds >= self.unchanged_threshold_sec:
            LOGGER.info(
                "No new BACnet devices found for %d seconds. Scan complete.",
                self.unchanged_threshold_sec,
            )
            return
          self._cancelled.wait(timeout=1.0)
          continue

        unchanged_seconds = 0
        for device in sorted(new_devices, key=lambda d: int(d[1])):
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

      if self._cancelled.is_set():
        return
      self._cancelled.wait(timeout=1.0)

  def _read_device_properties(
      self, client: Any, device_address: str, device_id: Any
  ) -> List[Any]:
    """Reads Device Object properties via ReadPropertyMultiple with RP fallback."""
    prop_names = [
        "objectName",
        "vendorName",
        "firmwareRevision",
        "modelName",
        "serialNumber",
        "description",
        "location",
        "applicationSoftwareVersion",
        "vendorIdentifier",
        "protocolVersion",
        "protocolRevision",
    ]
    try:
      props = client.readMultiple(
          f"{device_address} device {device_id} " + " ".join(prop_names)
      )
      if not isinstance(props, (list, tuple)):
        props = [props]
      padded = list(props) + [None] * max(0, len(prop_names) - len(props))
      return [None if isinstance(x, Exception) else x for x in padded[:11]]
    except Exception as err:  # pylint: disable=broad-exception-caught
      is_seg_err = isinstance(err, SegmentationNotSupported) or (
          "segmentation" in str(err).lower()
      )
      if not is_seg_err:
        raise
      LOGGER.info(
          "ReadPropertyMultiple unsupported/segmented on %s/%s; falling back"
          " to individual ReadProperty requests.",
          device_address,
          device_id,
      )
      results: List[Any] = []
      any_success = False
      last_err: Optional[Exception] = None
      for prop in prop_names:
        try:
          val = client.read(f"{device_address} device {device_id} {prop}")
          results.append(val)
          any_success = True
        except Exception as rp_err:  # pylint: disable=broad-exception-caught
          last_err = rp_err
          results.append(None)
      if not any_success and last_err is not None:
        raise last_err
      return results

  def discover_device(
      self, device_address: str, device_id: Any, discovery_config: Any
  ) -> DiscoveryEvents:
    """Discovers and inspects a single BACnet device across configured depth."""
    client = self._ensure_bacnet_client()
    generation = getattr(discovery_config, "generation", None)
    depth_val = self._resolve_depth(discovery_config)

    default_port = int(self.bacnet_port or DEFAULT_BACNET_PORT)
    network, families_map = _parse_bacnet_device_address(
        device_address, default_port=default_port
    )
    if network is None and len(self._active_networks) == 1:
      network = str(self._active_networks[0])

    event = DiscoveryEvents(
        generation=generation,
        family="bacnet",
        addr=str(device_id),
        network=network,
        families=families_map if families_map else None,
    )

    if depth_val == "entries":
      return event

    if depth_val in ("system", "details", "refs", "parts"):
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
            vendor_identifier,
            protocol_version,
            protocol_revision,
        ) = self._read_device_properties(client, device_address, device_id)

        ancillary_dict: Dict[str, Any] = {}
        if firmware_version is not None and str(firmware_version) != "":
          ancillary_dict["firmware-revision"] = str(firmware_version)
        if application_version is not None and str(application_version) != "":
          ancillary_dict["application-software-version"] = str(
              application_version
          )
        if location is not None and str(location) != "":
          ancillary_dict["location"] = str(location)
        if vendor_identifier is not None and str(vendor_identifier) != "":
          ancillary_dict["vendor-identifier"] = (
              vendor_identifier
              if isinstance(vendor_identifier, (int, float, bool))
              else str(vendor_identifier)
          )
        if protocol_version is not None and str(protocol_version) != "":
          ancillary_dict["protocol-version"] = (
              protocol_version
              if isinstance(protocol_version, (int, float, bool))
              else str(protocol_version)
          )
        if protocol_revision is not None and str(protocol_revision) != "":
          ancillary_dict["protocol-revision"] = (
              protocol_revision
              if isinstance(protocol_revision, (int, float, bool))
              else str(protocol_revision)
          )

        make_str = (
            str(vendor_name)
            if vendor_name is not None and str(vendor_name) != ""
            else None
        )
        model_str = (
            str(model_name)
            if model_name is not None and str(model_name) != ""
            else None
        )
        hw_block = (
            StateSystemHardware(make=make_str, model=model_str)
            if (make_str is not None or model_str is not None)
            else None
        )

        event.system = System(
            name=(
                str(object_name)
                if object_name is not None and str(object_name) != ""
                else None
            ),
            description=(
                str(description)
                if description is not None and str(description) != ""
                else None
            ),
            serial_no=(
                str(serial_number)
                if serial_number is not None and str(serial_number) != ""
                else None
            ),
            hardware=hw_block,
            ancillary=ancillary_dict if ancillary_dict else None,
        )
      except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.warning(
            "Error reading BACnet device properties (%s/%s): %s",
            device_address,
            device_id,
            err,
        )
        err_msg = str(err).strip() or type(err).__name__
        event.status = Entry(
            category="discovery.device.enumerate",
            level=500,
            message=f"Property read failed: {err_msg}",
            timestamp=_now_rfc3339(),
        )
        return event

    if depth_val in ("details", "refs", "parts"):
      try:
        refs = self.enumerate_refs(f"{device_address} {device_id}")
        if refs:
          event.refs = refs
      except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.warning(
            "Error enumerating points for BACnet device (%s/%s): %s",
            device_address,
            device_id,
            err,
        )
        err_msg = str(err).strip() or type(err).__name__
        event.status = Entry(
            category="discovery.device.enumerate",
            level=500,
            message=f"Point enumeration failed: {err_msg}",
            timestamp=_now_rfc3339(),
        )
        return event

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
      if isinstance(dev, DeviceDisconnected) or not dev.properties.objects_list:
        raise NoResponseFromController(
            f"Failed to connect or read objectList for {device_address}/{device_id}"
        )
      for point in dev.points:
        raw_type = str(point.properties.type or "")
        point_acronym = BACNET_ACRONYMS.get(raw_type, raw_type.upper())
        std_type = BACNET_STANDARD_TYPES.get(raw_type) or _camel_to_kebab(
            raw_type
        )

        present_val = getattr(point, "lastValue", None)
        ancillary: Dict[str, Any] = {}
        if present_val is not None:
          if isinstance(present_val, (int, float, bool, str)):
            ancillary["present-value"] = present_val
          else:
            ancillary["present-value"] = str(present_val)

        bacnet_props = getattr(point.properties, "bacnet_properties", None)
        if isinstance(bacnet_props, dict):
          for raw_k, canonical_k in BACNET_ANCILLARY_PROP_MAP.items():
            if raw_k in bacnet_props and bacnet_props[raw_k] is not None:
              val = bacnet_props[raw_k]
              ancillary[canonical_k] = (
                  val
                  if isinstance(val, (int, float, bool, str, list, dict))
                  else str(val)
              )

        explicit_writable = getattr(point.properties, "writable", None)
        if isinstance(explicit_writable, bool):
          writable = explicit_writable
        elif point_acronym in OUTPUT_OBJECT_ACRONYMS:
          writable = True
        elif point_acronym in VALUE_OBJECT_ACRONYMS:
          if isinstance(bacnet_props, dict) and any(
              k in bacnet_props
              for k in ("priorityArray", "priority-array", "priority_array")
          ):
            pa_val = (
                bacnet_props.get("priorityArray")
                if "priorityArray" in bacnet_props
                else (
                    bacnet_props.get("priority-array")
                    if "priority-array" in bacnet_props
                    else bacnet_props.get("priority_array")
                )
            )
            writable = pa_val is not None
          else:
            writable = True
        else:
          writable = False

        desc = point.properties.description
        ref = RefDiscovery(
            name=str(point.properties.name) if point.properties.name else None,
            description=(
                str(desc) if desc is not None and str(desc) != "" else None
            ),
            type=std_type if std_type else None,
            writable=writable,
            ancillary=ancillary if ancillary else None,
        )

        units_state = point.properties.units_state
        if isinstance(units_state, (list, tuple)):
          possible_vals = [
              str(v) for v in units_state if v is not None and str(v) != ""
          ]
          if possible_vals:
            ref.possible_values = possible_vals
          elif point_acronym in ("BI", "BO", "BV"):
            ref.possible_values = ["inactive", "active"]
        elif isinstance(units_state, str) and units_state:
          ref.units = units_state
        elif point_acronym in ("BI", "BO", "BV"):
          ref.possible_values = ["inactive", "active"]

        point_id = f"{point_acronym}/{point.properties.address}"
        refs[point_id] = ref
    except Exception as err:
      LOGGER.warning(
          "Error enumerating points for BACnet device (%s): %s", addr, err
      )
      raise

    return refs

  def close(self) -> None:
    """Closes the active BAC0 client connection."""
    with self._lock:
      if self._bacnet:
        try:
          self._bacnet.disconnect()
        except Exception as err:  # pylint: disable=broad-exception-caught
          LOGGER.debug("Error disconnecting BAC0 client: %s", err)
        self._bacnet = None

  def __del__(self) -> None:
    self.close()
