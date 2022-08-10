package udmi.schema;

import static udmi.schema.Level.DEBUG;
import static udmi.schema.Level.NOTICE;

// This class is manually curated, auto-generated, and then copied into the gencode directory.
// Look for the proper source and don't be fooled! Ultimately sourced from docs/specs/categories.md
public class Category {

    // System is in the process of (re)starting and essentially offline
    SYSTEM_BASE_START = "system.base.start";
    SYSTEM_BASE_START_LEVEL = NOTICE;

    // System is shutting down
    SYSTEM_BASE_SHUTDOWN = "system.base.shutdown";
    SYSTEM_BASE_SHUTDOWN_LEVEL = NOTICE;

    // System is fully ready for operation
    SYSTEM_BASE_READY = "system.base.ready";
    SYSTEM_BASE_READY_LEVEL = NOTICE;

    // Receiving a config message
    SYSTEM_CONFIG_RECEIVE = "system.config.receive";
    SYSTEM_CONFIG_RECEIVE_LEVEL = DEBUG;

    // Parsing a received message
    SYSTEM_CONFIG_PARSE = "system.config.parse";
    SYSTEM_CONFIG_PARSE_LEVEL = DEBUG;

    // Application of a parsed config message
    SYSTEM_CONFIG_APPLY = "system.config.apply";
    SYSTEM_CONFIG_APPLY_LEVEL = NOTICE;

    // Connected to the network
    SYSTEM_NETWORK_CONNECT = "system.network.connect";
    SYSTEM_NETWORK_CONNECT_LEVEL = NOTICE;

    // Disconnected from a network
    SYSTEM_NETWORK_DISCONNECT = "system.network.disconnect";
    SYSTEM_NETWORK_DISCONNECT_LEVEL = NOTICE;

    // Successful login. The entry message should include the username and application
    SYSTEM_AUTH_LOGIN = "system.auth.login";
    SYSTEM_AUTH_LOGIN_LEVEL = NOTICE;

    // Successful logout 
    SYSTEM_AUTH_LOGOUT = "system.auth.logout";
    SYSTEM_AUTH_LOGOUT_LEVEL = NOTICE;

    // Failed authentication attempt. The entry message should include the application
    SYSTEM_AUTH_FAIL = "system.auth.fail";
    SYSTEM_AUTH_FAIL_LEVEL = WARNING;

    // The `set_value` for a point has been applied
    POINTSET_POINT_APPLIED = "pointset.point.applied";
    POINTSET_POINT_APPLIED_LEVEL = INFO;

    // The point is in the process of updating
    POINTSET_POINT_UPDATING = "pointset.point.updating";
    POINTSET_POINT_UPDATING_LEVEL = NOTICE;

    // The reported value has been overridden locally
    POINTSET_POINT_OVERRIDDEN = "pointset.point.overridden";
    POINTSET_POINT_OVERRIDDEN_LEVEL = WARNING;

    // The system failed to read/write the point
    POINTSET_POINT_FAILURE = "pointset.point.failure";
    POINTSET_POINT_FAILURE_LEVEL = ERROR;

    // A `config` parameter for the point is invalid in some way
    POINTSET_POINT_INVALID = "pointset.point.invalid";
    POINTSET_POINT_INVALID_LEVEL = ERROR;

    // Relating to scanning a particular address family
    DISCOVERY_FAMILY_SCAN = "discovery.family.scan";
    DISCOVERY_FAMILY_SCAN_LEVEL = INFO;

    // Handling point enumeration for a given device
    DISCOVERY_DEVICE_ENUMERATE = "discovery.device.enumerate";
    DISCOVERY_DEVICE_ENUMERATE_LEVEL = INFO;

    // Relating to describing a particular point
    DISCOVERY_POINT_DESCRIBE = "discovery.point.describe";
    DISCOVERY_POINT_DESCRIBE_LEVEL = INFO;

    // Request for an update has been received
    BLOBSET_BLOB_RECEIVED = "blobset.blob.received";
    BLOBSET_BLOB_RECEIVED_LEVEL = DEBUG;

    // Update blob has been successfully fetched
    BLOBSET_BLOB_FETCHED = "blobset.blob.fetched";
    BLOBSET_BLOB_FETCHED_LEVEL = DEBUG;

    // Update has been successfully applied
    BLOBSET_BLOB_APPLIED = "blobset.blob.applied";
    BLOBSET_BLOB_APPLIED_LEVEL = NOTICE;
}
