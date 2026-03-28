"""Basic point definition."""
import abc
import copy
import threading
import time
from datetime import datetime
from datetime import timezone
from typing import Any
from typing import Callable
from typing import Optional

from udmi.core.managers.point.abstract_point import AbstractPoint
from udmi.schema import Category
from udmi.schema import Entry
from udmi.schema import PointPointsetConfig
from udmi.schema import PointPointsetEvents
from udmi.schema import PointPointsetModel
from udmi.schema import PointPointsetState
from udmi.schema import ValueState
from udmi.schema import RefDiscovery

DEFAULT_HEARTBEAT_SEC = 600

class BasicPoint(AbstractPoint): # pylint: disable=too-many-instance-attributes
    """
    Abstract representation of a basic data point.

    Provides the foundational boilerplate logic for a point, including state management,
    dirty flag handling, validation, and Change of Value (COV) calculations. Developers
    subclass this to implement hardware-specific get_value and set_value mechanics.

    State Machine Details:
    - Writable values move through states: None -> applied, invalid, or failure based on config.
    - Dirty State: The _dirty flag is set when config, value_state, or status changes,
      triggering the PointsetManager to regenerate the global device state and state_etag.
    - Reporting State: Tracks _last_reported_value and _last_reported_time to accurately
      execute COV and periodic heartbeat reporting logic.
    """

    def __init__(self, name: str, model: Optional[PointPointsetModel] = None):
        self._name = name
        self._data = PointPointsetEvents()
        self._state = PointPointsetState()

        self._writable = model.writable if (model and model.writable) else False
        self._ref = model.ref if model else None

        if model and model.units:
            self._state.units = model.units

        self._written = False
        self._dirty = True

        self._expiry_time: Optional[float] = None

        # Store full model for subclass extensibility
        self._model = model

        # COV and Heartbeat
        self._cov_increment: Optional[float] = None
        self._last_reported_value: Any = None
        self._last_reported_time: float = 0.0

    def get_name(self) -> str:
        return self._name

    def get_data(self) -> PointPointsetEvents:
        return self._data

    def is_dirty(self) -> bool:
        return self._dirty

    def get_state(self) -> PointPointsetState:
        self._dirty = False
        return self._state

    @property
    def value_state(self):
        """Gets value state."""
        return self._state.value_state

    @value_state.setter
    def value_state(self, value):
        """Sets value state."""
        self._state.value_state = value
        self._dirty = True

    @property
    def status(self):
        """Gets status."""
        return self._state.status

    @status.setter
    def status(self, value):
        """Sets status."""
        self._state.status = value
        self._dirty = True

    def update_data(self) -> None:
        """
        Updates the telemetry data from the implementation source.
        Calls the abstract get_value method to refresh the point's data payload,
        unless a writeback is currently active (in which case it retains the written value).
        """
        if not self._written:
            self._data.present_value = self.get_value()

    def set_model(self, model: PointPointsetModel) -> None:
        """
        Applies static definition from Metadata.
        Initializes point properties (like units or ref overrides) during device startup
        and flags the point as dirty so the State is regenerated.
        """
        if not model:
            return
        if model.units:
            self._state.units = model.units
        if model.ref:
            self._ref = model.ref
        self._dirty = True

    def clear_writeback(self) -> None:
        """
        Clears the writeback state and reverts to base state.
        Handles the expiration of a set_value_expiry timer by wiping the overriden value
        state and forcing an immediate re-read from the underlying hardware.
        """
        self._written = False
        self._state.value_state = None
        self._state.status = None
        self._expiry_time = None
        self.update_data()
        self._dirty = True

    def set_config(self, config: PointPointsetConfig, **kwargs: 'Any') -> None:
        """
        Set the configuration for this point, nominally to indicate writing a value.
        Entrypoint for the PointsetManager to push config updates or writebacks down to the point.
        Checks if the payload changed and manages the _dirty flag.
        """
        previous_value_state = self._state.value_state
        previous_status = copy.deepcopy(self._state.status)

        invalid_expiry = kwargs.get('invalid_expiry', False)
        is_expired = kwargs.get('is_expired', False)
        on_state_change = kwargs.get('on_state_change')

        self._update_state_config(config, invalid_expiry, is_expired, on_state_change)

        state_changed = self._state.value_state != previous_value_state
        status_changed = self._state.status != previous_status

        if state_changed or status_changed:
            self._dirty = True

    def _update_state_config(self,
        config: Optional[PointPointsetConfig],
        invalid_expiry: bool = False,
        is_expired: bool = False,
        on_state_change: Optional[Callable] = None) -> None:
        # pylint: disable=too-many-return-statements
        """
        Update the state of this point based off of a new config.
        The core writeback state machine logic. Validates ref matching, expiry timestamps,
        writeability, and delegates to the hardware validation logic. Executes hardware
        actuation asynchronously, immediately transitioning into `updating` state, and
        bubbles up Entries once `applied` or `failure` state is reached.
        """
        self._state.status = None

        if config:
            if config.cov_increment is not None:
                self._cov_increment = config.cov_increment
            if config.units is not None:
                self._state.units = config.units

        # 1. Validate Ref
        if config is not None and config.ref != self._ref:
            self._state.status = self._create_entry(
                Category.POINTSET_POINT_FAILURE, "Invalid point ref"
            )
            return

        # 2. Check if set_value is present (Release/Null check)
        if config is None or config.set_value is None:
            self.clear_writeback()
            return

        if invalid_expiry:
            self.clear_writeback()
            self._state.status = self._create_entry(
                Category.POINTSET_POINT_INVALID, "Invalid or missing set_value_expiry"
            )
            self._state.value_state = ValueState.invalid
            return

        if is_expired:
            self.clear_writeback()
            return

        # 3. Validate Value
        try:
            if not self.validate_value(config.set_value):
                self._state.status = self._create_entry(
                    Category.POINTSET_POINT_INVALID,
                    "Written value is not valid"
                )
                self._state.value_state = ValueState.invalid
                return
        except Exception as ex:  # pylint: disable=broad-exception-caught
            self._state.status = self._create_entry(
                Category.POINTSET_POINT_FAILURE, str(ex)
            )
            self._state.value_state = ValueState.failure
            return

        # 4. Check Writable
        if not self._writable:
            self._state.status = self._create_entry(
                Category.POINTSET_POINT_FAILURE, "Point is not writable"
            )
            self._state.value_state = ValueState.failure
            return

        # 5. Apply Value
        def execute_writeback(val: Any) -> None:
            try:
                result = self.set_value(val)
                self._data.present_value = result
                self.value_state = ValueState.applied
                self._written = True
            except Exception as ex:  # pylint: disable=broad-exception-caught
                self.status = self._create_entry(
                    Category.POINTSET_POINT_FAILURE, str(ex)
                )
                self.value_state = ValueState.failure
            finally:
                if on_state_change:
                    on_state_change()

        self._state.value_state = ValueState.updating
        thread = threading.Thread(target=execute_writeback, args=(config.set_value,))
        thread.daemon = True
        thread.start()

    def _create_entry(self, category: Category, message: str) -> Entry:
        """Helper to create a status Entry object."""
        entry = Entry()
        entry.detail = f"Point {self._name} (writable {self._writable})"
        entry.timestamp = datetime.now(timezone.utc).isoformat()
        entry.message = message
        entry.category = category
        entry.level = category.level
        return entry

    @abc.abstractmethod
    def get_value(self) -> Any:
        """
        Return the current reading from the source.
        Abstract method where subclasses implement their read operations (IO, GPIO, Modbus, etc).
        """

    @abc.abstractmethod
    def set_value(self, value: Any) -> Any:
        """
        Write the value to the source and return the applied value.
        Abstract method where subclasses implement their hardware actuation logic.
        Should return the value that was actually confirmed by the hardware.
        """

    @abc.abstractmethod
    def validate_value(self, value: Any) -> bool:
        """
        Check if the value is valid for this point.
        Abstract hook for subclasses to reject writebacks (e.g., bounds checking) before
        an attempt to write to hardware is made.
        """

    @abc.abstractmethod
    def _populate_enumeration(self, point: RefDiscovery) -> None:
        """Hook for subclasses to populate extra enumeration fields."""

    def enumerate(self) -> RefDiscovery:
        """
        Returns discovery information for this point.
        Aggregates subclass-specific data with base properties to compile the Discovery payload.
        """
        point = RefDiscovery()
        point.description = f"{self.__class__.__name__} {self.get_name()}"
        point.writable = True if self._writable else None
        if self._state.units:
            point.units = self._state.units
        if self._ref:
            point.ref = self._ref
        self._populate_enumeration(point)
        return point

    def should_report(self, sample_rate_sec: int) -> bool:
        """
        Determines if this point needs to be reported based on Change of Value (COV)
        and Heartbeat logic.
        Compares the current present_value against the _last_reported_value to invoke COV
        reporting, otherwise falls back to evaluating the _last_reported_time against the 
        heartbeat interval.
        """
        if self._data.present_value is None:
            return False

        now = time.time()

        if self._last_reported_value is None:
            return True

        should_report_cov = False

        if self._data.present_value != self._last_reported_value:
            is_numeric = (isinstance(self._data.present_value, (int, float)) and
                          not isinstance(self._data.present_value, bool) and
                          isinstance(self._last_reported_value, (int, float)) and
                          not isinstance(self._last_reported_value, bool))

            if is_numeric and self._cov_increment is not None:
                delta = abs(self._data.present_value - self._last_reported_value)
                # Ensure we handle casting just in case
                if delta >= self._cov_increment:
                    should_report_cov = True
            else:
                should_report_cov = True

        if should_report_cov:
            return True

        # Default heartbeat or sample rate
        heartbeat_interval = sample_rate_sec if sample_rate_sec > 0 else DEFAULT_HEARTBEAT_SEC
        if (now - self._last_reported_time) >= heartbeat_interval:
            return True

        return False

    def mark_reported(self) -> None:
        """
        Updates the reporting state after a successful publish.
        Caches the last reported value and timestamp to reset COV and Heartbeat state machines.
        """
        self._last_reported_value = self._data.present_value
        self._last_reported_time = time.time()
