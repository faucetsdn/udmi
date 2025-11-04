"""
Defines the abstract base class (ABC) for feature managers.

This module provides the core `BaseManager` interface that all
feature-specific managers (e.g., SystemManager, PointsetManager) must
implement to be orchestrated by the main Device class.
"""

import abc
import logging
from typing import Optional
from typing import TYPE_CHECKING

from udmi.schema import Config
from udmi.schema import DataModel
from udmi.schema import State

if TYPE_CHECKING:
    # To avoid circular import error
    from udmi.core import Device
    from udmi.core.messaging import AbstractMessageDispatcher

LOGGER = logging.getLogger(__name__)


class BaseManager(abc.ABC):
    """
    Abstract base class for all feature Managers.

    A manager implements a specific slice of UDMI logic (e.g., Pointset, Gateway).
    The Device class will orchestrate multiple managers.
    """

    def __init__(self) -> None:
        self._device: Optional["Device"] = None
        self._dispatcher: Optional["AbstractMessageDispatcher"] = None
        LOGGER.debug("Initialized manager: %s", self.__class__.__name__)

    def set_device_context(self, device: Optional["Device"],
        dispatcher: Optional["AbstractMessageDispatcher"]) -> None:
        """
        Provides the manager with a handle to the core device components.
        This is called by the Device during initialization.
        """
        self._device = device
        self._dispatcher = dispatcher

    def publish_event(self, event_model: DataModel, subfolder: str) -> None:
        """
        Helper method for managers to publish events.
        """
        if not self._dispatcher:
            LOGGER.error("Manager %s cannot publish event: dispatcher not set.",
                         self.__class__.__name__)

        self._dispatcher.publish_event(f"events/{subfolder}", event_model)

    def start(self) -> None:
        """
        Called by the Device when the main loop starts.
        Optional to implement; for managers that need an init process.
        """

    def stop(self) -> None:
        """
        Called by the Device when the main loop stops.
        Optional to implement; for managers that need a cleanup process
        (e.g., stopping threads).
        """

    @abc.abstractmethod
    def handle_config(self, config: Config) -> None:
        """
        Handle a newly received config message.
        The manager should only act on the parts of the config
        it is responsible for (e.g., config.system, config.pointset).
        """
        raise NotImplementedError

    @abc.abstractmethod
    def handle_command(self, command_name: str, payload: dict) -> None:
        """
        Handle a newly received command.
        The manager should check if the command_name (e.g., 'pointset.writeback')
        is one that it should process.
        """
        raise NotImplementedError

    @abc.abstractmethod
    def update_state(self, state: State) -> None:
        """
        Contribute to the state object before it is published.
        The manager should only modify the parts of the state
        it is responsible for (e.g., state.pointset).
        """
        raise NotImplementedError
