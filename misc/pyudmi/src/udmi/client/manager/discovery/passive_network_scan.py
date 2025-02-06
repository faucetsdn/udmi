import dataclasses
import logging
import queue
import socket
import threading
import time
from typing import Callable, Optional

import scapy.all
import scapy.layers.inet
import scapy.sendrecv

from udmi_schema.schema.events_discovery import DiscoveryEvents
from udmi_schema.schema.state import State
from udmi_schema.schema.discovery_family import FamilyDiscovery

from udmi.client.manager.discovery.discovery_manager import DiscoveryManager

BACNET_BVLC_MARKER = b"\x81"
BACNET_APDU_I_AM_START = b"\x10\x00\xc4"
PRIVATE_IP_BPC_FILTER = (
    "ip and "
    "("
    "src net 10.0.0.0/8 or src net 172.16.0.0/12 or src net 192.168.0.0/16 or "
    "dst net 10.0.0.0/8 or dst net 172.16.0.0/12 or dst net 192.168.0.0/16 or "
    "dst net 100.64.0.0/10 or src net 100.64.0.0/10"
    ")"
)


@dataclasses.dataclass(eq=True, frozen=True)
class PassiveScanRecord:
    addr: str
    mac: Optional[str] = None
    hostname: Optional[str] = None


def get_host(ip: str) -> Optional[str]:
    try:
        return socket.gethostbyaddr(ip)[0]
    except socket.herror:
        logging.warning(f"Reverse DNS lookup failed for {ip}")
        return None
    except socket.gaierror as e:
        logging.error(f"Error during DNS lookup for {ip}: {e}")


class PassiveNetworkScan(DiscoveryManager):
    scan_family = "ipv4"

    def __init__(
        self,
        state: State,
        publisher: Callable[[DiscoveryEvents], None],
        *,
        interface: Optional[str] = None,
        publish_interval: int = 5,
    ):
        self.queue: queue.SimpleQueue = queue.SimpleQueue()
        self.interface: Optional[str] = interface
        self.addresses_seen: set[str] = set()
        self.device_records: set[PassiveScanRecord] = set()
        self.devices_records_published: set[PassiveScanRecord] = set()
        self.publish_interval: int = publish_interval
        self.cancel_threads: threading.Event = threading.Event()
        self.packets_seen: int = -1
        self.packet_count_start: int = -1
        self.queue_thread: threading.Thread = threading.Thread(
            target=self.queue_worker, args=[], daemon=True
        )
        self.service_thread: threading.Thread = threading.Thread(
            target=self.discovery_service, args=[], daemon=True
        )
        self.sniffer: scapy.sendrecv.AsyncSniffer = scapy.sendrecv.AsyncSniffer(
            prn=self.queue.put, store=False, iface=self.interface,
            filter=PRIVATE_IP_BPC_FILTER
        )
        # Configure Scapy
        scapy.all.conf.layers.filter(
            [scapy.layers.inet.Ether, scapy.layers.inet.IP,
             scapy.layers.inet.ICMP, scapy.layers.inet.UDP]
        )
        logging.debug(f"{self.__class__.__name__} initialized with interface: "
                      f"{self.interface}, "
                      f"publish_interval: {self.publish_interval}")
        super().__init__(state, publisher)

    def start_discovery(self) -> None:
        logging.info("Starting network discovery...")
        self.cancel_threads.clear()
        self.packets_seen = 0
        self.devices_records_published.clear()
        self.device_records.clear()
        self.addresses_seen.clear()

        self.queue_thread.start()
        self.service_thread.start()
        self.sniffer.start()
        self.packet_count_start = self._get_packet_counter_total()
        logging.info("Network discovery started.")

    def stop_discovery(self) -> None:
        logging.info("Stopping network discovery...")
        self.sniffer.stop()
        self.sniffer.join()

        packet_count_end = self._get_packet_counter_total()

        self.cancel_threads.set()
        self.queue_thread.join()
        self.service_thread.join()

        if self.interface:
            logging.info(
                f"packets seen by scapy: {self.packets_seen}, packets seen by "
                f"interface: {packet_count_end - self.packet_count_start}"
            )
        logging.info(f"devices seen: {len(self.addresses_seen)}")
        logging.info("Network discovery stopped.")

    def _get_packet_counter_total(self) -> Optional[int]:
        if not self.interface:
            logging.warning("No interface specified, cannot get packet counter")
            return None
        try:
            with open(
                 f"/sys/class/net/{self.interface}/statistics/rx_packets") as f:
                rx = int(f.read())

            with open(
                 f"/sys/class/net/{self.interface}/statistics/tx_packets") as f:
                tx = int(f.read())

            return rx + tx
        except FileNotFoundError:
            logging.error(f"Packet counter file not found for "
                          f"interface {self.interface}")
            return None
        except Exception as e:
            logging.exception(f"Unexpected error reading packet counters for "
                              f"{self.interface}: {e}")
            return None

    def queue_worker(self):
        """
        Takes sniffer packets from the queue and processes them.
        """
        logging.debug("Queue worker thread started.")
        self.packets_seen = 0
        self.ip_packets_seen = 0
        self.bacnet_packets_seen = 0
        while not self.cancel_threads.is_set():
            try:
                item = self.queue.get(timeout=1)
                self.packets_seen += 1
                if scapy.layers.inet.IP in item:
                    self.ip_packets_seen += 1

                    # A packet "sees" 2 devices: the source and the destination
                    for x in ["src", "dst"]:
                        if x == "src":
                            if scapy.layers.inet.UDP in item and (
                                item[scapy.layers.inet.UDP].dport == 47808
                                or item[scapy.layers.inet.UDP].sport == 47808
                            ):
                                # Treat packet as a BACnet packet
                                payload = bytes(
                                    item[scapy.layers.inet.UDP].payload)
                                if payload[0:1] == BACNET_BVLC_MARKER:
                                    if (index := payload.find(
                                        BACNET_APDU_I_AM_START, 0, 10)) > 0:
                                        object_identifier = int.from_bytes(
                                            payload[index + 3: index + 3 + 4]
                                        )
                                        instance_number = object_identifier & 0x3FFFF
                                        logging.info(
                                            f"maybe bacnet addr {instance_number} at {getattr(item[scapy.layers.inet.IP], x)}")
                        # Not all packets have IP addresses, but this scan requires an IP Address
                        ip = getattr(item[scapy.layers.inet.IP], x,
                                     None)  # Use None as default
                        if ip and ip not in self.addresses_seen:
                            self.addresses_seen.add(ip)
                            try:
                                hostname = get_host(ip)
                            except Exception as e:
                                logging.error(
                                    f"Error getting host for {ip}: {e}")
                                hostname = None
                            try:
                                mac = getattr(item[scapy.layers.inet.Ether], x,
                                              None)  # Use None as default
                            except AttributeError:
                                mac = None
                            self.device_records.add(
                                PassiveScanRecord(addr=ip, hostname=hostname,
                                                  mac=mac)
                            )
            except queue.Empty:
                pass

            except Exception as e:
                logging.exception(f"Error processing packet from queue: {e}")

        logging.debug("Queue worker thread stopped.")

    def discovery_service(self):
        logging.debug("Discovery service thread started.")
        while not self.cancel_threads.is_set():  # More robust thread stopping
            new_device_records = self.device_records - self.devices_records_published

            if new_device_records:
                try:
                    # Batch publish the records
                    discovery_events = [
                        DiscoveryEvents(
                            generation=self.config.generation,
                            scan_addr=device_record.addr,
                            scan_family=self.scan_family,
                            families=dict(
                                ether=FamilyDiscovery(addr=device_record.mac)),
                        )
                        for device_record in new_device_records
                    ]
                    for event in discovery_events:
                        self.publish(event)  # Publish all events
                    self.devices_records_published.update(new_device_records)
                    logging.info(
                        f"Published {len(new_device_records)} new device records.")

                except Exception as e:
                    logging.error(f"Error publishing discovery events: {e}")

            try:
                time.sleep(self.publish_interval)
            except InterruptedError:
                # Handle potential interruption during sleep
                pass

        logging.debug("Discovery service thread stopped.")
