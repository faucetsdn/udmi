"""Spotter custom managers."""

try:
  from manager.discovery import SpotterDiscoveryManager
  from manager.system import SpotterSystemManager
except ImportError:
  from edge.spotter.src.manager.discovery import SpotterDiscoveryManager
  from edge.spotter.src.manager.system import SpotterSystemManager

__all__ = ["SpotterDiscoveryManager", "SpotterSystemManager"]

