"""
Defines the abstract base class (ABC) for a message dispatcher.

This module provides the core `AbstractMessageDispatcher` interface.
The Dispatcher sits between the raw Messaging Client and the application logic,
handling serialization (Object -> JSON) and deserialization (JSON -> Dict)
so the rest of the application deals with Data Models, not raw strings.
"""

from abc import ABC
from abc import abstractmethod
from typing import Any
from typing import Callable
from typing import Dict
from typing import Optional

from udmi.core.messaging.abstract_client import AbstractMessagingClient
from udmi.schema import DataModel

# Handler Signature: (device_id: str, channel: str, payload: Dict[str, Any]) -> None
MessageHandler = Callable[[str, str, Dict[str, Any]], None]


class AbstractMessageDispatcher(ABC):
    """
    Abstract interface for a UDMI message dispatcher.

    Responsibilities:
    1.  Wraps an AbstractMessagingClient.
    2.  Serializes DataModel objects to JSON strings for publishing.
    3.  Deserializes incoming JSON strings to Dictionaries for handlers.
    4.  Routes incoming messages to registered callbacks based on channel.
    """

    @property
    @abstractmethod
    def client(self) -> AbstractMessagingClient:
        """Accessor for the underlying messaging client."""

    @abstractmethod
    def register_handler(self, channel: str, handler: MessageHandler) -> None:
        """
        Registers a callback for a specific UDMI channel (e.g., 'config').

        Args:
            channel: The channel name.
            handler: A function accepting (device_id, channel, payload_dict).
        """

    @abstractmethod
    def publish_state(self, state: DataModel,
        device_id: Optional[str] = None) -> None:
        """
        Serializes and publishes the device State message.

        Args:
            state: The UDMI State object (schema-derived).
            device_id: (Optional) Target device ID (for gateways).
        """

    @abstractmethod
    def publish_event(self, channel: str, event: DataModel,
        device_id: Optional[str] = None) -> None:
        """
        Serializes and publishes a device Event message.

        Args:
            channel: The specific event sub-channel (e.g., 'system', 'pointset').
            event: The UDMI Event object (schema-derived).
            device_id: (Optional) Target device ID.
        """

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
