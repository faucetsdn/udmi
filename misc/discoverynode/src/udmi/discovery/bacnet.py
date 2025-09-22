
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
import re
import concurrent.futures
from typing import Iterable

BAC0.log_level("silence")
for name in logging.getLogger().manager.loggerDict:
  if name.startswith("BAC0"):
    logging.getLogger(name).setLevel(logging.CRITICAL)

_IP_ADDRESS_REGEX = r"([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})"

def future_wait_and_count_outstanding(futures: Iterable[concurrent.futures.Future], timeout: int) -> int:
  """A wrapper arround concurrent.futures.wait which returns a count of 
  outstanding (not done or cancelled) futures.

  Args:
    futures: iteratable of futures
    timeout: maximum time to wait
  
  Returns:
    count of outstanding futures
  """
  _, outstanding = concurrent.futures.wait(futures, 1)
  return len(outstanding)

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
    self.resolver_dispatcher_thread = None
   
    self.bacnet = BAC0.lite(ip=bacnet_ip, port=bacnet_port)
    
    super().__init__(state, publisher)

  def resolve_task_dispatcher(self, target_ips: list[str]):
    """Dispatches ip->bacnet resolv tasks across simulateous workers"""
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
      futures = [executor.submit(self.resolve_task, ip) for ip in target_ips]
      while (
          future_wait_and_count_outstanding(futures, 1) > 0
      ):
        if self.cancelled:
          executor.shutdown(True, cancel_futures=True)
          break
  
  def resolve_task(self, ip_address: str) -> None:
    if id := self.get_bacnet_id_from_ip(ip_address):
      logging.debug("found bacnet %s at ip %s", id, ip_address)
      self.targetted_devices_found.add((ip_address, id))

  def get_bacnet_id_from_ip(self, ip_address: str) -> int | bool:
    try:
      _, addr = self.bacnet.read(f"{ip_address} device 4194303 objectIdentifier", None, 0, None, 3)
      return addr
    except BAC0.core.io.IOExceptions.NoResponseFromController:
      return False
    except Exception:
      return False

  def start_discovery(self):
    self.devices_published.clear()
    self.targetted_devices_found.clear()

    self.cancelled = False

    if not self.config.addrs:
      self.bacnet.discover(global_broadcast=True)
      device_list_function = self.discovery_device_list_producer
    else:
      # addrs could in thory be a device IP + address
      # or an MSTP address, but currently only IP addresses are supported.
      for addr in self.config.addrs:
        if not re.match(_IP_ADDRESS_REGEX, addr):
          raise RuntimeError("Only IP adress supported")
        
      self.resolver_dispatcher_thread = threading.Thread(
          target=self.resolve_task_dispatcher, args=[copy.copy(self.config.addrs)], daemon=True
      )
      self.resolver_dispatcher_thread.start()
      device_list_function = self.targetted_scan_device_list_producer

    self.result_producer_thread = threading.Thread(
        target=self.devices_consumer, args=[device_list_function], daemon=True
    )
    self.result_producer_thread.start()


  def stop_discovery(self):
    self.cancelled = True
    if self.resolver_dispatcher_thread:
      self.resolver_dispatcher_thread.join()
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
        (
         object_name, 
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
  
  def targetted_scan_device_list_producer(self) -> set[tuple[str, str]]:
    """Returns the list of discovered devices from a targetted bacnet scan."""
    return self.targetted_devices_found
  
  def discovery_device_list_producer(self) -> set[tuple[str, str]]:
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

  def __del__(self):
    self.bacnet.disconnect()
    super().__del__()