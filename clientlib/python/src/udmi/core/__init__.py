"""
UDMI Core Package.

This package brings key classes and factory functions to the top-level
`udmi.core` namespace for convenient access.
"""

from .device import Device
from .factory import create_mqtt_device_instance
from .factory import create_device_with_basic_auth
from .factory import create_device_with_jwt
