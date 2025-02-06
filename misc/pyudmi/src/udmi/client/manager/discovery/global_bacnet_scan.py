import asyncio
import ipaddress
import logging
import threading
import time
from enum import StrEnum
from typing import Callable

import BAC0
from BAC0.core.io.IOExceptions import SegmentationNotSupported
from udmi_schema.schema.discovery_family import FamilyDiscovery
from udmi_schema.schema.discovery_ref import RefDiscovery
from udmi_schema.schema.entry import Entry
from udmi_schema.schema.events_discovery import DiscoveryEvents
from udmi_schema.schema.state import State

from udmi.client.manager.discovery.discovery_manager import DiscoveryManager
from udmi.client.manager.discovery.discovery_manager import (
    catch_exceptions_to_status,
    mark_task_complete_on_return
)

LOOP = asyncio.get_event_loop()


class BacnetObjectAcronyms(StrEnum):
    """
    Mapping of object names to accepted acronyms
    """
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


class GlobalBacnetScan(DiscoveryManager):
    scan_family = "bacnet"

    def __init__(
        self,
        state: State,
        publisher: Callable[[DiscoveryEvents], None],
        *,
        bacnet_ip: str = None,
        bacnet_port: int = None,
        bacnet_intf: str = None):
        self.devices_published = set()
        self.cancelled = None
        self.runner_thread = None
        self.bacnet = BAC0.lite(ip=bacnet_ip, port=bacnet_port)
        super().__init__(state, publisher)

    def start_discovery(self) -> None:
        self.devices_published.clear()
        self.cancelled = False
        self.runner_thread = threading.Thread(
            target=self.runner, args=[], daemon=True
        )
        self.runner_thread.start()
        self.bacnet.discover(global_broadcast=True)

    def stop_discovery(self) -> None:
        self.cancelled = True
        self.runner_thread.join()

    @mark_task_complete_on_return
    @catch_exceptions_to_status
    def runner(self):
        while not self.cancelled:
            try:
                # discoveredDevices is "None" before initialised
                if self.bacnet.discoveredDevices:
                    new_devices = (set(self.bacnet.discoveredDevices.keys()) -
                                   self.devices_published)

                    for device in new_devices:
                        # Check that it is not cancelled in the inner loop too
                        # because this can take a long time to enumerate
                        # through all found devices.
                        if self.cancelled:
                            break

                        device_addr, device_id = device
                        start = time.monotonic()
                        event = self.get_discovery_event(device_addr, device_id)
                        end = time.monotonic()
                        logging.info(f"discovery for {device} in {end - start} "
                                     f"seconds")
                        self.publish(event)
                        self.devices_published.add(device)
                    if self.cancelled:
                        return
            except AttributeError as err:
                logging.exception(err)
            finally:
                time.sleep(1)

    def get_discovery_event(self, device_addr, device_id) -> DiscoveryEvents:
        """
        Process device_addr and device_id to create and return the
        corresponding DiscoveryEvent
        :param device_addr:
        :param device_id:
        :return: DiscoveryEvent for the device
        """
        # Capture existence of the device
        event = DiscoveryEvents(
            generation=self.config.generation,
            scan_family=self.scan_family,
            scan_addr=str(device_id)
        )

        try:
            ipaddress.ip_address(device_addr)
        except ValueError:
            pass
        else:
            event.families["ipv4"] = FamilyDiscovery(addr=device_addr)

        # Set basic properties
        try:
            obj_name, vendor_name, firmware_version, model_name, serial_num = (
                self.bacnet.readMultiple(
                    f"{device_addr} device {device_id} objectName vendorName"
                    " firmwareRevision modelName serialNumber")
            )

            logging.info(
                f"object_name: {obj_name} | vendor_name: {vendor_name} | "
                f"firmware: {firmware_version} | model: {model_name} | "
                f"serial_number: {serial_num}")

            event.system.serial_no = serial_num
            event.system.hardware.make = vendor_name
            event.system.hardware.model = model_name
            event.system.ancillary["firmware"] = firmware_version
            event.system.ancillary["name"] = obj_name
        except (SegmentationNotSupported, Exception) as err:
            logging.exception(f"error reading from {device_addr}/{device_id}:"
                              f" {err}")
            return event

        # Capture information on points
        try:
            device = LOOP.run_until_complete(
                BAC0.device(device_addr, device_id, self.bacnet, poll=0))
            for point in device.points:
                ref = RefDiscovery()
                ref.name = point.properties.name
                ref.description = point.properties.description
                ref.ancillary["present_value"] = point.lastValue
                ref.type = point.properties.type
                if isinstance(point.properties.units_state, list):
                    ref.possible_values = point.properties.units_state
                elif isinstance(point.properties.units_state, str):
                    ref.units = point.properties.units_state
                point_id = (BacnetObjectAcronyms[point.properties.type].value +
                            ":" + point.properties.address)
                event.refs[point_id] = ref
        except Exception as err:
            event.status = Entry(category="device.discovery.error", level=500,
                                 detail=str(err))
            logging.exception(f"error reading from {device_addr}/{device_id}")
            return event

        return event
