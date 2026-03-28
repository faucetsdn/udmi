import abc
from typing import Any

from udmi.schema import PointPointsetConfig
from udmi.schema import PointPointsetEvents
from udmi.schema import PointPointsetState
from udmi.schema import PointPointsetModel
from udmi.schema import RefDiscovery


class AbstractPoint(abc.ABC):
    """
    Interface representing a point reading.
    Defines the strict contract that any point implementation must adhere to,
    ensuring the PointsetManager remains agnostic to data acquisition details.
    
    Defines lifecycle methods (update_data, set_config, set_model) and state 
    reporting methods (get_data, get_state, is_dirty) that concrete classes 
    must implement to align with the UDMI specification.
    """

    @abc.abstractmethod
    def get_name(self) -> str:
        """
        Provides the string identifier that maps to the pointset block in UDMI payloads.
        """

    @abc.abstractmethod
    def get_data(self) -> PointPointsetEvents:
        """
        Provides the compiled telemetry data payload for this point to be included in PointsetEvents.
        """

    @abc.abstractmethod
    def update_data(self) -> None:
        """
        Instructs the point to perform any necessary IO or state checks to refresh its telemetry data.
        """

    @abc.abstractmethod
    def is_dirty(self) -> bool:
        """
        Returns if the state of the point has changed.
        Indicates to the PointsetManager whether this point's state needs to be re-aggregated 
        and a new state_etag generated because of configuration or status changes.
        """

    @abc.abstractmethod
    def get_state(self) -> PointPointsetState:
        """
        Provides the compiled state payload (including value_state and status) for PointsetState.
        """

    @abc.abstractmethod
    def set_config(self, config: PointPointsetConfig, **kwargs: 'Any') -> None:
        """
        Sets the config of the point.
        Applies dynamic changes (like writebacks via set_value) from a cloud config update.
        """

    @abc.abstractmethod
    def should_report(self, sample_rate_sec: int) -> bool:
        """
        Determines if this point needs to be reported.
        Evaluates the Change of Value (COV) tolerance and Heartbeat interval to inform 
        the PointsetManager whether this point should be included in the next telemetry payload.
        """

    @abc.abstractmethod
    def mark_reported(self) -> None:
        """
        Updates the reporting state after a successful publish.
        Caches the last reported value and timestamp to reset COV and Heartbeat state machines.
        """

    @abc.abstractmethod
    def set_model(self, model: PointPointsetModel) -> None:
        """
        Applies static definition from Metadata.
        Initializes point properties (like units, type, or writable flags) during device startup.
        """

    @abc.abstractmethod
    def enumerate(self) -> RefDiscovery:
        """
        Returns discovery information for this point.
        Formats the point's static details for inclusion in the Discovery block.
        """
