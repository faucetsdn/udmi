import dataclasses
import ipaddress
import logging
import queue
import socket
import threading
import time
import scapy.all
import scapy.layers.inet
import scapy.sendrecv
import udmi.discovery.discovery as discovery
import udmi.schema.discovery_event
import udmi.schema.state


@dataclasses.dataclass(eq=True, frozen=True)
class PassiveScanRecord:
  addr: str
  mac: str | None = None
  hostname: str | None = None


class PassiveNetworkDiscovery(discovery.DiscoveryController):
  """Passive Network Discovery."""

  scan_family = "ipv4"

  def __init__(self, state, publisher):

    self.queue_log_interval = 5000
    self.queue = queue.SimpleQueue()

    self.addresses_seen = set()
    self.device_records = set()
    self.devices_records_published = set()
    self.scan_interval = 120
    self.publish_interval = 5
    self.cancel_threads = threading.Event()

    # Configure Scapy
    scapy.all.conf.layers.filter(
        [scapy.layers.inet.Ether, scapy.layers.inet.IP, scapy.layers.inet.ICMP]
    )

    super().__init__(state, publisher)

  def start_discovery(self):
    # Queue processor uses a threading event for enabling/disabling
    self.cancel_threads.clear()
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

    self.sniffer = scapy.sendrecv.AsyncSniffer(prn=self.queue.put, store=False)
    self.sniffer.start()

    """with open("/sys/class/net/wlp0s20f3/statistics/rx_packets") as f:
      rx = int(f.read())
  
    with open("/sys/class/net/wlp0s20f3/statistics/tx_packets") as f:
      tx = int(f.read())

    self.packet_count_start = rx + tx"""

  def stop_discovery(self):
    self.sniffer.stop()
    self.sniffer.join()

    """
    with open("/sys/class/net/wlp0s20f3/statistics/rx_packets") as f:
      rx = int(f.read())
    with open("/sys/class/net/wlp0s20f3/statistics/tx_packets") as f:
      tx = int(f.read())
    self.packet_count_end = rx + tx
    """
    self.cancel_threads.set()
    self.queue_thread.join()
    self.service_thread.join()

    # logging.info(f"actual packets: %d, seen by scapy: %d", self.packet_count_end - self.packet_count_start, self.packets_seen)
    logging.info("devices seen: %d ", len(self.addresses_seen))

  def discovery_service(self):
    while True:
      new_device_records = self.device_records - self.devices_records_published

      for device_record in new_device_records:
        self.publisher(
            udmi.schema.discovery_event.DiscoveryEvent(
                generation=self.config.generation,
                scan_addr=device_record.addr,
                scan_family=self.scan_family,
                families=dict(
                    ethmac=udmi.schema.discovery_event.DiscoveryFamily(
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
    self.packets_seen = 0
    self.ip_packets_seen = 0
    while True:
      try:
        item = self.queue.get(True, 1)
        self.packets_seen += 1
        if scapy.layers.inet.IP in item:
          self.ip_packets_seen += 1
          # A packet "sees" two devices - the source and the destination
          for x in ["src", "dst"]:
            # Not all packets have IP addresses, but this scan requires an IP Address
            if (
                (ip := getattr(item[scapy.layers.inet.IP], x))
                and ip is not None
                and ip not in self.addresses_seen
            ):
              if ipaddress.ip_address(ip).is_private:
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
