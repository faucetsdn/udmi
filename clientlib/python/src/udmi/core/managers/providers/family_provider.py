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
    """

    @abstractmethod
    def start_scan(self, discovery_config: Any, publish_func: Callable[[str, DiscoveryEvents], None]) -> None:
        """
        Starts a discovery scan for this family.
        """
        pass

    @abstractmethod
    def stop_scan(self) -> None:
        """
        Stops any active scan.
        """
        pass

    @abstractmethod
    def enumerate_refs(self, addr: str) -> Dict[str, RefDiscovery]:
        """
        Enumerates points/refs for a specific address.
        """
        pass