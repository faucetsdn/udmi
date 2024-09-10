from schema.discovery_event import DiscoveryEvent
from typing import Callable, Any
import schema.state
import time
import discovery
import threading
import BAC0
import concurrent.futures
import multiprocessing


def bacnet_discovery():
  bacnet = BAC0.lite(port=47808)
  print(bacnet)
  while True:
    bacnet.discover(global_broadcast=True)
    print(bacnet.devices)
    time.sleep(5)
  return set(bacnet.devices)

class GlobalBacnetDiscovery(discovery.DiscoveryController):
  """Passive Network Discovery."""
  scan_family = "bacnet"

  def __init__(self, state: schema.state.LocalnetFamily, publisher: Callable[[DiscoveryEvent],None], *, bacnet_ip: str):
    
    BAC0.log_level("silence")
    BAC0.log_level(log_file=None, stdout=None, stderr=None)
    self.devices_published = set()
    self.cancelled = None
    self.result_producer_thread = None

    super().__init__(state, publisher)

  def start_discovery(self):
    self.devices_published.clear()
    self.cancelled = False
    with concurrent.futures.ProcessPoolExecutor(mp_context=multiprocessing.get_context("spawn")) as executor:
      future = executor.submit(bacnet_discovery)
      print(future.result())
  
  def stop_discovery(self):
    self.cancelled = True
    self.result_producer_thread.join()
  