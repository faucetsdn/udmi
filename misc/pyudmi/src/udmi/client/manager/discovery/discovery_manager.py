import abc
import atexit
import copy
import datetime
import functools
import logging
import threading
import time
from enum import Enum
from typing import Callable

from udmi_schema.schema.config_discovery import FamilyDiscoveryConfig
from udmi_schema.schema.entry import Entry
from udmi_schema.schema.events_discovery import DiscoveryEvents
from udmi_schema.schema.state import State
from udmi_schema.schema.state_discovery_family import (
    FamilyDiscoveryState,
    Phase
)

from udmi.util.status import Status

ACTION_START = 1
ACTION_STOP = -1
RUNNER_LOOP_INTERVAL = 0.1
MAX_THRESHOLD_GENERATION = -10  # [seconds], should be negative


def catch_exceptions_to_status(method: Callable):
    """
    Decorator which propagates errors up to the internal status.
    :param method: method to wrap
    :return:
    """

    @functools.wraps(method)
    def _impl(self, *args, **kwargs):
        try:
            return method(self, *args, **kwargs)
        except Exception as err:
            self._handle_exception(err)

    return _impl


def mark_task_complete_on_return(method: Callable):
    """
    Decorator which marks discovery as `done` when wrapped function returns.
    :param method: method to wrap
    :return:
    """

    @functools.wraps(method)
    def _impl(self, *args, **kwargs):
        method(self, *args, **kwargs)
        self._set_internal_status(Status.FINISHED)
        self.state.phase = Phase.stopped

    return _impl


def _validate_discovery_config(config: FamilyDiscoveryConfig):
    """
    Validates that the config is valid
    Throws:
      RuntimeError: If the provided config is invalid
    :param config: FamilyDiscoveryConfig
    :return:
    """
    if config.scan_duration_sec > config.scan_interval_sec:
        raise RuntimeError("scan duration cannot be greater than interval")

    if config.scan_duration_sec < 0 or config.scan_interval_sec < 0:
        raise RuntimeError("scan duration or interval cannot be negative")


class DiscoveryManager(abc.ABC):

    @property
    @abc.abstractmethod
    def scan_family(self):
        """
        The primary `scan_family` for this discovery component e.g. ipv4, bacnet
        :return:
        """

    @abc.abstractmethod
    def start_discovery(self) -> None:
        """
        Trigger the start of discovery.

        This method should:
        - Setup and clear any required variables, e.g. dictionaries,
        memory/previously remembered results

        This method should NOT:
        - Change the state
        :return:
        """

    @abc.abstractmethod
    def stop_discovery(self) -> None:
        """
        Trigger stopping discovery and clean shutdown of any discovery
        processes, threads, etc

        This should be called:
          - exceptions from discovery component
          - errors starting discovery
          - to stop discovery (because the config has changed)
          - stop discovery before (because interval has passed)
          - script is exiting (through `atexit`)
        As such, it should be safe from any malfunctioning.

        This method should:
          - Return only when all subcomponents have been shut down
          - Log any relevant counters for troubleshooting
          :return:
        """

    def __init__(self, state: State, publisher: Callable):
        self.state = FamilyDiscoveryState()
        self.internal_status = None
        self.publisher = publisher

        # TODO: Check if below assignment is required and why.
        state.discovery.families[self.scan_family] = self.state

        self.config = None
        self.mutex = threading.Lock()
        self.scheduler_thread = None
        self.scheduler_thread_stopped = threading.Event()
        self.generation = None
        self.count_events = None
        self.publisher_mutex = threading.Lock()
        atexit.register(self._stop)

    def _increment_event_counter_and_get(self) -> int:
        """
        Increments the internal event counter and returns the new value.
        :return: New event count
        """
        with self.publisher_mutex:
            self.count_events = self.count_events + 1
            return self.count_events

    def _set_internal_status(self, new_status: Status):
        """
        Sets the internal state to the given state.
        :param new_status:
        :return:
        """
        logging.info("status set to %s, was %s", new_status,
                     self.internal_status)
        self.internal_status = new_status

    def _handle_exception(self, err: Exception) -> None:
        """
        Helper function which updates the status when an exception is caught.
        :return:
        """
        self._set_internal_status(Status.ERROR)
        self.state.status = Entry(category="device.discovery.error", level=500,
                                  detail=str(err))
        logging.exception(err)

    def _start(self):
        """
        Start discover scan.
        :return:
        """
        logging.info(f"Starting discovery for {self.__class__.__name__}")
        self.state.status = None
        self._set_internal_status(Status.STARTING)
        self.count_events = 0
        self.generation = datetime.datetime.now()
        self.state.generation = self.generation
        try:
            self.start_discovery()
        except Exception as err:
            self._handle_exception(err)
        else:
            self._set_internal_status(Status.STARTED)
            self.state.phase = Phase.active
            logging.info(f"Started... {self.__class__.__name__}")

    def _stop(self):
        """
        Stop discovery scan.
        :return:
        """
        logging.info(f"Stopping discovery for {self.__class__.__name__}")
        if self.internal_status not in [Status.STARTING, Status.STARTED]:
            logging.info(
                f"Cannot stop because status was {self.internal_status}")
            return

        logging.info(f"Attempting stop for {self.__class__.__name__}")
        self.state.status = None
        self._set_internal_status(Status.CANCELLING)
        try:
            self.stop_discovery()
            self.state.phase = Phase.stopped
            self._set_internal_status(Status.CANCELLED)
            logging.info(f"Stopped... {self.__class__.__name__}")
        except Exception as err:
            self._handle_exception(err)

    def publish(self, event: DiscoveryEvents):
        """
        Publishes the provided Discovery Event, setting event counts.
        :return:
        """
        event_number = self._increment_event_counter_and_get()
        event.event_no = event_number
        logging.warning(
            f"published discovery for {event.scan_family}:{event.scan_addr} "
            f"#{event_number}")
        self.publisher(event)

    def _reset_udmi_state(self):
        """
        Resets the UDMI state for this family by setting all keys as null.
        :return:
        """
        for field_name in self.state.__fields__:
            setattr(self.state, field_name, None)

    def on_state_update_hook(self):
        """
        TODO: Figure out where this needs to be used
        Check that the state is not reset before setting the active count
        :return:
        """
        if self.state.phase is not None:
            self.state.active_count = self.count_events

    @catch_exceptions_to_status
    def _scheduler(self, start_time: int, config: FamilyDiscoveryConfig):
        """
        TODO: Check how scheduler and manager can be decoupled
        The scheduler thread.
        :param start_time:
        :param config:
        :return:
        """
        # Initial execution of the scheduler is always to start a discovery
        next_action_time = start_time
        next_action = ACTION_START

        self._set_internal_status(Status.SCHEDULED)
        self.state.phase = Phase.pending
        self.state.generation = config.generation

        scan_interval_sec = config.scan_interval_sec if \
            config.scan_interval_sec is not None else 0

        # Set the duration to match the interval duration so that the scheduled
        # stop logic is simpler
        # if neither are set, this sets it to 0 (the default)
        scan_duration_sec = config.scan_duration_sec if \
            config.scan_duration_sec is not None else scan_interval_sec

        while not self.scheduler_thread_stopped.is_set():
            if time.monotonic() > next_action_time:
                with self.mutex:
                    if config != self.config:
                        # Check that the config has not changed whilst the
                        # scheduler was waiting to acquire the lock
                        logging.info("config has change, exiting")
                        return

                    # make a copy of the current action so next_action can be
                    # safely mutated
                    current_action = next_action

                    if current_action == ACTION_START:
                        self._start()

                        if scan_duration_sec > 0:
                            next_action = ACTION_STOP
                            next_action_time = (time.monotonic() +
                                                scan_duration_sec)
                            logging.info(
                                f"scheduled discovery stop for "
                                f"{self.__class__.__name__} in "
                                f"{scan_duration_sec} seconds")
                        else:
                            # the scan runs indefinitely, exit the scheduler
                            logging.info(
                                f"{self.__class__.__name__} discovery running "
                                f"indefinitely")
                            return

                    elif current_action == ACTION_STOP:
                        self._stop()

                        # If the scan is repetitive, schedule the next start
                        if scan_interval_sec > 0:
                            next_action = ACTION_START
                            sleep_interval = (scan_interval_sec -
                                              scan_duration_sec)
                            next_action_time = time.monotonic() + sleep_interval
                            logging.info(
                                f"scheduled discovery start for "
                                f"{self.__class__.__name__} in "
                                f"{scan_duration_sec} seconds")
                            self._set_internal_status(Status.SCHEDULED)
                            self.state.phase = Phase.pending
                        else:
                            # The scan is not repetitive, exit the scheduler
                            return

            time.sleep(RUNNER_LOOP_INTERVAL)

    @catch_exceptions_to_status
    def apply_discovery_config(self, config_dict):
        """
        TODO: Find a better name for this method
        :param config_dict: Complete UDMI configuration as a dictionary
        :return:
        """
        logging.debug(f"received config {config_dict}")

        with self.mutex:
            try:
                discovery_config_dict = config_dict.get(
                    "discovery").get("families").get(self.scan_family)
            except KeyError as err:
                # `self.scan_family`` is not in the config message
                # Stop discovery and clear state
                self._stop()
                self.config = None
                self._reset_udmi_state()
                return

            # Create a new config dict (always new)
            config = FamilyDiscoveryConfig(
                **discovery_config_dict) if discovery_config_dict else None

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
                logging.debug("config is now empty, do nothing")
                return

            self._reset_udmi_state()
            generation_from_config = datetime.datetime.strptime(
                f"{config.generation}+0000", "%Y-%m-%dT%H:%M:%SZ%z")
            time_delta_from_now = \
                (generation_from_config -
                 datetime.datetime.now(tz=datetime.timezone.utc))
            seconds_from_now = time_delta_from_now.total_seconds()

            if seconds_from_now < MAX_THRESHOLD_GENERATION:
                raise RuntimeError(
                    f"generation start time ({seconds_from_now} from now "
                    f"exceeds allowable threshold {MAX_THRESHOLD_GENERATION})")
            logging.info(
                f"discovery {config} starts in {seconds_from_now} seconds")

            self.scheduler_thread_stopped.clear()
            self.scheduler_thread = threading.Thread(
                target=self._scheduler,
                args=[time.monotonic() + seconds_from_now, copy.copy(config)],
                daemon=True
            )
            self.scheduler_thread.start()
