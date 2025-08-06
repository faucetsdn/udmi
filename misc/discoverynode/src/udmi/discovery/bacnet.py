
import ipaddress
import logging
import threading
import time
from typing import Any, Callable
import BAC0
import BAC0.core.io.IOExceptions
import udmi.discovery.discovery as discovery
import udmi.schema.discovery_event
from udmi.schema.discovery_event import DiscoveryEvent
from udmi.schema.discovery_event import DiscoveryPoint
import udmi.schema.state
import ipaddress
import enum
import copy

BAC0.log_level("silence")
for name in logging.getLogger().manager.loggerDict:
  if name.startswith("BAC0"):
    logging.getLogger(name).setLevel(logging.CRITICAL)

class BacnetObjectAcronyms(enum.StrEnum):
  """ Mapping of object names to accepted aronyms"""
  analogInput = "AI"
  analogOutput = "AO"
  analogValue = "AV"
  binaryInput = "BI"
  binaryOutput = "BO"
  binaryValue = "BV"
  loop = "LP"
  multiStateInput = "MSI"
  multiStateOutput = "MSO"
  multiStateValue = "MSV"
  characterstringValue = "CSV"

class CowardlyQuit(Exception):
  pass


class GlobalBacnetDiscovery(discovery.DiscoveryController):
  """Bacnet discovery."""

  family = "bacnet"

  def __init__(
      self,
      state: udmi.schema.state.LocalnetFamily,
      publisher: Callable[[DiscoveryEvent], None],
      *,
      bacnet_ip: str = None,
      bacnet_port: int = None,
  ):
    self.devices_published = set()
    self.targetted_devices_found = set()
    self.cancelled = None
    self.result_producer_thread = None
    self.bacnet_scan_executor_thread = None
   
    self.bacnet = BAC0.lite(ip=bacnet_ip, port=bacnet_port)
    
    super().__init__(state, publisher)

  def validate_ip_set(ip_addresses: list[str]) -> bool:
    """
    Validates that all items in a set are valid IP addresses with optional CIDR.

    Args:
      ip_strings: A list of strings to validate.

    Returns:
      True if all strings are valid IP addresses, False otherwise.
    """
    for maybe_ip in ip_addresses:
      try:
        ipaddress.ip_interface(maybe_ip)
      except ValueError:
        return False
    return True
  

  def start_discovery(self):
    self.devices_published.clear()
    self.targetted_devices_found.clear()

    self.cancelled = False

    if not self.config.addrs:
      self.bacnet.discover(global_broadcast=True)
      device_list_function = self.bacnet_discovery_producer
    else:
      # addrs could in thory be a device IP + address
      # for now, it's assumed to be an IP address which will be sent
      # a who/is packet. In the event of the former, it should preopulate
      # a structure similar to self.bacnet.discoveredDevices dict.
      target_ips_to_scan = []
      for addr in self.config.addrs:
        if ':' in addr:
          self.targetted_devices_found.add(tuple(addr.split(':')))
        else:
          try:
            ipaddress.ip_interface(addr)
            target_ips_to_scan.append(addr)
          except ValueError:
            raise(
              RuntimeError("bad IP addess")
            )
        # TODO: does addrs need to be copied? check how self.config is mutated.
      self.bacnet_scan_executor_thread = threading.Thread(
          target=self.serial_bacnet_scan_executor, args=[target_ips_to_scan], daemon=True
      )
      self.bacnet_scan_executor_thread.start()
      device_list_function = self.targetted_scan_producer

    self.result_producer_thread = threading.Thread(
        target=self.devices_consumer, args=[device_list_function], daemon=True
    )
    self.result_producer_thread.start()

      # start a serial thread
  def serial_bacnet_scan_executor(self, target_addrs: list[str], wait_ms: int = 11) -> None:
    """Sends a Who/Is request to a given list of IP addresses.

    Args:
      target_addrs: list of IP addresses
      wait: seconds to wait between requests
    """
    logging.info("thread %s", threading.get_ident())
    for addr in target_addrs:
      time.sleep(wait_ms * 0.001)
      if self.cancelled:
        return
      results = self.bacnet.whois(addr)
      for result in results:
        found_addr, found_id = tuple(result)
        if addr == found_id:
          self.targetted_devices_found.add((found_addr, found_id))


  def stop_discovery(self):
    self.cancelled = True
    if self.bacnet_scan_executor_thread:
      self.bacnet_scan_executor_thread.join()
    self.result_producer_thread.join()


  def discover_device(self, device_address, device_id) -> DiscoveryEvent:
  
    ### Existence of a device
    ###################################################################
    event = udmi.schema.discovery_event.DiscoveryEvent(
        generation=self.config.generation,
        family=self.family,
        addr=str(device_id),
    )

    try:
      ipaddress.ip_address(device_address)
    except ValueError:
      pass
    else:
      event.families["ipv4"] = udmi.schema.discovery_event.DiscoveryFamily(
          addr=device_address
      ) 

    ### Basic Properties
    ###################################################################
    if self.config.depth in ["system", "refs"]:
      try:
        (object_name, 
         vendor_name, 
         firmware_version,
         model_name,
         serial_number, 
         description, 
         location, 
         application_version) = (
            self.bacnet.readMultiple(
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
        )
    
        event.system.serial_no = serial_number
        event.system.hardware.make = vendor_name
        event.system.hardware.model = model_name

        event.system.ancillary["description"] = description
        event.system.ancillary["location"] = location
        event.system.ancillary["application_version"] = application_version
        event.system.ancillary["firmware"] = firmware_version
        event.system.ancillary["name"] = object_name

      except (BAC0.core.io.IOExceptions.SegmentationNotSupported, Exception) as err:
        logging.exception(f"error reading from {device_address}/{device_id}")
        event.status = udmi.schema.discovery_event.Status("discovery.error", 500, str(err))
        return event

    ### Points
    ###################################################################
    if self.config.depth in ["refs"]:
      try:
        device = BAC0.device(device_address, device_id, self.bacnet, poll=0)

        for point in device.points:
          ref = DiscoveryPoint()
          ref.name = point.properties.name
          ref.description = point.properties.description
          ref.ancillary["present_value"] = point.lastValue
          ref.type = point.properties.type
          if isinstance(point.properties.units_state, list): 
            ref.possible_values = point.properties.units_state
          elif isinstance(point.properties.units_state, str): 
            ref.units = point.properties.units_state
          point_id = BacnetObjectAcronyms[point.properties.type].value + ":" + point.properties.address
          event.refs[point_id] = ref
    
      except Exception as err:
        event.status = udmi.schema.discovery_event.Status("discovery.error", 500, str(err))
        logging.exception(f"error reading from {device_address}/{device_id}")
        return event
    
    return event
  
  def targetted_scan_producer(self) -> set[tuple[str, str]]:
    """Returns the list of discovered devices from a targetted bacnet scan."""
    return self.targetted_devices_found
  
  def bacnet_discovery_producer(self) -> set[tuple[str, str]]:
    """Returns the list of discovered devices from a global bacnet scan."""
    if self.bacnet.discoveredDevices is not None:
      return (
          set(self.bacnet.discoveredDevices.keys()) - self.devices_published
      )
    return set()


  @discovery.main_task
  @discovery.catch_exceptions_to_state
  def devices_consumer(self, producer = Callable[[], set[tuple[str, str]]]) -> None:
    # used to determine when a scan is complete
    # in reality to a who/is scan devices will respond very quickly (seconds)
    unchanged_device_count_seconds:int = 0

    # when to determine done
    UNCHANGED_COUNT_THRESHOLD:int = 300

    while not self.cancelled:
      try:
        new_devices = (
            producer() - self.devices_published
        )
        
        if not new_devices:
          unchanged_device_count_seconds += 1
          continue
        unchanged_device_count_seconds = 0

        for device in new_devices:
          # Check that it is not cancelled in the inner loop too because this
          # can take a long time to enumerate through all found devices.
          if self.cancelled:
            return
          
          address, id = device
          start = time.monotonic()
          event = self.discover_device(address, id)
          end = time.monotonic() 
          logging.info(f"discovery for {device} in {end - start} seconds")

          self.publish(event)
          self.devices_published.add(device)
      except AttributeError as err:
        logging.exception(err)
      finally:
        if self.cancelled:
          return
        if unchanged_device_count_seconds > UNCHANGED_COUNT_THRESHOLD:
          return
        time.sleep(1)
