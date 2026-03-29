from dataclasses import dataclass
from typing import ClassVar, Set

from ._base import DataModel

@dataclass
class Bucket(DataModel):
  """
  This class is manually curated, auto-generated, and then copied into the directory.
  Look for the proper source and don't be fooled! Ultimately sourced from docs/specs/buckets.md
  """

  # IoT connection endpoint management
  ENDPOINT: ClassVar[str] = "endpoint"

  # Endpoint configuration updates
  ENDPOINT_CONFIG: ClassVar[str] = "endpoint.config"

  # Basic device property enumeration capability
  ENUMERATION: ClassVar[str] = "enumeration"

  # Enumerating available points of a device
  ENUMERATION_POINTSET: ClassVar[str] = "enumeration.pointset"

  # Enumerating the features a device supports
  ENUMERATION_FEATURES: ClassVar[str] = "enumeration.features"

  # Enumerating the network families of the device
  ENUMERATION_FAMILIES: ClassVar[str] = "enumeration.families"

  # Automated discovery capabilities
  DISCOVERY: ClassVar[str] = "discovery"

  # Scanning a network for devices
  DISCOVERY_SCAN: ClassVar[str] = "discovery.scan"

  # UDMI gateway capabilities
  GATEWAY: ClassVar[str] = "gateway"

  # Pointset and telemetry capabilities
  POINTSET: ClassVar[str] = "pointset"

  # Basic system operations
  SYSTEM: ClassVar[str] = "system"

  # System mode
  SYSTEM_MODE: ClassVar[str] = "system.mode"

  # Writeback related operations
  WRITEBACK: ClassVar[str] = "writeback"

  # unknown default value
  UNKNOWN_DEFAULT: ClassVar[str] = "unknown"

  _values: ClassVar[Set[str]] = {
    ENDPOINT,
    ENDPOINT_CONFIG,
    ENUMERATION,
    ENUMERATION_POINTSET,
    ENUMERATION_FEATURES,
    ENUMERATION_FAMILIES,
    DISCOVERY,
    DISCOVERY_SCAN,
    GATEWAY,
    POINTSET,
    SYSTEM,
    SYSTEM_MODE,
    WRITEBACK,
    UNKNOWN_DEFAULT
  }

  @classmethod
  def contains(cls, value: str) -> bool:
    return value in cls._values
