"""
Global constants for the pyudmi library.
"""
from udmi.schema import Blobsets

# The UDMI schema version this library is built to support.
# This is used in State and Event message headers.
UDMI_VERSION = "1.5.2"
PERSISTENT_STORE_PATH = ".udmi_persistence.json"
IOT_ENDPOINT_CONFIG_BLOB_KEY = Blobsets.field_iot_endpoint_config
