"""
Defines the interface for Protocol Family Providers (e.g., BACnet, Modbus).

This module provides the `FamilyProvider` abstract base class, which defines
the contract for pluggable modules that handle discovery and point enumeration
for specific local network protocols.
"""
from abc import ABC
from abc import abstractmethod
from typing import Any
from typing import Callable
from typing import Dict

from udmi.schema import DiscoveryEvents
from udmi.schema import RefDiscovery


class FamilyProvider(ABC):
    """
    Abstract interface for a protocol family provider.

    Implementations of this class are responsible for interacting with specific
    local network protocols (BACnet, Modbus, etc.) to discover devices and
    enumerate their points.
    """

    @abstractmethod
    def start_scan(self, discovery_config: Any,
        publish_func: Callable[[str, DiscoveryEvents], None]) -> None:
        """
        Starts a discovery scan for this family.

        This method should be non-blocking (or run in its own thread managed by
        the provider) and report results via the `publish_func` callback.

        Args:
            discovery_config: The configuration block specific to this family
                              (e.g., scan ranges, timeouts).
            publish_func: A callback to report discovered devices/points.
                          Signature: (scan_id: str, event: DiscoveryEvents) -> None
        """

    @abstractmethod
    def stop_scan(self) -> None:
        """
        Stops any active scan immediately.
        """

    @abstractmethod
    def enumerate_refs(self, addr: str) -> Dict[str, RefDiscovery]:
        """
        Enumerates points (refs) for a specific device address.

        Args:
            addr: The protocol-specific address of the target device.

        Returns:
            A dictionary mapping point IDs to their RefDiscovery metadata.
        """
