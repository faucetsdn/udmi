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
import udmi.discovery.discovery as discovery
import udmi.schema.discovery_event
from udmi.schema.discovery_event import DiscoveryEvent
import udmi.schema.state


BAC0.log_level(log_file=None, stdout=None, stderr=None)
BAC0.log_level("silence")


class GlobalBacnetDiscovery(discovery.DiscoveryController):
  """Passive Network Discovery."""

  scan_family = "bacnet"

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

  @discovery.catch_exceptions_to_state
  @discovery.main_task
  def result_producer(self):
    while not self.cancelled:
      try:
        # discoveredDevices is "None" before initialised
        if self.bacnet.discoveredDevices is not None:
          new_devices = (
              set(self.bacnet.discoveredDevices.keys()) - self.devices_published
          )
          for device in new_devices:
            (address, id) = device

            # if depths ...
            # Get make and model
            object_name, vendor_name, firmware_version, model_name, serial_number = (
                self.bacnet.readMultiple(
                    f"{address} device {id} objectName vendorName"
                    " firmwareRevision modelName serialNumber"
                )
            )

            event = udmi.schema.discovery_event.DiscoveryEvent(
                generation=self.config.generation,
                scan_family=self.scan_family,
                scan_addr=str(id),
            )

            event.families["ipv4"] = udmi.schema.discovery_event.DiscoveryFamily(
                addr=address
            ) 
            event.system.serial_no = serial_number
            event.system.hardware.make = vendor_name
            event.system.hardware.model = model_name
            #event.system.software.firmware = firmware_version

            self.publisher(event)
            self.devices_published.add(device)

          if self.cancelled:
            return
      except AttributeError as err:
        logging.exception(err)
      finally:
        time.sleep(1)
