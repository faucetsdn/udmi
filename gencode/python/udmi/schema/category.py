"""
This module defines the Category enum, representing system event categories
and their default logging levels.
"""
from enum import Enum
from .level import Level

class Category(Enum):
    """
    This class is manually curated, auto-generated, and then copied into the directory.
    Look for the proper source and don't be fooled! Ultimately sourced from docs/specs/categories.md
    """

    # System is in the process of (re)starting and essentially offline
    SYSTEM_BASE_START = ("system.base.start", Level.NOTICE)

    # System is shutting down
    SYSTEM_BASE_SHUTDOWN = ("system.base.shutdown", Level.NOTICE)

    # System is fully ready for operation
    SYSTEM_BASE_READY = ("system.base.ready", Level.NOTICE)

    # Receiving a config message
    SYSTEM_CONFIG_RECEIVE = ("system.config.receive", Level.DEBUG)

    # Parsing a received message
    SYSTEM_CONFIG_PARSE = ("system.config.parse", Level.DEBUG)

    # Application of a parsed config message
    SYSTEM_CONFIG_APPLY = ("system.config.apply", Level.NOTICE)

    # Successful login. The entry message should include the username and application
    SYSTEM_AUTH_LOGIN = ("system.auth.login", Level.NOTICE)

    # Successful logout
    SYSTEM_AUTH_LOGOUT = ("system.auth.logout", Level.NOTICE)

    # Failed authentication attempt. The entry message should include the application
    SYSTEM_AUTH_FAIL = ("system.auth.fail", Level.WARNING)

    # Category for normal operating state (also default).
    POINTSET_POINT_NOMINAL = ("pointset.point.nominal", Level.INFO)

    # The `set_value` for a point has been applied
    POINTSET_POINT_APPLIED = ("pointset.point.applied", Level.INFO)

    # The point is in the process of updating
    POINTSET_POINT_UPDATING = ("pointset.point.updating", Level.NOTICE)

    # The reported value has been overridden locally
    POINTSET_POINT_OVERRIDDEN = ("pointset.point.overridden", Level.WARNING)

    # The system failed to read/write the point
    POINTSET_POINT_FAILURE = ("pointset.point.failure", Level.ERROR)

    # A `config` parameter for the point is invalid in some way
    POINTSET_POINT_INVALID = ("pointset.point.invalid", Level.ERROR)

    # Aspects of a specific network
    LOCALNET_NETWORK = ("localnet.network", Level.INFO)

    # Connected status of the device on
    LOCALNET_NETWORK_CONNECT = ("localnet.network.connect", Level.NOTICE)

    # Expected status for attachment failures between gateway and cloud
    GATEWAY_SETUP_ATTACH = ("gateway.setup.attach", Level.ERROR)

    # Basic target block specification, missing (warning) or unprocessable (error)
    GATEWAY_PROXY_TARGET = ("gateway.proxy.target", Level.WARNING)

    # Fieldbus connection between gateway and proxied device (error on failure)
    GATEWAY_PROXY_CONNECT = ("gateway.proxy.connect", Level.ERROR)

    # Relating to scanning a particular address family
    DISCOVERY_FAMILY_SCAN = ("discovery.family.scan", Level.INFO)

    # Handling point enumeration for a given device
    DISCOVERY_DEVICE_ENUMERATE = ("discovery.device.enumerate", Level.INFO)

    # Relating to describing a particular point
    DISCOVERY_POINT_DESCRIBE = ("discovery.point.describe", Level.INFO)

    # Stage of applying a device mapping
    MAPPING_DEVICE_APPLY = ("mapping.device.apply", Level.INFO)

    # About receiving a blob update
    BLOBSET_BLOB_RECEIVE = ("blobset.blob.receive", Level.DEBUG)

    # Fetching a blob update
    BLOBSET_BLOB_FETCH = ("blobset.blob.fetch", Level.DEBUG)

    # Applying a blob update
    BLOBSET_BLOB_APPLY = ("blobset.blob.apply", Level.NOTICE)

    # Receiving/processing a message for validation.
    VALIDATION_DEVICE_RECEIVE = ("validation.device.receive", Level.DEBUG)

    # Basic schema and structure validation.
    VALIDATION_DEVICE_SCHEMA = ("validation.device.schema", Level.INFO)

    # Errors validating semantic content of the message.
    VALIDATION_DEVICE_CONTENT = ("validation.device.content", Level.INFO)

    # Multiple issues reported.
    VALIDATION_DEVICE_MULTIPLE = ("validation.device.multiple", Level.INFO)

    # Device was unexpected (not in site model).
    VALIDATION_DEVICE_EXTRA = ("validation.device.extra", Level.INFO)

    # The validation summary report.
    VALIDATION_SUMMARY_REPORT = ("validation.summary.report", Level.INFO)

    # An individual line-item sequence test.
    VALIDATION_FEATURE_SEQUENCE = ("validation.feature.sequence", Level.INFO)

    # Feature message schema validations.
    VALIDATION_FEATURE_SCHEMA = ("validation.feature.schema", Level.INFO)

    # Sequence test capability.
    VALIDATION_FEATURE_CAPABILITY = ("validation.feature.capability", Level.INFO)


    def __init__(self, value_str: str, default_level: Level):
        self._value_str = value_str
        self.default_level = default_level

    @property
    def value(self) -> str:
        return self._value_str

    @property
    def level(self) -> int:
        return self.default_level.value

    def __str__(self) -> str:
        return self._value_str
