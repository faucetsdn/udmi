"""Protocol Family Providers for UDMI Spotter."""

try:
    from providers.bacnet import BacnetFamilyProvider
    from providers.ether import EtherFamilyProvider
    from providers.passive import PassiveFamilyProvider
except ImportError:
    from edge.spotter.src.providers.bacnet import BacnetFamilyProvider
    from edge.spotter.src.providers.ether import EtherFamilyProvider
    from edge.spotter.src.providers.passive import PassiveFamilyProvider

__all__ = [
    "BacnetFamilyProvider",
    "EtherFamilyProvider",
    "PassiveFamilyProvider",
]
