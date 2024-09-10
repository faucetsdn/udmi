import logging
import mqtt as mqtt
import threading
import queue
import schema.discovery_event
import schema.state
import schema.util
import scapy.layers.inet
import scapy.all
import scapy.sendrecv
import time
import socket
import functools
import dataclasses
import json
import schema.util
import ipaddress
from typing import Callable

class UDMI:
  """UDMI Client."""
  
  _state_monitor_interval = 1 # [s]

  def __init__(self, *, mqtt_client: mqtt.MQTTClient, device_id: str):
    self.client = mqtt_client
    self.device_id = device_id
    self.state = schema.state.State()
    self.callbacks = {} # lambda, 

    threading.Thread(target=self.state_monitor, args=[], daemon=True).start()

  def process_config(self, config: str):
    logging.error(f"config callback {config[:24]}")

  #NOTE: This is flawed, because if the predicate starts to fail, for example if the block was removed, then
  # the new config is never pased to the the config. Rather, functions should always receive configs
  def add_config_route(self, filter: Callable, destination: Callable):
    self.callbacks[filter] = destination

  def config_handler(self, config_string: str):

    try:
      config = json.loads(config_string)
      logging.info("received config %s", config["timestamp"])
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
    self.state.system.status = schema.state.Status(category="system.config.apply", level=500, message=str(err))

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
    logging.info("published state: %s", state)
    self.client.publish_message(f"/devices/{self.device_id}/state", state)
  
  def publish_discovery(self, payload):
    logging.warning("published discovery: %s", payload.to_json())
    self.client.publish_message(f"/devices/{self.device_id}/events/discovery", payload.to_json())

