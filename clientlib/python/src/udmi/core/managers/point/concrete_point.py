from typing import Any
from typing import Optional

from udmi.core.managers.point.basic_point import BasicPoint
from udmi.schema import PointPointsetModel


class Point(BasicPoint):
    """
    Default concrete implementation of a Point.
    Provides an in-memory holder for the present value.
    """

    def __init__(self, name: str, model: Optional[PointPointsetModel] = None):
        super().__init__(name, model)
        # Seed with baseline_value from model if present
        self._present_value: Any = model.baseline_value if (model and model.baseline_value is not None) else None

    def get_value(self) -> Any:
        return self._present_value

    def set_value(self, value: Any) -> Any:
        """
        Concrete implementation of set_value, returning the value as applied.
        """
        return value

    def validate_value(self, value: Any) -> bool:
        """
        Default validation that takes all values as valid.
        """
        return True

    def set_present_value(self, value: Any) -> None:
        """
        API for manual injection of values (e.g., from sample scripts).
        """
        self._present_value = value
