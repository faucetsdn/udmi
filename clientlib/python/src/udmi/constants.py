"""
Global constants for the pyudmi library.

This module defines system-wide constants used for versioning, persistence,
schema mapping.
"""
from typing import Final
from udmi.schema import Blobsets

# The UDMI schema version this library is built to support.
# This is used in State and Event message headers.
UDMI_VERSION: Final[str] = "1.5.2"

# Default file path for storing persistent device state (e.g. endpoint info).
PERSISTENT_STORE_PATH: Final[str] = ".udmi_persistence.json"

# The key used in the 'blobset' configuration to identify the endpoint config blob.
# Derived dynamically from the schema definition to ensure consistency with
# the UDMI schema generation.
IOT_ENDPOINT_CONFIG_BLOB_KEY: Final[str] = str(
    Blobsets.field_iot_endpoint_config.value)
