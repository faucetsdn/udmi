import contextlib
import json
import logging
import pathlib
import threading
import time
import textwrap

from typing import Any, Callable

import udmi.discovery.bacnet
import udmi.discovery.manager
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
    self.state_hooks = []
    self.discovery_manager: udmi.discovery.manager.DiscoveryManager | None = None

    # Setup state
    self.state = udmi.schema.state.State()

    version_paths = [
      pathlib.Path(__file__).with_name(UDMICore.INSTALLED_VERSION_FILE),
      pathlib.Path(__file__).parent.parent / UDMICore.INSTALLED_VERSION_FILE,
    ]

    installed_version = None
    for path in version_paths:
      try:
        with open(path, encoding="utf-8") as f:
          if (content := f.read().strip()) != "":
            installed_version = content
            break
      except FileNotFoundError:
        continue

    if installed_version is not None:
        self.state.system.software.version = installed_version
    else:
        self.state.system.software.version = "unknown"

    
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

  def config_handler(self, config_string: str):

    try:
      config = json.loads(config_string)
      logging.info("received config %s: \n%s", config["timestamp"], textwrap.indent(config_string, "\t\t\t"))
  
    except json.JSONDecodeError as err:
      logging.exception(err)
      self.status_from_exception(err)
      return
    try:
      self.discovery_manager.apply_config(config)
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
    """Creates the DiscoveryManager for the families enabled in local config.

    No scan controllers are constructed here; the manager creates them on
    demand when a family or named scan first appears in a UDMI config.
    """
    flags = dict(bacnet=bacnet, vendor=vendor, ipv4=ipv4, ether=ether)
    enabled = {name for name, flag in flags.items() if flag}

    family_kwargs = {
        "vendor": dict(range=self.config.get("vendor", {}).get("range")),
        "ipv4": dict(subnet_filter=self.config.get("ip", {}).get("subnet_filter")),
        "ether": dict(ping_concurrency=self.config.get("ether", {}).get("ping_concurrency")),
    }

    if "bacnet" in enabled:
      # BAC0 binds a UDP port: exactly one stack per process, shared by all
      # bacnet scan instances (which are mutually exclusive, see bacnet.py).
      family_kwargs["bacnet"] = dict(
          bacnet=udmi.discovery.bacnet.create_bacnet_stack(
              ip=self.config.get("bacnet", {}).get("ip"),
              port=self.config.get("bacnet", {}).get("port"),
          )
      )

    self.discovery_manager = udmi.discovery.manager.DiscoveryManager(
        self.state,
        self.publish_discovery,
        enabled_families=enabled,
        family_kwargs=family_kwargs,
    )
    self.register_state_hook(self.discovery_manager.on_state_update_hook)
    self.components["discovery"] = self.discovery_manager
