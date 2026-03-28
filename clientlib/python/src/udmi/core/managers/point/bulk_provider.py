"""
Provides the BulkPointProvider interface.
"""
from abc import ABC, abstractmethod
from typing import Any, Dict

class BulkPointProvider(ABC):
    """
    Interface for providing bulk telemetry reads to the PointsetManager.
    This interface allows for batch reading of hardware points (e.g., Modbus, BACnet) at
    the start of a telemetry cycle, significantly reducing IO overhead compared to
    polling points individually.
    """

    @abstractmethod
    def read_points(self) -> Dict[str, Any]:
        """
        Reads the current values of all supported points from hardware.
        Executes a batch IO operation to retrieve the latest sensor readings and returns them
        as a mapping to be injected into the PointsetManager's pipeline.

        Returns:
            Dict[str, Any]: A mapping of point names to their present values.
        """
        pass
