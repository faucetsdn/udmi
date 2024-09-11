"""Framework class for"""

import abc
import atexit
import enum
from functools import wraps
import logging
import sched
import threading
import traceback
from typing import Any, Callable
import udmi.schema.config
import udmi.schema.state


def catch_exceptions_to_state(method: Callable):
  """Decorator which propogates errors up to the state.

  Args:
    method: method to wrap
  """

  @wraps(method)
  def _impl(self, *args, **kwargs):
    try:
      return method(self, *args, **kwargs)
    except Exception as err:
      self.mark_error_from_exception(err)

  return _impl


def main_task(method: Callable):
  """Decorator which marks discovery as `done` when wrapped functoin returns.

  Args:
    method: method to wrap
  """

  @wraps(method)
  def _impl(self, *args, **kwargs):
    method(self, *args, **kwargs)
    self.state.phase = states.FINISHED

  return _impl


class states(enum.StrEnum):
  # Discovery service has been initialized but is not doing anytihng
  INITIALIZED = "initialized"

  # Discovery is scheduled in the future
  PENDING = "pending"

  # Receieved the signal to start (from config block, or next interval)
  STARTING = "starting"

  # Discovery has started
  STARTED = "started"

  # Recieved the signal to cancel (e.g. from config message change)
  CANCELLING = "cancelling"

  # Discovery has been cancelled
  CANCELLED = "cancelled"

  # Discovery has finished the scan
  FINISHED = "finished"

  # Discovery has finished because the interval is over
  FINISHED_INTERVAL = "interval_finished"

  # Some error occured during processing
  ERROR = "error"


class DiscoveryController(abc.ABC):

  @property
  @abc.abstractmethod
  def scan_family(self):
    """The primary `scan_family` for this discovery component.

    E.g. ipv4, bacnet
    """
    pass

  @abc.abstractmethod
  def start_discovery(self):
    """Trigger the start of discovery.

    This method should:
      - Setup and clear any required variables, e.g. dictionaries,
      memory/previously remembered results

    This method should NOT:
      - Change the state
    """
    pass

  @abc.abstractmethod
  def stop_discovery(self):
    """Trigger stopping discovery and clean shutdown of any discovery processes, threads, etc

    This should may be called:
      - exceptions from discovery component
      - errors starting discovery
      - to stop discovery (because the config has changed)
      - stop discovery before (because interval has passed)
      - script is exiting (through atexit)cript is exiting (through atexit)

    As such, it should be safe from any malfunctioning

    This method should:
      - Return only when all subcomponents have been shut down
      - Log any relevant counters for troubleshooting
    """
    pass

  # Generation, provided by external source
  generation: None | str = None

  def __init__(self, state: udmi.schema.state.State, publisher: Callable):
    self.state = udmi.schema.state.Discovery()
    self.publisher = publisher
    state.discovery.families[self.scan_family] = self.state
    self.config = None
    self.mutex = threading.Lock()
    self.config_version = 0
    atexit.register(self._stop)

  def _status_from_exception(self, err: Exception) -> None:
    """Create state in status"""
    self.state.status = udmi.schema.state.Status(
        category="discovery.error", level=500, message=str(err)
    )

  def mark_error_from_exception(self, err: Exception) -> None:
    """Helper function to mark"""
    self.state.phase = states.ERROR
    self._status_from_exception(err)
    logging.exception(err)

  def _start(self):
    logging.info("Starting...")
    self.state.status = None
    self.state.phase = states.STARTING
    try:
      self.start_discovery()
      self.state.phase = states.STARTED
    except Exception as err:
      self.mark_error_from_exception(err)

  def scheduled_task(self, config_hash):
    with self.mutex:
      if config_version != self.config_version:
        # config has changed, return
        return

  def _stop(self):
    logging.info("Stopping discovery for %s", type(self).__name__)
    if self.state.phase not in [states.STARTING, states.STARTED]:
      logging.info("Not stopping because state was %s", self.state.phase)
      return

    self.state.status = None
    self.state.phase = states.CANCELLING
    try:
      self.stop_discovery()
      self.state.phase = states.CANCELLED
    except Exception as err:
      self.mark_error_from_exception(err)

  @catch_exceptions_to_state
  def controller(self, config_dict: None | dict[str:Any]):
    """Main discovery controller which manages discovery sub-class.

    Args:
      config_dict: Complete UDMI configuration as a dictionary
    """
    logging.info("received config")
    with self.mutex:
      discovery_config_dict = config_dict["discovery"]["families"].get(
          self.scan_family
      )
      config = (
          udmi.schema.config.DiscoveryFamily(**discovery_config_dict)
          if discovery_config_dict
          else None
      )

      # Identical config, do nothing
      if config == self.config:
        return

      # config has changed
      # Stop any running discovery
      self._stop()
      self.config_version = +1

      # Discovery config empty
      if not config and self.config:
        self.config = None

      # Config changed without a new generation
      elif self.config and self.config.generation == config.generation:
        raise RuntimeError("config changed, but generation did not change")

      # New discovery config, start a new discovery
      else:

        self.config = config
        self._start()

  def done(self):
    self.state.phase = states.FINISHED
