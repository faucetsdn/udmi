"""
Feature managers package for the UDMI device.

This package defines the core `BaseManager` abstract base class
and provides concrete implementations for different slices of UDMI
functionality (e.g., `SystemManager`).

The imports in this file make these key classes directly available
from the `udmi.core.managers` namespace for easier use.
"""

from udmi.core.managers.base_manager import BaseManager
from udmi.core.managers.system_manager import SystemManager
