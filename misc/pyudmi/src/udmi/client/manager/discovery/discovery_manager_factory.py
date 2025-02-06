from udmi.client.manager.discovery.global_bacnet_scan import GlobalBacnetScan
from udmi.client.manager.discovery.nmap_banner_scan import NmapBannerScan
from udmi.client.manager.discovery.passive_network_scan import (
    PassiveNetworkScan
)

SCAN_FAMILY_TO_DISCOVERY_MAP = {
    'bacnet': GlobalBacnetScan,
    'ether': NmapBannerScan,
    'ipv4': PassiveNetworkScan
}


class ScanNotImplemented(Exception):
    pass


def get_manager_for_family(family_name: str, *args, **kwargs):
    manager = SCAN_FAMILY_TO_DISCOVERY_MAP.get(family_name)
    if not manager:
        raise ScanNotImplemented(f"Invalid key, choose one from available "
                                 f"scans: {SCAN_FAMILY_TO_DISCOVERY_MAP.keys()}"
                                 )
    return manager(*args, **kwargs)
