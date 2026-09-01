"""Spotter custom managers."""

try:
    from manager.discovery import SpotterDiscoveryManager
except ImportError:
    from edge.spotter.src.manager.discovery import SpotterDiscoveryManager

__all__ = ["SpotterDiscoveryManager"]
