import abc

from udmi.schema import PointPointsetConfig
from udmi.schema import PointPointsetEvents
from udmi.schema import PointPointsetState
from udmi.schema import PointPointsetModel
from udmi.schema import RefDiscovery


class AbstractPoint(abc.ABC):
    """
    Interface representing a point reading, matching Java AbstractPoint.
    """

    @abc.abstractmethod
    def get_name(self) -> str:
        """
        Returns the name of the point
        """

    @abc.abstractmethod
    def get_data(self) -> PointPointsetEvents:
        """
        Returns the data of the point.
        """

    @abc.abstractmethod
    def update_data(self) -> None:
        """
        Updates the data of the point.
        """

    @abc.abstractmethod
    def is_dirty(self) -> bool:
        """
        Returns if the state of the point has changed.
        """

    @abc.abstractmethod
    def get_state(self) -> PointPointsetState:
        """
        Returns the state of the point.
        """

    @abc.abstractmethod
    def set_config(self, config: PointPointsetConfig, **kwargs: 'Any') -> None:
        """
        Sets the state of the point.
        """

    @abc.abstractmethod
    def should_report(self, sample_rate_sec: int) -> bool:
        """
        Determines if this point needs to be reported based on Change of Value (COV)
        and Heartbeat logic.
        """

    @abc.abstractmethod
    def mark_reported(self) -> None:
        """Updates the reporting state state after a successful publish."""

    @abc.abstractmethod
    def set_model(self, model: PointPointsetModel) -> None:
        """Applies static definition from Metadata."""

    @abc.abstractmethod
    def enumerate(self) -> RefDiscovery:
        """
        Returns discovery information for this point.
        """
