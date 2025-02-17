package udmi.schema;

import static udmi.schema.Level.DEBUG;
import static udmi.schema.Level.INFO;
import static udmi.schema.Level.NOTICE;
import static udmi.schema.Level.WARNING;
import static udmi.schema.Level.ERROR;

import java.util.HashMap;
import java.util.Map;

// This class is manually curated, auto-generated, and then copied into the gencode directory.
// Look for the proper source and don't be fooled! Ultimately sourced from docs/specs/categories.md
public class Category {
    public static final Map<String, Level> LEVEL = new HashMap<>();


    // System is in the process of (re)starting and essentially offline
    public static final String SYSTEM_BASE_START = "system.base.start";
    public static final Level SYSTEM_BASE_START_LEVEL = NOTICE;
    public static final int SYSTEM_BASE_START_VALUE = NOTICE.value();
    static { LEVEL.put(SYSTEM_BASE_START, NOTICE); }

    // System is shutting down
    public static final String SYSTEM_BASE_SHUTDOWN = "system.base.shutdown";
    public static final Level SYSTEM_BASE_SHUTDOWN_LEVEL = NOTICE;
    public static final int SYSTEM_BASE_SHUTDOWN_VALUE = NOTICE.value();
    static { LEVEL.put(SYSTEM_BASE_SHUTDOWN, NOTICE); }

    // System is fully ready for operation
    public static final String SYSTEM_BASE_READY = "system.base.ready";
    public static final Level SYSTEM_BASE_READY_LEVEL = NOTICE;
    public static final int SYSTEM_BASE_READY_VALUE = NOTICE.value();
    static { LEVEL.put(SYSTEM_BASE_READY, NOTICE); }

    // Receiving a config message
    public static final String SYSTEM_CONFIG_RECEIVE = "system.config.receive";
    public static final Level SYSTEM_CONFIG_RECEIVE_LEVEL = DEBUG;
    public static final int SYSTEM_CONFIG_RECEIVE_VALUE = DEBUG.value();
    static { LEVEL.put(SYSTEM_CONFIG_RECEIVE, DEBUG); }

    // Parsing a received message
    public static final String SYSTEM_CONFIG_PARSE = "system.config.parse";
    public static final Level SYSTEM_CONFIG_PARSE_LEVEL = DEBUG;
    public static final int SYSTEM_CONFIG_PARSE_VALUE = DEBUG.value();
    static { LEVEL.put(SYSTEM_CONFIG_PARSE, DEBUG); }

    // Application of a parsed config message
    public static final String SYSTEM_CONFIG_APPLY = "system.config.apply";
    public static final Level SYSTEM_CONFIG_APPLY_LEVEL = NOTICE;
    public static final int SYSTEM_CONFIG_APPLY_VALUE = NOTICE.value();
    static { LEVEL.put(SYSTEM_CONFIG_APPLY, NOTICE); }

    // Successful login. The entry message should include the username and application
    public static final String SYSTEM_AUTH_LOGIN = "system.auth.login";
    public static final Level SYSTEM_AUTH_LOGIN_LEVEL = NOTICE;
    public static final int SYSTEM_AUTH_LOGIN_VALUE = NOTICE.value();
    static { LEVEL.put(SYSTEM_AUTH_LOGIN, NOTICE); }

    // Successful logout 
    public static final String SYSTEM_AUTH_LOGOUT = "system.auth.logout";
    public static final Level SYSTEM_AUTH_LOGOUT_LEVEL = NOTICE;
    public static final int SYSTEM_AUTH_LOGOUT_VALUE = NOTICE.value();
    static { LEVEL.put(SYSTEM_AUTH_LOGOUT, NOTICE); }

    // Failed authentication attempt. The entry message should include the application
    public static final String SYSTEM_AUTH_FAIL = "system.auth.fail";
    public static final Level SYSTEM_AUTH_FAIL_LEVEL = WARNING;
    public static final int SYSTEM_AUTH_FAIL_VALUE = WARNING.value();
    static { LEVEL.put(SYSTEM_AUTH_FAIL, WARNING); }

    // Category for normal operating state (also default).
    public static final String POINTSET_POINT_NOMINAL = "pointset.point.nominal";
    public static final Level POINTSET_POINT_NOMINAL_LEVEL = INFO;
    public static final int POINTSET_POINT_NOMINAL_VALUE = INFO.value();
    static { LEVEL.put(POINTSET_POINT_NOMINAL, INFO); }

    // The `set_value` for a point has been applied
    public static final String POINTSET_POINT_APPLIED = "pointset.point.applied";
    public static final Level POINTSET_POINT_APPLIED_LEVEL = INFO;
    public static final int POINTSET_POINT_APPLIED_VALUE = INFO.value();
    static { LEVEL.put(POINTSET_POINT_APPLIED, INFO); }

    // The point is in the process of updating
    public static final String POINTSET_POINT_UPDATING = "pointset.point.updating";
    public static final Level POINTSET_POINT_UPDATING_LEVEL = NOTICE;
    public static final int POINTSET_POINT_UPDATING_VALUE = NOTICE.value();
    static { LEVEL.put(POINTSET_POINT_UPDATING, NOTICE); }

    // The reported value has been overridden locally
    public static final String POINTSET_POINT_OVERRIDDEN = "pointset.point.overridden";
    public static final Level POINTSET_POINT_OVERRIDDEN_LEVEL = WARNING;
    public static final int POINTSET_POINT_OVERRIDDEN_VALUE = WARNING.value();
    static { LEVEL.put(POINTSET_POINT_OVERRIDDEN, WARNING); }

    // The system failed to read/write the point
    public static final String POINTSET_POINT_FAILURE = "pointset.point.failure";
    public static final Level POINTSET_POINT_FAILURE_LEVEL = ERROR;
    public static final int POINTSET_POINT_FAILURE_VALUE = ERROR.value();
    static { LEVEL.put(POINTSET_POINT_FAILURE, ERROR); }

    // A `config` parameter for the point is invalid in some way
    public static final String POINTSET_POINT_INVALID = "pointset.point.invalid";
    public static final Level POINTSET_POINT_INVALID_LEVEL = ERROR;
    public static final int POINTSET_POINT_INVALID_VALUE = ERROR.value();
    static { LEVEL.put(POINTSET_POINT_INVALID, ERROR); }

    // Aspects of a specific network
    public static final String LOCALNET_NETWORK = "localnet.network";
    public static final Level LOCALNET_NETWORK_LEVEL = INFO;
    public static final int LOCALNET_NETWORK_VALUE = INFO.value();
    static { LEVEL.put(LOCALNET_NETWORK, INFO); }

    // Connected status of the device on 
    public static final String LOCALNET_NETWORK_CONNECT = "localnet.network.connect";
    public static final Level LOCALNET_NETWORK_CONNECT_LEVEL = NOTICE;
    public static final int LOCALNET_NETWORK_CONNECT_VALUE = NOTICE.value();
    static { LEVEL.put(LOCALNET_NETWORK_CONNECT, NOTICE); }

    // Expected status for attachment failures between gateway and cloud
    public static final String GATEWAY_SETUP_ATTACH = "gateway.setup.attach";
    public static final Level GATEWAY_SETUP_ATTACH_LEVEL = ERROR;
    public static final int GATEWAY_SETUP_ATTACH_VALUE = ERROR.value();
    static { LEVEL.put(GATEWAY_SETUP_ATTACH, ERROR); }

    // Basic target block specification, missing (warning) or unprocessable (error)
    public static final String GATEWAY_PROXY_TARGET = "gateway.proxy.target";
    public static final Level GATEWAY_PROXY_TARGET_LEVEL = WARNING;
    public static final int GATEWAY_PROXY_TARGET_VALUE = WARNING.value();
    static { LEVEL.put(GATEWAY_PROXY_TARGET, WARNING); }

    // Fieldbus connection between gateway and proxied device (error on failure)
    public static final String GATEWAY_PROXY_CONNECT = "gateway.proxy.connect";
    public static final Level GATEWAY_PROXY_CONNECT_LEVEL = ERROR;
    public static final int GATEWAY_PROXY_CONNECT_VALUE = ERROR.value();
    static { LEVEL.put(GATEWAY_PROXY_CONNECT, ERROR); }

    // Relating to scanning a particular address family
    public static final String DISCOVERY_FAMILY_SCAN = "discovery.family.scan";
    public static final Level DISCOVERY_FAMILY_SCAN_LEVEL = INFO;
    public static final int DISCOVERY_FAMILY_SCAN_VALUE = INFO.value();
    static { LEVEL.put(DISCOVERY_FAMILY_SCAN, INFO); }

    // Handling point enumeration for a given device
    public static final String DISCOVERY_DEVICE_ENUMERATE = "discovery.device.enumerate";
    public static final Level DISCOVERY_DEVICE_ENUMERATE_LEVEL = INFO;
    public static final int DISCOVERY_DEVICE_ENUMERATE_VALUE = INFO.value();
    static { LEVEL.put(DISCOVERY_DEVICE_ENUMERATE, INFO); }

    // Relating to describing a particular point
    public static final String DISCOVERY_POINT_DESCRIBE = "discovery.point.describe";
    public static final Level DISCOVERY_POINT_DESCRIBE_LEVEL = INFO;
    public static final int DISCOVERY_POINT_DESCRIBE_VALUE = INFO.value();
    static { LEVEL.put(DISCOVERY_POINT_DESCRIBE, INFO); }

    // Stage of applying a device mapping
    public static final String MAPPING_DEVICE_APPLY = "mapping.device.apply";
    public static final Level MAPPING_DEVICE_APPLY_LEVEL = INFO;
    public static final int MAPPING_DEVICE_APPLY_VALUE = INFO.value();
    static { LEVEL.put(MAPPING_DEVICE_APPLY, INFO); }

    // About receiving a blob update
    public static final String BLOBSET_BLOB_RECEIVE = "blobset.blob.receive";
    public static final Level BLOBSET_BLOB_RECEIVE_LEVEL = DEBUG;
    public static final int BLOBSET_BLOB_RECEIVE_VALUE = DEBUG.value();
    static { LEVEL.put(BLOBSET_BLOB_RECEIVE, DEBUG); }

    // Fetching a blob update
    public static final String BLOBSET_BLOB_FETCH = "blobset.blob.fetch";
    public static final Level BLOBSET_BLOB_FETCH_LEVEL = DEBUG;
    public static final int BLOBSET_BLOB_FETCH_VALUE = DEBUG.value();
    static { LEVEL.put(BLOBSET_BLOB_FETCH, DEBUG); }

    // Applying a blob update
    public static final String BLOBSET_BLOB_APPLY = "blobset.blob.apply";
    public static final Level BLOBSET_BLOB_APPLY_LEVEL = NOTICE;
    public static final int BLOBSET_BLOB_APPLY_VALUE = NOTICE.value();
    static { LEVEL.put(BLOBSET_BLOB_APPLY, NOTICE); }

    // Receiving/processing a message for validation.
    public static final String VALIDATION_DEVICE_RECEIVE = "validation.device.receive";
    public static final Level VALIDATION_DEVICE_RECEIVE_LEVEL = DEBUG;
    public static final int VALIDATION_DEVICE_RECEIVE_VALUE = DEBUG.value();
    static { LEVEL.put(VALIDATION_DEVICE_RECEIVE, DEBUG); }

    // Basic schema and structure validation.
    public static final String VALIDATION_DEVICE_SCHEMA = "validation.device.schema";
    public static final Level VALIDATION_DEVICE_SCHEMA_LEVEL = INFO;
    public static final int VALIDATION_DEVICE_SCHEMA_VALUE = INFO.value();
    static { LEVEL.put(VALIDATION_DEVICE_SCHEMA, INFO); }

    // Errors validating semantic content of the message.
    public static final String VALIDATION_DEVICE_CONTENT = "validation.device.content";
    public static final Level VALIDATION_DEVICE_CONTENT_LEVEL = INFO;
    public static final int VALIDATION_DEVICE_CONTENT_VALUE = INFO.value();
    static { LEVEL.put(VALIDATION_DEVICE_CONTENT, INFO); }

    // Multiple issues reported.
    public static final String VALIDATION_DEVICE_MULTIPLE = "validation.device.multiple";
    public static final Level VALIDATION_DEVICE_MULTIPLE_LEVEL = INFO;
    public static final int VALIDATION_DEVICE_MULTIPLE_VALUE = INFO.value();
    static { LEVEL.put(VALIDATION_DEVICE_MULTIPLE, INFO); }

    // Device was unexpected (not in site model).
    public static final String VALIDATION_DEVICE_EXTRA = "validation.device.extra";
    public static final Level VALIDATION_DEVICE_EXTRA_LEVEL = INFO;
    public static final int VALIDATION_DEVICE_EXTRA_VALUE = INFO.value();
    static { LEVEL.put(VALIDATION_DEVICE_EXTRA, INFO); }

    // The validation summary report.
    public static final String VALIDATION_SUMMARY_REPORT = "validation.summary.report";
    public static final Level VALIDATION_SUMMARY_REPORT_LEVEL = INFO;
    public static final int VALIDATION_SUMMARY_REPORT_VALUE = INFO.value();
    static { LEVEL.put(VALIDATION_SUMMARY_REPORT, INFO); }

    // An individual line-item sequence test.
    public static final String VALIDATION_FEATURE_SEQUENCE = "validation.feature.sequence";
    public static final Level VALIDATION_FEATURE_SEQUENCE_LEVEL = INFO;
    public static final int VALIDATION_FEATURE_SEQUENCE_VALUE = INFO.value();
    static { LEVEL.put(VALIDATION_FEATURE_SEQUENCE, INFO); }

    // Feature message schema validations.
    public static final String VALIDATION_FEATURE_SCHEMA = "validation.feature.schema";
    public static final Level VALIDATION_FEATURE_SCHEMA_LEVEL = INFO;
    public static final int VALIDATION_FEATURE_SCHEMA_VALUE = INFO.value();
    static { LEVEL.put(VALIDATION_FEATURE_SCHEMA, INFO); }

    // Sequence test capability.
    public static final String VALIDATION_FEATURE_CAPABILITY = "validation.feature.capability";
    public static final Level VALIDATION_FEATURE_CAPABILITY_LEVEL = INFO;
    public static final int VALIDATION_FEATURE_CAPABILITY_VALUE = INFO.value();
    static { LEVEL.put(VALIDATION_FEATURE_CAPABILITY, INFO); }
}
