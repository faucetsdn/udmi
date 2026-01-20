from dataclasses import dataclass
from typing import ClassVar, Dict

from ._base import DataModel
from .level import Level

@dataclass
class Category(DataModel):
    """
    This class is manually curated, auto-generated, and then copied into the directory.
    Look for the proper source and don't be fooled! Ultimately sourced from docs/specs/categories.md
    """
    LEVEL: ClassVar[Dict[str, Level]] = {}

    # System is in the process of (re)starting and essentially offline
    SYSTEM_BASE_START: ClassVar[str] = "system.base.start"
    SYSTEM_BASE_START_LEVEL: ClassVar[Level] = Level.NOTICE
    SYSTEM_BASE_START_VALUE: ClassVar[int] = Level.NOTICE.value
    LEVEL[SYSTEM_BASE_START] = Level.NOTICE

    # System is shutting down
    SYSTEM_BASE_SHUTDOWN: ClassVar[str] = "system.base.shutdown"
    SYSTEM_BASE_SHUTDOWN_LEVEL: ClassVar[Level] = Level.NOTICE
    SYSTEM_BASE_SHUTDOWN_VALUE: ClassVar[int] = Level.NOTICE.value
    LEVEL[SYSTEM_BASE_SHUTDOWN] = Level.NOTICE

    # System is fully ready for operation
    SYSTEM_BASE_READY: ClassVar[str] = "system.base.ready"
    SYSTEM_BASE_READY_LEVEL: ClassVar[Level] = Level.NOTICE
    SYSTEM_BASE_READY_VALUE: ClassVar[int] = Level.NOTICE.value
    LEVEL[SYSTEM_BASE_READY] = Level.NOTICE

    # Receiving a config message
    SYSTEM_CONFIG_RECEIVE: ClassVar[str] = "system.config.receive"
    SYSTEM_CONFIG_RECEIVE_LEVEL: ClassVar[Level] = Level.DEBUG
    SYSTEM_CONFIG_RECEIVE_VALUE: ClassVar[int] = Level.DEBUG.value
    LEVEL[SYSTEM_CONFIG_RECEIVE] = Level.DEBUG

    # Parsing a received message
    SYSTEM_CONFIG_PARSE: ClassVar[str] = "system.config.parse"
    SYSTEM_CONFIG_PARSE_LEVEL: ClassVar[Level] = Level.DEBUG
    SYSTEM_CONFIG_PARSE_VALUE: ClassVar[int] = Level.DEBUG.value
    LEVEL[SYSTEM_CONFIG_PARSE] = Level.DEBUG

    # Application of a parsed config message
    SYSTEM_CONFIG_APPLY: ClassVar[str] = "system.config.apply"
    SYSTEM_CONFIG_APPLY_LEVEL: ClassVar[Level] = Level.NOTICE
    SYSTEM_CONFIG_APPLY_VALUE: ClassVar[int] = Level.NOTICE.value
    LEVEL[SYSTEM_CONFIG_APPLY] = Level.NOTICE

    # Successful login. The entry message should include the username and application
    SYSTEM_AUTH_LOGIN: ClassVar[str] = "system.auth.login"
    SYSTEM_AUTH_LOGIN_LEVEL: ClassVar[Level] = Level.NOTICE
    SYSTEM_AUTH_LOGIN_VALUE: ClassVar[int] = Level.NOTICE.value
    LEVEL[SYSTEM_AUTH_LOGIN] = Level.NOTICE

    # Successful logout
    SYSTEM_AUTH_LOGOUT: ClassVar[str] = "system.auth.logout"
    SYSTEM_AUTH_LOGOUT_LEVEL: ClassVar[Level] = Level.NOTICE
    SYSTEM_AUTH_LOGOUT_VALUE: ClassVar[int] = Level.NOTICE.value
    LEVEL[SYSTEM_AUTH_LOGOUT] = Level.NOTICE

    # Failed authentication attempt. The entry message should include the application
    SYSTEM_AUTH_FAIL: ClassVar[str] = "system.auth.fail"
    SYSTEM_AUTH_FAIL_LEVEL: ClassVar[Level] = Level.WARNING
    SYSTEM_AUTH_FAIL_VALUE: ClassVar[int] = Level.WARNING.value
    LEVEL[SYSTEM_AUTH_FAIL] = Level.WARNING

    # Category for normal operating state (also default).
    POINTSET_POINT_NOMINAL: ClassVar[str] = "pointset.point.nominal"
    POINTSET_POINT_NOMINAL_LEVEL: ClassVar[Level] = Level.INFO
    POINTSET_POINT_NOMINAL_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[POINTSET_POINT_NOMINAL] = Level.INFO

    # The `set_value` for a point has been applied
    POINTSET_POINT_APPLIED: ClassVar[str] = "pointset.point.applied"
    POINTSET_POINT_APPLIED_LEVEL: ClassVar[Level] = Level.INFO
    POINTSET_POINT_APPLIED_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[POINTSET_POINT_APPLIED] = Level.INFO

    # The point is in the process of updating
    POINTSET_POINT_UPDATING: ClassVar[str] = "pointset.point.updating"
    POINTSET_POINT_UPDATING_LEVEL: ClassVar[Level] = Level.NOTICE
    POINTSET_POINT_UPDATING_VALUE: ClassVar[int] = Level.NOTICE.value
    LEVEL[POINTSET_POINT_UPDATING] = Level.NOTICE

    # The reported value has been overridden locally
    POINTSET_POINT_OVERRIDDEN: ClassVar[str] = "pointset.point.overridden"
    POINTSET_POINT_OVERRIDDEN_LEVEL: ClassVar[Level] = Level.WARNING
    POINTSET_POINT_OVERRIDDEN_VALUE: ClassVar[int] = Level.WARNING.value
    LEVEL[POINTSET_POINT_OVERRIDDEN] = Level.WARNING

    # The system failed to read/write the point
    POINTSET_POINT_FAILURE: ClassVar[str] = "pointset.point.failure"
    POINTSET_POINT_FAILURE_LEVEL: ClassVar[Level] = Level.ERROR
    POINTSET_POINT_FAILURE_VALUE: ClassVar[int] = Level.ERROR.value
    LEVEL[POINTSET_POINT_FAILURE] = Level.ERROR

    # A `config` parameter for the point is invalid in some way
    POINTSET_POINT_INVALID: ClassVar[str] = "pointset.point.invalid"
    POINTSET_POINT_INVALID_LEVEL: ClassVar[Level] = Level.ERROR
    POINTSET_POINT_INVALID_VALUE: ClassVar[int] = Level.ERROR.value
    LEVEL[POINTSET_POINT_INVALID] = Level.ERROR

    # Aspects of a specific network
    LOCALNET_NETWORK: ClassVar[str] = "localnet.network"
    LOCALNET_NETWORK_LEVEL: ClassVar[Level] = Level.INFO
    LOCALNET_NETWORK_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[LOCALNET_NETWORK] = Level.INFO

    # Connected status of the device on
    LOCALNET_NETWORK_CONNECT: ClassVar[str] = "localnet.network.connect"
    LOCALNET_NETWORK_CONNECT_LEVEL: ClassVar[Level] = Level.NOTICE
    LOCALNET_NETWORK_CONNECT_VALUE: ClassVar[int] = Level.NOTICE.value
    LEVEL[LOCALNET_NETWORK_CONNECT] = Level.NOTICE

    # Expected status for attachment failures between gateway and cloud
    GATEWAY_SETUP_ATTACH: ClassVar[str] = "gateway.setup.attach"
    GATEWAY_SETUP_ATTACH_LEVEL: ClassVar[Level] = Level.ERROR
    GATEWAY_SETUP_ATTACH_VALUE: ClassVar[int] = Level.ERROR.value
    LEVEL[GATEWAY_SETUP_ATTACH] = Level.ERROR

    # Basic target block specification, missing (warning) or unprocessable (error)
    GATEWAY_PROXY_TARGET: ClassVar[str] = "gateway.proxy.target"
    GATEWAY_PROXY_TARGET_LEVEL: ClassVar[Level] = Level.WARNING
    GATEWAY_PROXY_TARGET_VALUE: ClassVar[int] = Level.WARNING.value
    LEVEL[GATEWAY_PROXY_TARGET] = Level.WARNING

    # Fieldbus connection between gateway and proxied device (error on failure)
    GATEWAY_PROXY_CONNECT: ClassVar[str] = "gateway.proxy.connect"
    GATEWAY_PROXY_CONNECT_LEVEL: ClassVar[Level] = Level.ERROR
    GATEWAY_PROXY_CONNECT_VALUE: ClassVar[int] = Level.ERROR.value
    LEVEL[GATEWAY_PROXY_CONNECT] = Level.ERROR

    # Relating to scanning a particular address family
    DISCOVERY_FAMILY_SCAN: ClassVar[str] = "discovery.family.scan"
    DISCOVERY_FAMILY_SCAN_LEVEL: ClassVar[Level] = Level.INFO
    DISCOVERY_FAMILY_SCAN_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[DISCOVERY_FAMILY_SCAN] = Level.INFO

    # Handling point enumeration for a given device
    DISCOVERY_DEVICE_ENUMERATE: ClassVar[str] = "discovery.device.enumerate"
    DISCOVERY_DEVICE_ENUMERATE_LEVEL: ClassVar[Level] = Level.INFO
    DISCOVERY_DEVICE_ENUMERATE_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[DISCOVERY_DEVICE_ENUMERATE] = Level.INFO

    # Relating to describing a particular point
    DISCOVERY_POINT_DESCRIBE: ClassVar[str] = "discovery.point.describe"
    DISCOVERY_POINT_DESCRIBE_LEVEL: ClassVar[Level] = Level.INFO
    DISCOVERY_POINT_DESCRIBE_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[DISCOVERY_POINT_DESCRIBE] = Level.INFO

    # Stage of applying a device mapping
    MAPPING_DEVICE_APPLY: ClassVar[str] = "mapping.device.apply"
    MAPPING_DEVICE_APPLY_LEVEL: ClassVar[Level] = Level.INFO
    MAPPING_DEVICE_APPLY_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[MAPPING_DEVICE_APPLY] = Level.INFO

    # About receiving a blob update
    BLOBSET_BLOB_RECEIVE: ClassVar[str] = "blobset.blob.receive"
    BLOBSET_BLOB_RECEIVE_LEVEL: ClassVar[Level] = Level.DEBUG
    BLOBSET_BLOB_RECEIVE_VALUE: ClassVar[int] = Level.DEBUG.value
    LEVEL[BLOBSET_BLOB_RECEIVE] = Level.DEBUG

    # Fetching a blob update
    BLOBSET_BLOB_FETCH: ClassVar[str] = "blobset.blob.fetch"
    BLOBSET_BLOB_FETCH_LEVEL: ClassVar[Level] = Level.DEBUG
    BLOBSET_BLOB_FETCH_VALUE: ClassVar[int] = Level.DEBUG.value
    LEVEL[BLOBSET_BLOB_FETCH] = Level.DEBUG

    # Applying a blob update
    BLOBSET_BLOB_APPLY: ClassVar[str] = "blobset.blob.apply"
    BLOBSET_BLOB_APPLY_LEVEL: ClassVar[Level] = Level.NOTICE
    BLOBSET_BLOB_APPLY_VALUE: ClassVar[int] = Level.NOTICE.value
    LEVEL[BLOBSET_BLOB_APPLY] = Level.NOTICE

    # Receiving/processing a message for validation.
    VALIDATION_DEVICE_RECEIVE: ClassVar[str] = "validation.device.receive"
    VALIDATION_DEVICE_RECEIVE_LEVEL: ClassVar[Level] = Level.DEBUG
    VALIDATION_DEVICE_RECEIVE_VALUE: ClassVar[int] = Level.DEBUG.value
    LEVEL[VALIDATION_DEVICE_RECEIVE] = Level.DEBUG

    # Basic schema and structure validation.
    VALIDATION_DEVICE_SCHEMA: ClassVar[str] = "validation.device.schema"
    VALIDATION_DEVICE_SCHEMA_LEVEL: ClassVar[Level] = Level.INFO
    VALIDATION_DEVICE_SCHEMA_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[VALIDATION_DEVICE_SCHEMA] = Level.INFO

    # Errors validating semantic content of the message.
    VALIDATION_DEVICE_CONTENT: ClassVar[str] = "validation.device.content"
    VALIDATION_DEVICE_CONTENT_LEVEL: ClassVar[Level] = Level.INFO
    VALIDATION_DEVICE_CONTENT_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[VALIDATION_DEVICE_CONTENT] = Level.INFO

    # Multiple issues reported.
    VALIDATION_DEVICE_MULTIPLE: ClassVar[str] = "validation.device.multiple"
    VALIDATION_DEVICE_MULTIPLE_LEVEL: ClassVar[Level] = Level.INFO
    VALIDATION_DEVICE_MULTIPLE_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[VALIDATION_DEVICE_MULTIPLE] = Level.INFO

    # Device was unexpected (not in site model).
    VALIDATION_DEVICE_EXTRA: ClassVar[str] = "validation.device.extra"
    VALIDATION_DEVICE_EXTRA_LEVEL: ClassVar[Level] = Level.INFO
    VALIDATION_DEVICE_EXTRA_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[VALIDATION_DEVICE_EXTRA] = Level.INFO

    # The validation summary report.
    VALIDATION_SUMMARY_REPORT: ClassVar[str] = "validation.summary.report"
    VALIDATION_SUMMARY_REPORT_LEVEL: ClassVar[Level] = Level.INFO
    VALIDATION_SUMMARY_REPORT_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[VALIDATION_SUMMARY_REPORT] = Level.INFO

    # An individual line-item sequence test.
    VALIDATION_FEATURE_SEQUENCE: ClassVar[str] = "validation.feature.sequence"
    VALIDATION_FEATURE_SEQUENCE_LEVEL: ClassVar[Level] = Level.INFO
    VALIDATION_FEATURE_SEQUENCE_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[VALIDATION_FEATURE_SEQUENCE] = Level.INFO

    # Feature message schema validations.
    VALIDATION_FEATURE_SCHEMA: ClassVar[str] = "validation.feature.schema"
    VALIDATION_FEATURE_SCHEMA_LEVEL: ClassVar[Level] = Level.INFO
    VALIDATION_FEATURE_SCHEMA_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[VALIDATION_FEATURE_SCHEMA] = Level.INFO

    # Sequence test capability.
    VALIDATION_FEATURE_CAPABILITY: ClassVar[str] = "validation.feature.capability"
    VALIDATION_FEATURE_CAPABILITY_LEVEL: ClassVar[Level] = Level.INFO
    VALIDATION_FEATURE_CAPABILITY_VALUE: ClassVar[int] = Level.INFO.value
    LEVEL[VALIDATION_FEATURE_CAPABILITY] = Level.INFO

