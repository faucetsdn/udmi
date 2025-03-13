import dataclasses
import ipaddress
import logging
import os
import queue
import re
import shlex
import socket
import subprocess
import threading
import time
from typing import Any, Callable
import xml.etree.ElementTree
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
import dataclasses

BAC0.log_level(log_file=None, stdout=None, stderr=None)
BAC0.log_level("silence")


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
      bacnet_intf: str = None,
  ):
    self.devices_published = set()
    self.cancelled = None
    self.result_producer_thread = None

    self.bacnet = BAC0.lite(ip=bacnet_ip, port=bacnet_port)

    super().__init__(state, publisher)

  def start_discovery(self):
    self.devices_published.clear()
    self.cancelled = False
    self.result_producer_thread = threading.Thread(
        target=self.result_producer, args=[], daemon=True
    )
    self.result_producer_thread.start()
    self.bacnet.discover(global_broadcast=True)

  def stop_discovery(self):
    self.cancelled = True
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
    if self.config.depth in ["system", "device"]:
      try:
        object_name, vendor_name, firmware_version, model_name, serial_number = (
            self.bacnet.readMultiple(
                f"{device_address} device {device_id} objectName vendorName"
                " firmwareRevision modelName serialNumber"
            )
        )

        logging.info("object_name: %s vendor_name: %s firmware: %s model: %s serial: %s",  object_name, vendor_name, firmware_version, model_name, serial_number)

        event.system.serial_no = serial_number
        event.system.hardware.make = vendor_name
        event.system.hardware.model = model_name
    
        event.system.ancillary["firmware"] = firmware_version
        event.system.ancillary["name"] = object_name

      except (BAC0.core.io.IOExceptions.SegmentationNotSupported, Exception) as err:
        logging.exception(f"error reading from {device_address}/{device_id}")
        return event

    ### Points
    ###################################################################
    if self.config.depth in ["refs", "system", "device"]:
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
  
  @discovery.main_task
  @discovery.catch_exceptions_to_state
  def result_producer(self):
    while not self.cancelled:
      try:
        # discoveredDevices is "None" before initialised
        if self.bacnet.discoveredDevices is not None:
          new_devices = (
              set(self.bacnet.discoveredDevices.keys()) - self.devices_published
          )
          for device in new_devices:
            # Check that it is not cancelled in the inner loop too because this
            # can take a long time to enumerate through all found devices.
            if self.cancelled:
              break
            
            address, id = device
            start = time.monotonic()
            event = self.discover_device(address, id)
            end = time.monotonic() 
            logging.info(f"discovery for {device} in {end - start} seconds")

            self.publish(event)
            self.devices_published.add(device)

          if self.cancelled:
            return
      except AttributeError as err:
        logging.exception(err)
      finally:
        time.sleep(1)
