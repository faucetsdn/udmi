import concurrent.futures
import itertools
import logging
import threading
import time
import udmi.discovery.discovery as discovery
from udmi.schema.discovery_event import DiscoveryEvent, DiscoveryFamily


class NumberDiscovery(discovery.DiscoveryController):

  scan_family = "vendor"

  def __init__(self, state, publisher):
    self.cancelled = None
    self.task_thread = None
    super().__init__(state, publisher)

  def start_discovery(self):
    self.cancelled = False
    self.task_thread = threading.Thread(
        target=self.discoverer, args=[], daemon=True
    )
    self.task_thread.start()

  @discovery.catch_exceptions_to_state
  @discovery.main_task
  def discoverer(self):
    for i in itertools.count(1):
      if self.cancelled:
        return
      result = DiscoveryEvent(
          generation=self.generation, scan_family=self.scan_family, scan_addr=i
      )
      self.publisher(result)
      time.sleep(1)

  def stop_discovery(self):
    self.cancelled = True
    self.task_thread.join()
