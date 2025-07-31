import contextlib
import json
import logging
import pathlib
import threading
import time
import textwrap

from typing import Any, Callable

import udmi.discovery
import udmi.discovery.bacnet
import udmi.discovery.ether
import udmi.discovery.numbers
import udmi.discovery.passive
import udmi.publishers.publisher
import udmi.schema.discovery_event
import udmi.schema.state
import udmi.schema.util
import udmi.schema.util


class UDMICore:
  """UDMI Client."""

  STATE_TOPIC_TEMPLATE = "{}/state"
  EVENT_POINTSET_TOPIC_TEMPLATE = "{}/events/pointset"
  EVENT_DISCOVERY_TOPIC_TEMPLATE = "{}/events/discovery"
  EVENT_SYSTEM_TOPIC_TEMPLATE = "{}/events/system"
  _STATE_MONITOR_INTERVAL = 1  # [s]
  
  INSTALLED_VERSION_FILE = "installed_version.txt"

  def __init__(
      self,
      *,
      publisher: udmi.publishers.publisher.Publisher,
      topic_prefix: str,
      config: dict[str:Any],
  ):
    self.publisher = publisher
    self.config = config
    self.components = {}
    self.callbacks = {}  # lambda,
    self.state_hooks = []

    # Setup state
    self.state = udmi.schema.state.State()

    try:
      installed_version_file =  pathlib.Path(__file__).with_name(UDMICore.INSTALLED_VERSION_FILE)
      with open(installed_version_file, encoding="utf-8") as f:
        if (installed_version := f.read()) != "":
          self.state.system.software.version = installed_version
    except FileNotFoundError:
      self.state.system.software.version = "1"
    
    self.state.system.hardware.make = "unknown"
    self.state.system.hardware.model = "unknown"
    self.state.system.serial_no = "unknown"
    self.state.system.operation.operational = True

    # Setup topics
    self.topic_state = UDMICore.STATE_TOPIC_TEMPLATE.format(topic_prefix)

    self.topic_discovery_event = UDMICore.EVENT_DISCOVERY_TOPIC_TEMPLATE.format(
        topic_prefix
    )
    self.topic_system_event = UDMICore.EVENT_SYSTEM_TOPIC_TEMPLATE.format(
        topic_prefix
    )

    self.enable_discovery(**config.get("udmi",{}).get("discovery", {}))

    # Note, this depends on topic_state being set
    threading.Thread(target=self.state_monitor, args=[], daemon=True).start()

  def add_config_route(self, filter: Callable, destination: Callable):
    self.callbacks[filter] = destination

  def config_handler(self, config_string: str):

    try:
      config = json.loads(config_string)
      logging.info("received config %s: \n%s", config["timestamp"], textwrap.indent(config_string, "\t\t\t"))
  
    except json.JSONDecodeError as err:
      logging.exception(err)
      self.status_from_exception(err)
      return
    for filter, destination in self.callbacks.items():
      if filter(config) is True:
        try:
          destination.controller(config)
        except Exception as err:
          logging.exception(err)
          self.status_from_exception(err)
    self.state.system.last_config = config["timestamp"]

  def status_from_exception(self, err: Exception):
    """Create state in status"""
    self.state.system.status = udmi.schema.state.Status(
        category="system.config.apply", level=500, message=str(err)
    )

  def register_state_hook(self, hook: Callable) -> None:
    """ Registers a function to execute before state is published.
    
    The main use case is to update values only when state is published.

    Args:
      hook: Function to execute
    """
    self.state_hooks.append(hook)

  def execute_state_hooks(self) -> None:
    """ Execute registered hooks before state is published. """
    for hook in self.state_hooks:
      hook()

  def state_monitor(self):
    self._last_state_hash = None
    while True:
      current_hash = self.state.get_hash()
      if self._last_state_hash != current_hash:
        self.execute_state_hooks()
        self.publish_state()
        
        # Regenerate hash because the "state" may been modified by the hooks
        self._last_state_hash = self.state.get_hash()
      time.sleep(self._STATE_MONITOR_INTERVAL)

  def publish_state(self):
    state = self.state.to_json()
    logging.warning("published state: %s", state)
    self.publisher.publish_message(self.topic_state, state)

  def publish_discovery(self, payload):
    self.publisher.publish_message(self.topic_discovery_event, payload.to_json())

  def enable_discovery(self,*,bacnet=True,vendor=True,ipv4=True,ether=True):

    if vendor:
      number_discovery = udmi.discovery.numbers.NumberDiscovery(
          self.state, self.publish_discovery, range=self.config.get("vendor", {}).get("range"),
      )

      self.add_config_route(
          lambda x: True,
          number_discovery,
      )

      self.register_state_hook(number_discovery.on_state_update_hook)

      self.components["number_discovery"] = number_discovery
    
    if bacnet:
      bacnet_discovery = udmi.discovery.bacnet.GlobalBacnetDiscovery(
          self.state,
          self.publish_discovery,
          bacnet_ip=self.config.get("bacnet", {}).get("ip"),
      )

      self.add_config_route(
          lambda x: True,
          bacnet_discovery,
      )

      self.register_state_hook(bacnet_discovery.on_state_update_hook)

      self.components["bacnet_discovery"] = bacnet_discovery
    
    if ipv4:
      passive_discovery = udmi.discovery.passive.PassiveNetworkDiscovery(
          self.state, self.publish_discovery
      )

      self.add_config_route(
          lambda x: True,
          passive_discovery,
      )

      self.register_state_hook(passive_discovery.on_state_update_hook)

      self.components["passive_discovery"] = passive_discovery
    
    if ether:
      ether_scan = udmi.discovery.ether.EtherDiscovery(
          self.state,
          self.publish_discovery
      )

      self.add_config_route(
          lambda x: True,
          ether_scan,
      )

      self.register_state_hook(ether_scan.on_state_update_hook)

      self.components["ether_scan"] = ether_scan
    
