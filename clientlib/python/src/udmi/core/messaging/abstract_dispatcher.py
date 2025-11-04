import logging
from abc import ABC
from abc import abstractmethod
from typing import Any
from typing import Callable
from typing import Dict
from typing import Optional

from udmi.schema import DataModel

from .abstract_client import AbstractMessagingClient

LOGGER = logging.getLogger(__name__)

MessageHandler = Callable[[str, Dict[str, Any]], None]


class AbstractMessageDispatcher(ABC):
    """
    Abstract interface for a UDMI message dispatcher.
    Defines the contract for the Device class.
    """

    client: Optional[AbstractMessagingClient] = None

    @abstractmethod
    def register_handler(self, channel: str, handler: MessageHandler) -> None:
        """Register a handler for a specific channel."""

    @abstractmethod
    def publish_state(self, state: DataModel) -> None:
        """Serializes and publishes the device State message."""

    @abstractmethod
    def publish_event(self, channel: str, event: DataModel) -> None:
        """Serializes and publishes a device Event message."""

    @abstractmethod
    def connect(self) -> None:
        """Connects the underlying messaging client."""

    @abstractmethod
    def start_loop(self) -> None:
        """Starts the client's non-blocking network loop."""

    @abstractmethod
    def check_authentication(self) -> None:
        """
        Checks if the client's authentication needs to be refreshed
        and triggers the refresh if necessary.
        """

    @abstractmethod
    def close(self) -> None:
        """Shuts down the connection."""
