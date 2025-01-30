import concurrent.futures
import itertools
import logging
import threading
import time
import udmi.discovery.discovery as discovery
from udmi.schema.discovery_event import DiscoveryEvent, DiscoveryFamily


class NumberDiscovery(discovery.DiscoveryController):

  family = "vendor"

  def __init__(self, state, publisher, *, range):
    self.cancelled = None
    self.task_thread = None
    self.range = range
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
    if self.range:
      iterator = self.range.split(",")
    else:
      iterator = itertools.count(1)

    for i in iterator:
      if self.cancelled:
        return
      if i:
        result = DiscoveryEvent(
            generation=self.generation, family=self.scan_family, addr=str(i)
        )
        self.publish(result)
      time.sleep(1)

  def stop_discovery(self):
    self.cancelled = True
    self.task_thread.join()
