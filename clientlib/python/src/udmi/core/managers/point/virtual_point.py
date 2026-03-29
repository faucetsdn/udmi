"""Virtual point definition."""
from typing import Any
from typing import Optional

from udmi.core.managers.point.basic_point import BasicPoint
from udmi.schema import PointPointsetModel
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

    def __init__(self, name: str, model: Optional[PointPointsetModel] = None):
        super().__init__(name, model)
        # Seed with baseline_value from model if present
        self._present_value: Any = (
            model.baseline_value if (model and model.baseline_value is not None) else None
        )

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
