"""Framework class for"""

import abc
import dataclasses
import atexit
import enum
import functools
import logging
import sched
import threading
import traceback
from typing import Any, Callable
import udmi.schema.config
import udmi.schema.discovery_event
import udmi.schema.state
import udmi.schema.util
import time
import copy
import datetime

# TODO: Make this not negative
MAX_THRESHOLD_GENERATION = -10 # [seconds], should be negative

RUNNER_LOOP_INTERVAL = 0.1

# TODO: Move into an enum for readability
ACTION_START = 1
ACTION_STOP = -1

def catch_exceptions_to_state(method: Callable):
  """Decorator which propogates errors up to the state.

  Args:
    method: method to wrap
  """

  @functools.wraps(method)
  def _impl(self, *args, **kwargs):
    try:
      return method(self, *args, **kwargs)
    except Exception as err:
      self._handle_exception(err)
  return _impl


def main_task(method: Callable):
  """Decorator which marks discovery as `done` when wrapped function returns.

  Args:
    method: method to wrap
  """

  @functools.wraps(method)
  def _impl(self, *args, **kwargs):
    method(self, *args, **kwargs)
    self._set_internal_state(states.FINISHED)
    self.state.phase = udmi.schema.state.Phase.stopped
    self._publish_marker()

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
  
  SCHEDULED = "scheduled"

  # Some error occured during processing
  ERROR = "error"


class DiscoveryController(abc.ABC):
  """"""
  @property
  @abc.abstractmethod
  def family(self):
    """The primary `family` for this discovery component.

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

  def __init__(self, state: udmi.schema.state.State, publisher: Callable):
    self.state = udmi.schema.state.Discovery()
    self.internal_state = None
    self.publisher = publisher
    self.config: udmi.schema.config.DiscoveryFamily = None
    self.mutex = threading.Lock()
    self.scheduler_thread = None
    self.scheduler_thread_stopped = threading.Event()
    self.generation: datetime.datetime | None  = None
    self.count_events: int = None
    self.publisher_mutex = threading.Lock()

    state.discovery.families[self.family] = self.state
    
    atexit.register(self._stop)

  def _event_counter_increment_and_get(self) -> int:
    """ Incremenets the internal event counter and returns the new value.

    Returns:
      New event count
    """
    with self.publisher_mutex:
      self.count_events = self.count_events + 1
      return self.count_events

  def _handle_exception(self, err: Exception) -> None:
    """Helper function which updates the status when an exception is caught."""
    self._set_internal_state(states.ERROR)
    self.state.status = udmi.schema.state.Status(
        category="discovery.error", level=500, message=str(err)
    )
    logging.exception(err)

  def _start(self) -> None:
    logging.info("Starting discovery for %s", type(self).__name__)
    self.state.status = None
    self._set_internal_state(states.STARTING)
    self.count_events = 0

    try:
      self._publish_marker()
      self.start_discovery()
    except Exception as err:
      self._handle_exception(err)
    else:
      self._set_internal_state(states.STARTED)
      self.state.phase = udmi.schema.state.Phase.active
      logging.info("Started... %s", type(self).__name__)

  def _stop(self):
    logging.debug("Stopping discovery for %s", type(self).__name__)
    if self.internal_state not in [states.STARTING, states.STARTED]:
      logging.debug("Not stopping because state was %s", self.internal_state)
      return
    logging.info("Stopped %s", type(self).__name__)

    self.state.status = None
    self._set_internal_state(states.CANCELLING)
    try:
      self.stop_discovery()
      self.state.phase = udmi.schema.state.Phase.stopped
      self._set_internal_state(states.CANCELLED)
    except Exception as err:
      self._handle_exception(err)

  def _publish_marker(self):
    event_no = 0 if self.internal_state == states.STARTING else -(self.count_events + 1)
    event = udmi.schema.discovery_event.DiscoveryEvent(
      generation=self.generation,
      family=self.family,
      event_no=event_no
    )
    logging.info("publishing discovery marker for %s #%d", self.family, event_no)
    self.publisher(event)

  def publish(self, event: udmi.schema.discovery_event.DiscoveryEvent):
    """ Publishes the provided Discovery Event, setting event counts."""
    event_number = self._event_counter_increment_and_get()
    event.event_no = event_number
    logging.info("publishing discovery for %s:%s #%d", event.family, event.addr, event_number)
    self.publisher(event)

  def _validate_config(config: udmi.schema.config.DiscoveryFamily):
    """ Validates that the given discovery family config is valid.

      Throws:
        RuntimeError: If the provided config is invalid
    """
    if config.scan_duration_sec > config.scan_interval_sec:
      raise RuntimeError("scan duration cannot be greater than interval")
    
    if config.scan_duration_sec < 0 or config.scan_interval_sec < 0:
      raise RuntimeError("scan duration or interval cannot be negative")

  @catch_exceptions_to_state
  def _scheduler(self, start_time:int, config: udmi.schema.config.DiscoveryFamily, initial_generation: datetime.datetime) -> None:
    """ The scheduler runs as a dedicated thread and is used to manage
    and schedule scans.

    Args:
      start_time: Time for the first scan. Will wait in the future, or start
        immediately if in the past
      
      config: A copy of the config the scan(s) were schedulde with. Sample rate
    """
    next_action_time = start_time
    
    # Initial execution of the scheduler is always to start a discovery
    # The scheduler should only be started when discovery is expected to start
    next_action = ACTION_START

    self._set_internal_state(states.SCHEDULED)
    self.state.phase = udmi.schema.state.Phase.pending

    self.set_generation(initial_generation)

    scan_interval_sec = config.scan_interval_sec if config.scan_interval_sec is not None else 0
    # Set the duration to match the interval duration so that the scheduled stop logic is simpler
    # if neither are set, this sets it to 0 (the default)
    scan_duration_sec = config.scan_duration_sec if config.scan_duration_sec is not None else scan_interval_sec
    
    while not self.scheduler_thread_stopped.is_set():
      if time.time() > next_action_time:
        with self.mutex:
          if config != self.config:
            logging.info("config has change, exiting")
            # Check that the config has not changed whilst the scheduler was waiting to acquire the lock
            # And a another scheduler thread may have started
            return

          # make a copy of the current action so next_acction can be safelty mutated
          # these are 1/-1 which do get copied (rather than referenced) by python,
          # or at least I think they do, but it does work.
          current_action = next_action
          
          if current_action == ACTION_START:
            self._start()
            
            if scan_duration_sec > 0:
              next_action = ACTION_STOP
              # Calcultae based of generation and current time, in case the generation is in the past
              # Otherwise systematic error gets introduced into all repetitive measurements
              # e.g. instead of starting "on the hour", they all start 5 seconds pst the hour
              next_action_time = time.time() + scan_duration_sec
              logging.info("scheduled discovery stop for %s in %d seconds", type(self).__name__, scan_duration_sec)
            else:
              # the scan runs indefinitely, exit the scheduler
              logging.info("%s discovery running indefinitely", type(self).__name__)
              return
            
          elif current_action == ACTION_STOP:
            self._stop()

            # If the scan is repetitive, schedule the next start
            if scan_interval_sec > 0:
              next_action = ACTION_START
              sleep_interval = scan_interval_sec - scan_duration_sec
              # calculate the next generation
              next_action_time = time.time() + sleep_interval
              logging.info("scheduled discovery start for %s in %d seconds", type(self).__name__, scan_duration_sec)
              self._set_internal_state(states.SCHEDULED)
              self.state.phase = udmi.schema.state.Phase.pending
            else:
              # The scan is not repetitive, exit the scheduler
              return
          
      time.sleep(RUNNER_LOOP_INTERVAL)


  @catch_exceptions_to_state
  def controller(self, config_dict: None | dict[str:Any]):
    """Main discovery controller.

    Primary function is to receieve a config message and responds accordingly.
    Stops discovery scans as neccesary, and if discovery is demanded, 
    determines when the first scan should start
    and starts the scheduler with that information.

    Args:
      config_dict: Complete UDMI configuration as a dictionary
    """

    logging.debug("received config %s", config_dict)
    with self.mutex:
      try:
        discovery_config_dict = config_dict["discovery"]["families"][self.family]
      except KeyError as err:
        # `self.family`` is not in the config message
        # Stop discovery and clear state
        self._stop()
        self.config = None
        self._reset_udmi_state()
        return
     
      # Create a new config dict (always new)
      config = (
          udmi.schema.config.DiscoveryFamily(**discovery_config_dict)
          if discovery_config_dict
          else None
      )

      # Identical config, do nothing
      if config == self.config:
        logging.debug("identical config, doing nothing")
        return

      # config has changed
      # Stop any running discovery and scheduled discovery
      self.config = config
      self._stop()
      
      # Stop discovery scheduler if it exists
      if self.scheduler_thread:
        self.scheduler_thread_stopped.set()
        self.scheduler_thread.join()
      
      # Discovery config empty
      if not config:
        # Note, the state does not get cleared, but it should be accurate
        # e.g. phase of `stopped` and a count of messages
        logging.debug("config is now empty, do nothing")
        return
      
      self._reset_udmi_state()

      generation_from_config = udmi.schema.util.datetime_from_iso_timestamp(config.generation)
      time_delta_from_now = generation_from_config - datetime.datetime.now(tz=datetime.timezone.utc)
      seconds_from_now = time_delta_from_now.total_seconds()

      if seconds_from_now >= MAX_THRESHOLD_GENERATION:
        # Generation is in the future or witihn tolerance
        initial_generation = generation_from_config
        scheduled_start = generation_from_config.timestamp()
  
      elif seconds_from_now < MAX_THRESHOLD_GENERATION and config.scan_interval_sec:
        # Generation is in the past, but it has interval
        # Use modular arithemtic to determine when the start time is
        cycles_elapsed, seconds_into_cycle = divmod(abs(seconds_from_now), config.scan_interval_sec)

        # determine whether to join this cycle or wait till the next
        cycle_modifer = 1 if seconds_into_cycle > abs(MAX_THRESHOLD_GENERATION) else 0

        initial_generation = generation_from_config + datetime.timedelta(seconds=config.scan_interval_sec * (cycle_modifer + cycles_elapsed))
        scheduled_start = initial_generation.timestamp()

      elif seconds_from_now < MAX_THRESHOLD_GENERATION:
        raise RuntimeError(f"generation start time ({seconds_from_now} from now exceeds allowable threshold {MAX_THRESHOLD_GENERATION})")
      
      logging.info(f"discovery {config} starts in {scheduled_start - time.time()} seconds")

      # Discovery is go

      self.scheduler_thread_stopped.clear()
      self.scheduler_thread = threading.Thread(
          target=self._scheduler, args=[scheduled_start, copy.copy(config), initial_generation], daemon=True
      )
      self.scheduler_thread.start()


  def _set_internal_state(self, new_state: states) -> None:
    """ Sets the internal state to the given state and logs the new state.

    Arguments:
      new_state

    """
    logging.info("state now %s, was %s", new_state, self.internal_state)
    self.internal_state = new_state

  def _reset_udmi_state(self) -> None:
    """ Resets the UDMI state for this family by nulling all keys. """
    for field in dataclasses.fields(self.state):
      setattr(self.state, field.name, None)

  def on_state_update_hook(self) -> None:
    # Check that the state is not reset before setting the active count
    if self.state.phase is not None:
      self.state.active_count = self.count_events

  def set_generation(self, new_generation:datetime.datetime) -> None:
    """Updates the generation to the the given generation"""
    current_generation = udmi.schema.util.datetime_serializer(self.generation) if self.generation else None
    logging.info("generation now %s, was %s", 
                 current_generation,
                 udmi.schema.util.datetime_serializer(new_generation))
    self.generation = new_generation
    self.state.generation = new_generation

  def __del__(self):
    self._stop()
