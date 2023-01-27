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
    public static final Map<String, Level> LEVEL = new HashMap();


    // Basic system operation
    public static final String SYSTEM = "system";

    // Baseline system operational messages
    public static final String SYSTEM_BASE = "system.base";

    // (**NOTICE**) System is in the process of (re)starting and essentially offline
    public static final String SYSTEM_BASE_START = "system.base.start";

    // (**NOTICE**) System is shutting down
    public static final String SYSTEM_BASE_SHUTDOWN = "system.base.shutdown";

    // (**NOTICE**) System is fully ready for operation
    public static final String SYSTEM_BASE_READY = "system.base.ready";

    // Baseline message handling
    public static final String SYSTEM_BASE_COMMS = "system.base.comms";

    // Configuration message handling
    public static final String SYSTEM_CONFIG = "system.config";

    // (**DEBUG**) Receiving a config message
    public static final String SYSTEM_CONFIG_RECEIVE = "system.config.receive";

    // (**DEBUG**) Parsing a received message
    public static final String SYSTEM_CONFIG_PARSE = "system.config.parse";

    // (**NOTICE**) Application of a parsed config message
    public static final String SYSTEM_CONFIG_APPLY = "system.config.apply";

    // Network (IP) message handling
    public static final String SYSTEM_NETWORK = "system.network";

    // (**NOTICE**) Connected to the network
    public static final String SYSTEM_NETWORK_CONNECTION = "system.network.connection";

    // (**NOTICE**) Disconnected from a network
    public static final String SYSTEM_NETWORK_DISCONNECT = "system.network.disconnect";

    // Authentication to local application (e.g. web server, SSH)
    public static final String SYSTEM_AUTH = "system.auth";

    // (**NOTICE**) Successful login. The entry message should include the username and application
    public static final String SYSTEM_AUTH_LOGIN = "system.auth.login";

    // (**NOTICE**) Successful logout 
    public static final String SYSTEM_AUTH_LOGOUT = "system.auth.logout";

    // (**WARNING**) Failed authentication attempt. The entry message should include the application
    public static final String SYSTEM_AUTH_FAIL = "system.auth.fail";

    // Handling managing data point conditions
    public static final String POINTSET = "pointset";

    // Conditions relating to a specific point, the entry `message` should start with "Point _pointname_"
    public static final String POINTSET_POINT = "pointset.point";

    // (**INFO**) The `set_value` for a point has been applied
    public static final String POINTSET_POINT_APPLIED = "pointset.point.applied";

    // (**NOTICE**) The point is in the process of updating
    public static final String POINTSET_POINT_UPDATING = "pointset.point.updating";

    // (**WARNING**) The reported value has been overridden locally
    public static final String POINTSET_POINT_OVERRIDDEN = "pointset.point.overridden";

    // (**ERROR**) The system failed to read/write the point
    public static final String POINTSET_POINT_FAILURE = "pointset.point.failure";

    // (**ERROR**) A `config` parameter for the point is invalid in some way
    public static final String POINTSET_POINT_INVALID = "pointset.point.invalid";

    // Handling on-prem discovery flow
    public static final String DISCOVERY = "discovery";

    // Conditions specific to an entire address family (e.g. bacnet)
    public static final String DISCOVERY_FAMILY = "discovery.family";

    // (**INFO**) Relating to scanning a particular address family
    public static final String DISCOVERY_FAMILY_SCAN = "discovery.family.scan";

    // Conditions specific to device scanning
    public static final String DISCOVERY_DEVICE = "discovery.device";

    // (**INFO**) Handling point enumeration for a given device
    public static final String DISCOVERY_DEVICE_ENUMERATE = "discovery.device.enumerate";

    // Conditions specific to point enumeration
    public static final String DISCOVERY_POINT = "discovery.point";

    // (**INFO**) Relating to describing a particular point
    public static final String DISCOVERY_POINT_DESCRIBE = "discovery.point.describe";

    // Mapping processing for devices
    public static final String MAPPING = "mapping";

    // Relating to a specific individual device
    public static final String MAPPING_DEVICE = "mapping.device";

    // (**INFO**) Stage of applying a device mapping
    public static final String MAPPING_DEVICE_APPLY = "mapping.device.apply";

    // Handling update of device data blobs
    public static final String BLOBSET = "blobset";

    // Conditions specific to an individual blob
    public static final String BLOBSET_BLOB = "blobset.blob";

    // (**DEBUG**) About receiving a blob update
    public static final String BLOBSET_BLOB_RECEIVE = "blobset.blob.receive";

    // (**DEBUG**) Fetching a blob update
    public static final String BLOBSET_BLOB_FETCH = "blobset.blob.fetch";

    // (**NOTICE**) Applying a blob update
    public static final String BLOBSET_BLOB_APPLY = "blobset.blob.apply";

    // Handling validation pipeline messages
    public static final String VALIDATION = "validation";

    // Conditions specific to processing a given device message.
    public static final String VALIDATION_DEVICE = "validation.device";

    // (**DEBUG**) Receiving/processing a message for validation.
    public static final String VALIDATION_DEVICE_RECEIVE = "validation.device.receive";

    // (**INFO**) Basic schema and structure validation.
    public static final String VALIDATION_DEVICE_SCHEMA = "validation.device.schema";

    // (**INFO**) Errors validating semantic content of the message.
    public static final String VALIDATION_DEVICE_CONTENT = "validation.device.content";

    // (**INFO**) Multiple issues reported.
    public static final String VALIDATION_DEVICE_MULTIPLE = "validation.device.multiple";

    // Conditions specific to an overall site summary.
    public static final String VALIDATION_SUMMARY = "validation.summary";

    // (**INFO**) The validation summary report.
    public static final String VALIDATION_SUMMARY_REPORT = "validation.summary.report";
}
