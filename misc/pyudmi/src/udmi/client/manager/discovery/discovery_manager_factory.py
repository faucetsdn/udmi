from udmi.client.manager.discovery.global_bacnet_scan import GlobalBacnetScan
from udmi.client.manager.discovery.nmap_banner_scan import NmapBannerScan
from udmi.client.manager.discovery.passive_network_scan import (
    PassiveNetworkScan
)

DISCOVERY_SCAN_MAP = {
    'bacnet': GlobalBacnetScan,
    'nmap': NmapBannerScan,
    'passive': PassiveNetworkScan
}


class ScanNotImplemented(Exception):
    pass


def get_manager(key, *args, **kwargs):
    manager = DISCOVERY_SCAN_MAP.get(key)
    if not manager:
        raise ScanNotImplemented(f"Invalid key, choose one from available "
                                 f"scans: {DISCOVERY_SCAN_MAP.keys()}")
    return manager(*args, **kwargs)
