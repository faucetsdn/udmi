import concurrent.futures
import itertools
import logging
import threading
import time
import udmi.discovery.discovery as discovery
from udmi.schema.discovery_event import DiscoveryEvent, DiscoveryFamily


class NumberDiscovery(discovery.DiscoveryController):

  family = "vendor"

  def __init__(self, state_entry, publisher, *, scan_id, range = None):
    # Number discovery
    # 
    # Args:
    #   state_entry
    #   publisher
    #   (kw) scan_id (string): see DiscoveryController
    #   (kw) range (string): Comma seperated string of the numbers to discover,
    #      used when the scan config sets no `addrs`; or None for infinite
    #      sequential from 1.
    self.cancelled = None
    self.task_thread = None
    self.range = range
    super().__init__(state_entry, publisher, scan_id=scan_id)

  def start_discovery(self):
    self.cancelled = False
    self.task_thread = threading.Thread(
        target=self.discoverer, args=[], daemon=True
    )
    self.task_thread.start()

  @discovery.catch_exceptions_to_state
  @discovery.main_task
  def discoverer(self):
    # Per-scan `addrs` take precedence so that multiple vendor scans can each
    # have their own target list; the node-local `range` is the fallback.
    if self.config is not None and self.config.addrs:
      iterator = list(self.config.addrs)
    elif self.range:
      iterator = self.range.split(",")
    else:
      iterator = itertools.count(1)

    for i in iterator:
      if self.cancelled:
        return
      if i:
        addr = str(i)
        network = None
        if ":" in addr:
          network, addr = addr.split(":", 1)
        result = DiscoveryEvent(
            generation=self.generation, family=self.family, addr=addr, network=network
        )
        self.publish(result)
      time.sleep(1)

  def stop_discovery(self):
    self.cancelled = True
    if self.task_thread:
      self.task_thread.join()
