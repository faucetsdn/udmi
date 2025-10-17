import logging
from abc import ABC
from abc import abstractmethod
from typing import Any
from typing import Callable
from typing import Dict

from udmi.schema import DataModel

LOGGER = logging.getLogger(__name__)

MessageHandler = Callable[[str, Dict[str, Any]], None]


class AbstractMessageDispatcher(ABC):
    """
    Abstract interface for a UDMI message dispatcher.
    Defines the contract for the Device class.
    """

    @abstractmethod
    def register_handler(self, channel: str, handler: MessageHandler) -> None:
        """Register a handler for a specific channel."""
        pass

    @abstractmethod
    def publish_state(self, state: DataModel) -> None:
        """Serializes and publishes the device State message."""
        pass

    @abstractmethod
    def publish_event(self, channel: str, event: DataModel) -> None:
        """Serializes and publishes a device Event message."""
        pass

    @abstractmethod
    def connect(self) -> None:
        """Connects the underlying messaging client."""
        pass

    @abstractmethod
    def start_loop(self) -> None:
        """Starts the client's non-blocking network loop."""
        pass

    @abstractmethod
    def check_authentication(self) -> None:
        """
        Checks if the client's authentication needs to be refreshed
        and triggers the refresh if necessary.
        """
        pass

    @abstractmethod
    def close(self) -> None:
        """Shuts down the connection."""
        pass
