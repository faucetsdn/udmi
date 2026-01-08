"""
Abstract interface for a generic, protocol-agnostic messaging client.

This module defines the contract for sending and receiving messages (payloads)
over named channels, abstracting away the underlying protocol (e.g., MQTT).
"""
from abc import ABC
from abc import abstractmethod
from typing import Callable
from typing import Optional

# Signature: (device_id: str, channel: str, payload: str) -> None
OnMessageHandler = Callable[[str, str, str], None]
# Signature: () -> None
OnConnectHandler = Callable[[], None]
# Signature: (reason_code: int) -> None
OnDisconnectHandler = Callable[[int], None]


class AbstractMessagingClient(ABC):
    """
    An abstract base class for a generic messaging client.

    Its only job is to send and receive string payloads on named channels.
    It has no knowledge of UDMI, JSON, or specific message models.
    """

    @abstractmethod
    def connect(self) -> None:
        """
        Configures and initiates the connection to the message broker.

        This method should be non-blocking. The connection process
        happens in the background, and the 'on_connect' or 'on_disconnect'
        callback will be triggered upon completion.
        """

    @abstractmethod
    def publish(self, channel: str, payload: str,
        device_id: Optional[str] = None) -> None:
        """
        Publishes a raw string payload to a generic channel.

        Args:
            channel: The logical channel to publish to (e.g., 'state', 'event').
            payload: The raw string data to send.
            device_id: (Optional) The device ID to publish on behalf of.
                       If None, uses the primary device ID.
        """

    @abstractmethod
    def register_channel_subscription(self, channel: str,
        device_id: Optional[str] = None) -> None:
        """
        Registers interest in a generic channel.
        The client will handle the protocol-specific subscription.

        Args:
            channel: The channel to subscribe to.
            device_id: (Optional) The device ID to subscribe for.
                       If None, uses the primary device ID.
        """

    @abstractmethod
    def run(self) -> None:
        """
        Starts the client's non-blocking network loop.

        This should typically start a background thread to handle
        network I/O, allowing the main application thread to continue.
        """

    @abstractmethod
    def close(self) -> None:
        """
        Stops the network loop and gracefully disconnects the client.
        """

    @abstractmethod
    def check_authentication(self) -> None:
        """
        Checks if the client's authentication (e.g., token) needs
        to be refreshed and, if so, handles the refresh.

        This is intended to be called periodically by the main application loop.
        """

    @abstractmethod
    def set_on_message_handler(self, handler: OnMessageHandler) -> None:
        """
        Sets the external callback for incoming messages.

        Args:
            handler: A callable that accepts (device_id, channel, payload).
        """

    @abstractmethod
    def set_on_connect_handler(self, handler: OnConnectHandler) -> None:
        """
        Sets the external callback for successful connection events.

        Args:
            handler: A callable that takes no arguments.
        """

    @abstractmethod
    def set_on_disconnect_handler(self, handler: OnDisconnectHandler) -> None:
        """
        Sets the external callback for disconnect events.

        Args:
            handler: A callable that accepts (rc: int), where 'rc' is
                     the reason code for the disconnection.
        """
