import abc
import copy
from datetime import datetime
from datetime import timezone
from typing import Any
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

class BasicPoint(AbstractPoint):
    """
    Abstract representation of a basic data point.
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
        return self._state.value_state

    @value_state.setter
    def value_state(self, value):
        self._state.value_state = value
        self._dirty = True

    @property
    def status(self):
        return self._state.status

    @status.setter
    def status(self, value):
        self._state.status = value
        self._dirty = True

    def update_data(self) -> None:
        """
        Updates the telemetry data from the implementation source.
        """
        if not self._written:
            self._data.present_value = self.get_value()

    def set_model(self, model: PointPointsetModel) -> None:
        """Applies static definition from Metadata."""
        if not model:
            return
        if model.units:
            self._state.units = model.units
        if model.ref:
            self._ref = model.ref
        self._dirty = True

    def set_config(self, config: PointPointsetConfig) -> None:
        """
        Set the configuration for this point, nominally to indicate writing a value.
        """
        previous_value_state = self._state.value_state
        previous_status = copy.deepcopy(self._state.status)

        self._update_state_config(config)

        state_changed = (self._state.value_state != previous_value_state)
        status_changed = (self._state.status != previous_status)

        if state_changed or status_changed:
            self._dirty = True

    def _update_state_config(self,
        config: Optional[PointPointsetConfig]) -> None:
        """
        Update the state of this point based off of a new config.
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
            self._written = False
            self._state.value_state = None
            self.update_data()
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
        except Exception as ex:
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
        try:
            result = self.set_value(config.set_value)
            self._data.present_value = result
            self._state.value_state = ValueState.applied
            self._written = True
        except Exception as ex:
            self._state.status = self._create_entry(
                Category.POINTSET_POINT_FAILURE, str(ex)
            )
            self._state.value_state = ValueState.failure

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
        """Return the current reading from the source."""
        pass

    @abc.abstractmethod
    def set_value(self, value: Any) -> Any:
        """Write the value to the source and return the applied value."""
        pass

    @abc.abstractmethod
    def validate_value(self, value: Any) -> bool:
        """Check if the value is valid for this point."""
        pass

    @abc.abstractmethod
    def _populate_enumeration(self, point: RefDiscovery) -> None:
        """Hook for subclasses to populate extra enumeration fields."""
        pass

    def enumerate(self) -> RefDiscovery:
        """
        Returns discovery information for this point.
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
        """
        if self._data.present_value is None:
            return False

        import time
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
        """
        import time
        self._last_reported_value = self._data.present_value
        self._last_reported_time = time.time()