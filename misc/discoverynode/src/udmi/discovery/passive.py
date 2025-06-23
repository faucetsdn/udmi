import dataclasses
import ipaddress
import logging
import queue
import socket
import struct
import threading
import time
import scapy.all
import scapy.layers.inet
import scapy.sendrecv
import scapy.utils
import udmi.discovery.discovery as discovery
import udmi.schema.discovery_event
import udmi.schema.state

BACNET_BVLC_MARKER = b"\x81"
BACNET_APDU_I_AM_START = b"\x10\x00\xc4"
PRIVATE_IP_BPF_FILTER = (
    "ip and "
    "("
    "src net 10.0.0.0/8 or src net 172.16.0.0/12 or src net 192.168.0.0/16 or "
    "src net 100.64.0.0/10"
    ") and ("
    "dst net 10.0.0.0/8 or dst net 172.16.0.0/12 or dst net 192.168.0.0/16 or "
    "dst net 100.64.0.0/10"
    ")"
)


@dataclasses.dataclass(eq=True, frozen=True)
class PassiveScanRecord:
  addr: str
  mac: str | None = None
  hostname: str | None = None


class PassiveNetworkDiscovery(discovery.DiscoveryController):
  """Passive Network Discovery."""

  family = "ipv4"

  def __init__(self, state, publisher, *, interface=None):

    self.queue = queue.SimpleQueue()
    self.interface = None
    self.addresses_seen = set()
    self.device_records = set()
    self.devices_records_published = set()
    self.publish_interval = 5
    self.cancel_threads = threading.Event()
    self.scapy_is_go_signal = threading.Event()

    # Configure Scapy
    scapy.all.conf.layers.filter([
        scapy.layers.inet.Ether,
        scapy.layers.inet.IP,
        scapy.layers.inet.ICMP,
        scapy.layers.inet.UDP,
    ])

    super().__init__(state, publisher)

  def _get_packet_counter_total(self):
    try:
      with open(f"/sys/class/net/{self.interface}/statistics/rx_packets") as f:
        rx = int(f.read())

      with open(f"/sys/class/net/{self.interface}/statistics/tx_packets") as f:
        tx = int(f.read())

      return rx + tx
    except FileNotFoundError:
      return

  def scapy_is_go(self): 
    # Signal that packet capture has started. Callback executed by scapy
    # library on success. Necesary because exception disapears into the
    # the aether and we never no.
    logging.debug("SCAPY is go")
    self.scapy_is_go_signal.set()

  def start_discovery(self):
    # Queue processor uses a threading event for enabling/disabling
    self.cancel_threads.clear()
    self.scapy_is_go_signal.clear()
    rx = 0
    tx = 0
    self.packets_seen = 0
    self.devices_records_published.clear()
    self.device_records.clear()
    self.addresses_seen.clear()

    # Turn-on the queue worker to consume sniffed packets
    self.queue_thread = threading.Thread(
        target=self.queue_worker, args=[], daemon=True
    )
    self.queue_thread.start()

    # Turn-on the main service and publisher
    self.service_thread = threading.Thread(
        target=self.discovery_service, args=[], daemon=True
    )
    self.service_thread.start()

    self.sniffer = scapy.sendrecv.AsyncSniffer(
        prn=self.queue.put, store=False, iface=self.interface, started_callback=self.scapy_is_go, filter=PRIVATE_IP_BPF_FILTER
    )

    self.sniffer.start()
    if not self.scapy_is_go_signal.wait(5):
      raise Exception(f"Failed to initiate packet capture - {self.sniffer.exception}")

    self.packet_count_start = self._get_packet_counter_total()

  def stop_discovery(self):
    self.sniffer.stop()
    self.sniffer.join()

    packet_count_end = self._get_packet_counter_total()
    
    self.cancel_threads.set()
    self.queue_thread.join()
    self.service_thread.join()

    if self.interface:
      logging.info(
          f"packets seen by scapy: %d, packets seen by interface: %d",
          self.packets_seen,
          packet_count_end - self.packet_count_start,
      )
    logging.info("devices seen: %d ", len(self.addresses_seen))

  def discovery_service(self):
    while True:
      new_device_records = self.device_records - self.devices_records_published

      for device_record in new_device_records:
        self.publish(
            udmi.schema.discovery_event.DiscoveryEvent(
                generation=self.config.generation,
                addr=device_record.addr,
                family=self.family,
                families=dict(
                    ether=udmi.schema.discovery_event.DiscoveryFamily(
                        device_record.mac
                    )
                ),
            )
        )
        self.devices_records_published.add(device_record)

      if self.cancel_threads.is_set():
        return

      time.sleep(self.publish_interval)

  def get_host(self, ip: str):
    try:
      return socket.gethostbyaddr(ip)[0]
    except socket.herror:
      return None

  def queue_worker(self):
    """Takes sniffer packets from the queue and processes them."""
    self.packets_seen = 0
    self.ip_packets_seen = 0
    self.bacnet_packets_seen = 0
    while True:
      try:
        item = self.queue.get(True, 1)
        if self.packets_seen % 10000 == 0:
          logging.info("%d packets seen", self.packets_seen)
        self.packets_seen += 1
        if scapy.layers.inet.IP in item:
          self.ip_packets_seen += 1

          # A packet "sees" two devices - the source and the destination
          for x in ["src", "dst"]:

            if x == "src":
              if scapy.layers.inet.UDP in item and (
                      item[scapy.layers.inet.UDP].dport == 47808
                      or item[scapy.layers.inet.UDP].sport == 47808
                  ):
                    # Treat packet as a BACnet packet
                    payload = bytes(item[scapy.layers.inet.UDP].payload)
                    if payload[0:1] == BACNET_BVLC_MARKER:
                      if (index := payload.find(BACNET_APDU_I_AM_START, 0, 10)) > 0:
                        object_identifier = int.from_bytes(
                            payload[index + 3 : index + 3 + 4]
                        )
                        instance_number = object_identifier & 0x3FFFF
                        logging.info(f"maybe bacnet addr {instance_number} at {getattr(item[scapy.layers.inet.IP], x)}")
            # Not all packets have IP addresses, but this scan requires an IP Address
            if (
                (ip := getattr(item[scapy.layers.inet.IP], x))
                and ip is not None
                and ip not in self.addresses_seen
            ):
              self.addresses_seen.add(ip)
              self.device_records.add(
                  PassiveScanRecord(
                      addr=ip,
                      hostname=self.get_host(ip),
                      mac=getattr(item[scapy.layers.inet.Ether], x),
                  )
              )
      except queue.Empty:
        if self.cancel_threads.is_set():
          return

