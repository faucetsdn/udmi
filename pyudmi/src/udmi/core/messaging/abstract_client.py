"""
Abstract interface for a generic, protocol-agnostic messaging client.
"""

from abc import ABC
from abc import abstractmethod
from typing import Callable


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
        pass

    @abstractmethod
    def publish(self, channel: str, payload: str) -> None:
        """
        Publishes a raw string payload to a generic channel.

        Args:
            :param channel: The logical channel to publish to.
            :param payload: The raw string data to send.
        """
        pass

    @abstractmethod
    def run(self) -> None:
        """
        Starts the client's non-blocking network loop.

        This should typically start a background thread to handle
        network I/O, allowing the main application thread to continue.
        """
        pass

    @abstractmethod
    def close(self) -> None:
        """
        Stops the network loop and gracefully disconnects the client.
        """
        pass

    @abstractmethod
    def check_authentication(self) -> None:
        """
        Checks if the client's authentication (e.g., token) needs
        to be refreshed and, if so, handles the refresh.

        This is intended to be called periodically by the main application loop.
        """
        pass

    @abstractmethod
    def set_on_message_handler(self,
        handler: Callable[[str, str], None]) -> None:
        """
        Sets the external callback for incoming messages.

        Args:
            :param handler: A callable that accepts (channel: str, payload: str)
        """
        pass

    @abstractmethod
    def set_on_connect_handler(self, handler: Callable[[], None]) -> None:
        """
        Sets the external callback for successful connection events.

        Args:
            :param handler: A callable that takes no arguments.
        """
        pass

    @abstractmethod
    def set_on_disconnect_handler(self, handler: Callable[[int], None]) -> None:
        """
        Sets the external callback for disconnect events.

        Args:
            :param handler: A callable that accepts (rc: int), where 'rc' is
                            the reason code for the disconnection.
        """
        pass
