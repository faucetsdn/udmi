"""Framework class for discovery scan controllers.

Each `DiscoveryController` instance represents exactly one scan (either a
legacy `discovery.families.<family>` entry or a named `discovery.scans.<name>`
entry). Instances are created and owned by `udmi.discovery.manager`.
"""

import abc
import collections
import dataclasses
import enum
import functools
import logging
import threading
from typing import Callable
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


class ScanExclusionError(RuntimeError):
  """Raised when an exclusive scan cannot start because another scan of the
  same family currently holds the family's permit."""


class _ScanLogAdapter(logging.LoggerAdapter):
  """Prefixes every log line with the scan id, e.g. `[scans/nightly_ports]`."""

  def process(self, msg, kwargs):
    return f"[{self.extra['scan_id']}] {msg}", kwargs


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
    failed = False
    try:
      method(self, *args, **kwargs)
      self._publish_marker()
    except Exception:
      failed = True
      raise
    finally:
      if not failed and self.internal_state not in (states.CANCELLING, states.CANCELLED):
        self._set_internal_state(states.FINISHED)
      self.state.phase = udmi.schema.state.Phase.stopped
      self._release_exclusive()

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
  """Base class for a single discovery scan."""

  # Subclasses set True when at most one scan of this family may run at a time
  # (e.g. bacnet, which shares a single BAC0 stack and its discoveredDevices
  # table). Enforced across ALL instances of the family, legacy and named.
  exclusive: bool = False

  # family -> Semaphore(1); process-wide, keyed by family so legacy and named
  # scans of the same family contend for the same permit.
  _exclusive_permits: dict[str, threading.Semaphore] = collections.defaultdict(
      lambda: threading.Semaphore(1)
  )
  # family -> scan_id currently holding the permit
  _exclusive_owner: dict[str, str] = {}
  # guards the two dicts above
  _exclusive_registry_lock = threading.Lock()

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

  def __init__(
      self,
      state_entry: udmi.schema.state.Discovery,
      publisher: Callable,
      *,
      scan_id: str,
  ):
    """
    Args:
      state_entry: the Discovery state dataclass this scan reports into. The
        caller (DiscoveryManager) decides whether that lives under
        state.discovery.families[<family>] or state.discovery.scans[<name>].
      publisher: callable accepting a DiscoveryEvent
      scan_id: human readable id used in logs, e.g. "families/ether" or
        "scans/nightly_ports"
    """
    self.state = state_entry
    self.scan_id = scan_id
    self.log = _ScanLogAdapter(
        logging.getLogger(type(self).__module__), {"scan_id": scan_id}
    )
    self.internal_state = None
    self.publisher = publisher
    self.config: udmi.schema.config.DiscoveryFamily | None = None
    self.mutex = threading.Lock()
    self.scheduler_thread = None
    self.scheduler_thread_stopped = threading.Event()
    self.generation: datetime.datetime | None  = None
    self.count_events: int = None
    self.publisher_mutex = threading.Lock()
    self._holds_exclusive = False

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
    self.state.phase = udmi.schema.state.Phase.stopped
    self.state.status = udmi.schema.state.Status(
        category="discovery.error", level=500, message=str(err)
    )
    self.log.exception(err)

  def _acquire_exclusive(self) -> None:
    """Non-blocking acquisition of the family permit for exclusive scans.

    Raises:
      ScanExclusionError: if another scan of this family holds the permit.
    """
    if not self.exclusive:
      return
    with DiscoveryController._exclusive_registry_lock:
      permit = DiscoveryController._exclusive_permits[self.family]
      if not permit.acquire(blocking=False):
        owner = DiscoveryController._exclusive_owner.get(self.family, "unknown")
        raise ScanExclusionError(
            f"{self.family} scan already running as {owner}; this generation is skipped"
        )
      DiscoveryController._exclusive_owner[self.family] = self.scan_id
      self._holds_exclusive = True

  def _release_exclusive(self) -> None:
    """Returns the family permit if this instance holds it. Idempotent."""
    with DiscoveryController._exclusive_registry_lock:
      if not self._holds_exclusive:
        return
      self._holds_exclusive = False
      DiscoveryController._exclusive_owner.pop(self.family, None)
      DiscoveryController._exclusive_permits[self.family].release()

  def _start(self) -> None:
    self.log.info("starting discovery")
    self.state.status = None
    self._set_internal_state(states.STARTING)
    self.count_events = 0

    try:
      self._acquire_exclusive()
    except ScanExclusionError as err:
      self._set_internal_state(states.ERROR)
      self.state.status = udmi.schema.state.Status(
          category="discovery.error", level=500, message=str(err)
      )
      self.state.phase = udmi.schema.state.Phase.stopped
      self.log.warning("start rejected: %s", err)
      return

    try:
      self._publish_marker()
      self.start_discovery()
    except Exception as err:
      try:
        self.stop_discovery()
      except Exception:
        self.log.exception("error cleaning up after failed start_discovery")
      self._release_exclusive()
      self._handle_exception(err)
    else:
      self._set_internal_state(states.STARTED)
      self.state.phase = udmi.schema.state.Phase.active
      self.log.info("started")

  def _stop(self):
    """Stops a running scan. Silent no-op when nothing is running."""
    if self.internal_state not in [states.STARTING, states.STARTED]:
      return
    was = self.internal_state
    self.log.info("stopping discovery (was %s)", was)
    started = time.monotonic()

    self.state.status = None
    self._set_internal_state(states.CANCELLING)
    try:
      self.stop_discovery()
      self.state.phase = udmi.schema.state.Phase.stopped
      self._set_internal_state(states.CANCELLED)
      self.log.info("stopped (was %s) in %.1fs", was, time.monotonic() - started)
    except Exception as err:
      self._handle_exception(err)
    finally:
      self._release_exclusive()

  def _publish_marker(self):
    event_no = 0 if self.internal_state == states.STARTING else -(self.count_events + 1)
    event = udmi.schema.discovery_event.DiscoveryEvent(
      generation=self.generation,
      family=self.family,
      event_no=event_no
    )
    self.log.debug("publishing discovery marker for %s #%d", self.family, event_no)
    self.publisher(event)

  def publish(self, event: udmi.schema.discovery_event.DiscoveryEvent):
    """ Publishes the provided Discovery Event, setting event counts."""
    event_number = self._event_counter_increment_and_get()
    event.event_no = event_number
    self.log.debug("publishing discovery for %s:%s #%d", event.family, event.addr, event_number)
    self.publisher(event)

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
        # Never block indefinitely on the mutex: apply_config() holds it while
        # joining this thread, so an unbounded acquire here would deadlock.
        if not self.mutex.acquire(timeout=RUNNER_LOOP_INTERVAL):
          continue
        try:
          if config != self.config:
            self.log.info("config has changed, exiting scheduler")
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
              # Calculate based of generation and current time, in case the generation is in the past
              # Otherwise systematic error gets introduced into all repetitive measurements
              # e.g. instead of starting "on the hour", they all start 5 seconds pst the hour
              scheduled_stop_time = self.generation + datetime.timedelta(seconds=scan_duration_sec)
              next_action_time = scheduled_stop_time.timestamp()
              self.log.info("scheduled discovery stop in %d seconds", scan_duration_sec)
            else:
              # the scan runs indefinitely, exit the scheduler
              self.log.info("discovery running indefinitely")
              return
            
          elif current_action == ACTION_STOP:
            self._stop()

            # If the scan is repetitive, schedule the next start
            if scan_interval_sec > 0:
              next_action = ACTION_START
              next_generation = self.generation + datetime.timedelta(seconds=scan_interval_sec)
              next_action_time = next_generation.timestamp()
              self.set_generation(next_generation)
              self.log.info("scheduled discovery start in %d seconds", scan_interval_sec)
              self._set_internal_state(states.SCHEDULED)
              self.state.phase = udmi.schema.state.Phase.pending
            else:
              # The scan is not repetitive, exit the scheduler
              return
        finally:
          self.mutex.release()
          
      time.sleep(RUNNER_LOOP_INTERVAL)


  @catch_exceptions_to_state
  def apply_config(self, config: udmi.schema.config.DiscoveryFamily | None) -> None:
    """Applies a (possibly unchanged) scan config.

    No-op if identical to the current config. Otherwise stops any running or
    scheduled scan and, if `config` is not None, determines when the first
    scan should start and starts the scheduler with that information.

    Called by DiscoveryManager under the manager lock.

    Args:
      config: parsed scan config, or None to stop the scan.
    """
    with self.mutex:
      # Identical config, do nothing (silently: the manager already logs a
      # per-config summary, and idle scans must not add noise)
      if config == self.config:
        return

      # config has changed
      # Stop any running discovery and scheduled discovery
      self.config = None
      self._stop_scan_and_scheduler()
      
      # Discovery config empty
      if not config:
        # Note, the state does not get cleared, but it should be accurate
        # e.g. phase of `stopped` and a count of messages
        self.log.info("config cleared, scan stopped")
        return
      
      self.log.info("config changed: %s", config)
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
      
      self.config = config
      self.log.info("discovery starts in %.1f seconds", scheduled_start - time.time())

      # Discovery is go

      self.scheduler_thread_stopped.clear()
      self.scheduler_thread = threading.Thread(
          target=self._scheduler, args=[scheduled_start, copy.copy(config), initial_generation], daemon=True
      )
      self.scheduler_thread.start()

  def _stop_scan_and_scheduler(self) -> None:
    """Stops a running scan and joins the scheduler thread. Caller holds mutex."""
    self._stop()
    if self.scheduler_thread:
      self.scheduler_thread_stopped.set()
      self.scheduler_thread.join()
      self.scheduler_thread = None

  def shutdown(self) -> None:
    """Unconditionally stops any running or scheduled scan and clears the config."""
    with self.mutex:
      self.config = None
      self._stop_scan_and_scheduler()

  def _set_internal_state(self, new_state: states) -> None:
    """ Sets the internal state to the given state and logs the new state.

    Arguments:
      new_state

    """
    self.log.info("state now %s, was %s", new_state, self.internal_state)
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
    self.log.info("generation now %s, was %s", 
                 udmi.schema.util.datetime_serializer(new_generation),
                 current_generation)
    self.generation = new_generation
    self.state.generation = new_generation
