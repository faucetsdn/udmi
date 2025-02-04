import BAC0
from typing import Callable

from udmi.client.manager.discovery.discovery_manager import DiscoveryManager
from udmi_schema.schema.state_localnet_family import FamilyLocalnetState
from udmi_schema.schema.state_localnet import LocalnetState
from udmi_schema.schema.events_discovery import DiscoveryEvents


class GlobalBacnetScan(DiscoveryManager):

    scan_family = "bacnet"

    def __init__(
        self,
        state: FamilyLocalnetState,
        publisher: Callable[[DiscoveryEvents], None],
        *,
        bacnet_ip: str = None,
        bacnet_port: int = None,
        bacnet_intf: str = None):
        self.devices_published = set()
        self.cancelled = None
        self.result_producer_thread = None
        self.bacnet = BAC0.lite(ip=bacnet_ip, port=bacnet_port)
        super().__init__(state, publisher)

    def start_discovery(self) -> None:
        pass

    def stop_discovery(self) -> None:
        pass

