import json
import logging
import threading
import time
import textwrap

from typing import Any, Callable

import udmi.discovery
import udmi.discovery.bacnet
import udmi.discovery.nmap
import udmi.discovery.numbers
import udmi.discovery.passive
import udmi.publishers.publisher
import udmi.schema.discovery_event
import udmi.schema.state
import udmi.schema.util
import udmi.schema.util


class UDMI:
  """UDMI Client."""

  STATE_TOPIC_TEMPLATE = "{}/state"
  EVENT_POINTSET_TOPIC_TEMPLATE = "{}/events/pointset"
  EVENT_DISCOVERY_TOPIC_TEMPLATE = "{}/events/discovery"
  EVENT_SYSTEM_TOPIC_TEMPLATE = "{}/events/system"
  _state_monitor_interval = 1  # [s]

  def __init__(
      self,
      *,
      publisher: udmi.publishers.publisher.Publisher,
      topic_prefix: str,
      config: dict[str:Any],
  ):
    self.publisher = publisher
    self.config = config
    self.state = udmi.schema.state.State()
    self.components = {}
    self.callbacks = {}  # lambda,

    self.topic_state = UDMI.STATE_TOPIC_TEMPLATE.format(topic_prefix)

    self.topic_discovery_event = UDMI.EVENT_DISCOVERY_TOPIC_TEMPLATE.format(
        topic_prefix
    )
    self.topic_system_event = UDMI.EVENT_SYSTEM_TOPIC_TEMPLATE.format(
        topic_prefix
    )

    print(self.topic_state)

    threading.Thread(target=self.state_monitor, args=[], daemon=True).start()

    self.enable_discovery()

  def process_config(self, config: str):
    logging.error(f"config callback {config[:24]}")

  # NOTE: This is flawed, because if the predicate starts to fail, for example if the block was removed, then
  # the new config is never pased to the the config. Rather, functions should always receive configs and the predicate could match false
  def add_config_route(self, filter: Callable, destination: Callable):
    self.callbacks[filter] = destination

  def config_handler(self, config_string: str):

    try:
      config = json.loads(config_string)
      logging.info("received config %s: \n%s", config["timestamp"], textwrap.indent(config_string, "\t\t\t"))
  
    except json.JSONDecodeError as err:
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

  def state_monitor(self):
    self._last_state_hash = None
    while True:
      current_hash = self.state.get_hash()
      if self._last_state_hash != current_hash:
        self.publish_state()
        self._last_state_hash = current_hash
      time.sleep(self._state_monitor_interval)

  def publish_state(self):
    state = self.state.to_json()
    logging.warning("published state: %s", state)
    self.publisher.publish_message(self.topic_state, state)

  def publish_discovery(self, payload):
    logging.warning("published discovery: %s", payload.to_json())
    self.publisher.publish_message(self.topic_discovery_event, payload.to_json())

  def enable_discovery(self):

    number_discovery = udmi.discovery.numbers.NumberDiscovery(
        self.state, self.publish_discovery
    )

    self.add_config_route(
        lambda x: True,
        number_discovery,
    )

    self.components["number_discovery"] = number_discovery
    
    return
    bacnet_discovery = udmi.discovery.bacnet.GlobalBacnetDiscovery(
        self.state,
        self.publish_discovery,
        bacnet_ip=self.config.get("bacnet", {}).get("ip"),
    )

    self.add_config_route(
        lambda x: True,
        bacnet_discovery,
    )

    passive_discovery = udmi.discovery.passive.PassiveNetworkDiscovery(
        self.state, self.publish_discovery
    )

    self.components["bacnet_discovery"] = passive_discovery

    # THESE TWO USE THE SAME SCAN_FAMILY
    # THEIR `states` IN THE OVERALL STATE CLASHES
    # because both set state.discovery.families.SCAN_FAMILY = self.state

  
    self.add_config_route(
        lambda x: True,
        passive_discovery,
    )

    self.components["passive_discovery"] = passive_discovery
    

    nmap_banner_scan = udmi.discovery.nmap.NmapBannerScan(
        self.state,
        self.publish_discovery,
        target_ips=self.config["nmap"]["targets"],
    )

    self.add_config_route(
        lambda x: nmap_banner_scan.scan_family
        in x.get("discovery", {}).get("families", {})
        and x["discovery"]["families"][nmap_banner_scan.scan_family].get(
            "depths"
        )
        == "banner",
        nmap_banner_scan,
    )

    self.components["nmap_banner_scan"] = nmap_banner_scan
    
