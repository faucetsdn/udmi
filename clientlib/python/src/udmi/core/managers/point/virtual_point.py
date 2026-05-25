"""Virtual point definition."""
import warnings
from typing import Any
from typing import Optional

from udmi.core.managers.point.basic_point import BasicPoint
from udmi.schema import RefDiscovery


class Point(BasicPoint):
    """
    Default concrete implementation of a Point.
    Acts as an in-memory "virtual" point that simply stores and retrieves a present value.
    Useful for testing, simulations, and software-only variables where no hardware integration
    is needed.

    - Maintains a local _present_value.
    - Inherits COV and heartbeat state machine from BasicPoint.
    - Writebacks instantly apply to the in-memory value without hardware IO operations.
    """

    # pylint: disable=too-many-arguments
    def __init__(self,
                 name: str,
                 model: Optional[Any] = None,
                 *,
                 writable: Optional[bool] = None,
                 ref: Optional[str] = None,
                 units: Optional[str] = None,
                 baseline_value: Optional[Any] = None):
        super().__init__(name, model=model, writable=writable, ref=ref, units=units)
        if model is not None:
            self._present_value = getattr(model, 'baseline_value', None)
        else:
            self._present_value = baseline_value

    def get_value(self) -> Any:
        """
        Overrides the abstract get_value to return the in-memory _present_value.
        """
        return self._present_value

    def set_value(self, value: Any) -> Any:
        """
        Concrete implementation of set_value, returning the value as applied.
        Accepts the set_value command from the cloud and applies it directly to the 
        in-memory state.
        """
        return value

    def validate_value(self, value: Any) -> bool:
        """
        Default validation that takes all values as valid.
        Since this is a virtual point, it places no restrictions on the type or format 
        of the data written to it.
        """
        return True

    def set_present_value(self, value: Any) -> None:
        """
        API for manual injection of values (e.g., from sample scripts).
        Provides an external interface to mutate the point's value and optionally 
        resets its error status to simulate hardware recovery.
        """
        self._present_value = value
        if self.status and self.status.level >= 500:
            self.status = None

    def _populate_enumeration(self, point: RefDiscovery) -> None:
        """Concrete implementation doing nothing by default."""

    # =========================================================================
    # BACKWARD COMPATIBILITY LAYER
    # =========================================================================

    @property
    def present_value(self):
        """
        Get the point's present value.
        """
        warnings.warn(
            "Direct access to 'present_value' is deprecated in v2.0.0. "
            "Use 'get_data().present_value'.",
            DeprecationWarning, stacklevel=2
        )
        return self.get_data().present_value

    @present_value.setter
    def present_value(self, value):
        """
        Set the point's present value.
        """
        warnings.warn(
            "Direct setting of 'present_value' is deprecated. "
            "Use 'set_present_value(value)'.",
            DeprecationWarning, stacklevel=2
        )
        self.set_present_value(value)

    @property
    def units(self):
        """
        Get the point's units.
        """
        warnings.warn(
            "Direct access to 'units' is deprecated. "
            "Use 'get_state().units'.",
            DeprecationWarning, stacklevel=2
        )
        return self.get_state().units

    @units.setter
    def units(self, value):
        """
        Set the point's units.
        """
        warnings.warn(
            "Direct setting of 'units' is deprecated. "
            "Update metadata models instead.",
            DeprecationWarning, stacklevel=2
        )
        self._state.units = value
        self._dirty = True

    def get_event(self):
        """
        Provides the compiled telemetry data payload for this point to be included
        in PointsetEvents.
        """
        warnings.warn(
            "'get_event()' is deprecated. Use 'get_data()'.",
            DeprecationWarning, stacklevel=2
        )
        return self.get_data()

    def update_config(self, config) -> bool:
        """
        Update the configuration for this point and return true if state changed.
        """
        warnings.warn(
            "'update_config()' is deprecated. Use 'set_config()'.",
            DeprecationWarning, stacklevel=2
        )
        self.set_config(config)
        return self.is_dirty()
