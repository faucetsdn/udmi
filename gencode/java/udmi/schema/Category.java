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


    // System is in the process of (re)starting and essentially offline
    public static final String SYSTEM_BASE_START = "system.base.start";
    public static final Level SYSTEM_BASE_START_LEVEL = NOTICE;
    static { LEVEL.put(SYSTEM_BASE_START, NOTICE); }

    // System is shutting down
    public static final String SYSTEM_BASE_SHUTDOWN = "system.base.shutdown";
    public static final Level SYSTEM_BASE_SHUTDOWN_LEVEL = NOTICE;
    static { LEVEL.put(SYSTEM_BASE_SHUTDOWN, NOTICE); }

    // System is fully ready for operation
    public static final String SYSTEM_BASE_READY = "system.base.ready";
    public static final Level SYSTEM_BASE_READY_LEVEL = NOTICE;
    static { LEVEL.put(SYSTEM_BASE_READY, NOTICE); }

    // Receiving a config message
    public static final String SYSTEM_CONFIG_RECEIVE = "system.config.receive";
    public static final Level SYSTEM_CONFIG_RECEIVE_LEVEL = DEBUG;
    static { LEVEL.put(SYSTEM_CONFIG_RECEIVE, DEBUG); }

    // Parsing a received message
    public static final String SYSTEM_CONFIG_PARSE = "system.config.parse";
    public static final Level SYSTEM_CONFIG_PARSE_LEVEL = DEBUG;
    static { LEVEL.put(SYSTEM_CONFIG_PARSE, DEBUG); }

    // Application of a parsed config message
    public static final String SYSTEM_CONFIG_APPLY = "system.config.apply";
    public static final Level SYSTEM_CONFIG_APPLY_LEVEL = NOTICE;
    static { LEVEL.put(SYSTEM_CONFIG_APPLY, NOTICE); }

    // Connected to the network
    public static final String SYSTEM_NETWORK_CONNECTION = "system.network.connection";
    public static final Level SYSTEM_NETWORK_CONNECTION_LEVEL = NOTICE;
    static { LEVEL.put(SYSTEM_NETWORK_CONNECTION, NOTICE); }

    // Disconnected from a network
    public static final String SYSTEM_NETWORK_DISCONNECT = "system.network.disconnect";
    public static final Level SYSTEM_NETWORK_DISCONNECT_LEVEL = NOTICE;
    static { LEVEL.put(SYSTEM_NETWORK_DISCONNECT, NOTICE); }

    // Successful login. The entry message should include the username and application
    public static final String SYSTEM_AUTH_LOGIN = "system.auth.login";
    public static final Level SYSTEM_AUTH_LOGIN_LEVEL = NOTICE;
    static { LEVEL.put(SYSTEM_AUTH_LOGIN, NOTICE); }

    // Successful logout 
    public static final String SYSTEM_AUTH_LOGOUT = "system.auth.logout";
    public static final Level SYSTEM_AUTH_LOGOUT_LEVEL = NOTICE;
    static { LEVEL.put(SYSTEM_AUTH_LOGOUT, NOTICE); }

    // Failed authentication attempt. The entry message should include the application
    public static final String SYSTEM_AUTH_FAIL = "system.auth.fail";
    public static final Level SYSTEM_AUTH_FAIL_LEVEL = WARNING;
    static { LEVEL.put(SYSTEM_AUTH_FAIL, WARNING); }

    // The `set_value` for a point has been applied
    public static final String POINTSET_POINT_APPLIED = "pointset.point.applied";
    public static final Level POINTSET_POINT_APPLIED_LEVEL = INFO;
    static { LEVEL.put(POINTSET_POINT_APPLIED, INFO); }

    // The point is in the process of updating
    public static final String POINTSET_POINT_UPDATING = "pointset.point.updating";
    public static final Level POINTSET_POINT_UPDATING_LEVEL = NOTICE;
    static { LEVEL.put(POINTSET_POINT_UPDATING, NOTICE); }

    // The reported value has been overridden locally
    public static final String POINTSET_POINT_OVERRIDDEN = "pointset.point.overridden";
    public static final Level POINTSET_POINT_OVERRIDDEN_LEVEL = WARNING;
    static { LEVEL.put(POINTSET_POINT_OVERRIDDEN, WARNING); }

    // The system failed to read/write the point
    public static final String POINTSET_POINT_FAILURE = "pointset.point.failure";
    public static final Level POINTSET_POINT_FAILURE_LEVEL = ERROR;
    static { LEVEL.put(POINTSET_POINT_FAILURE, ERROR); }

    // A `config` parameter for the point is invalid in some way
    public static final String POINTSET_POINT_INVALID = "pointset.point.invalid";
    public static final Level POINTSET_POINT_INVALID_LEVEL = ERROR;
    static { LEVEL.put(POINTSET_POINT_INVALID, ERROR); }

    // Relating to scanning a particular address family
    public static final String DISCOVERY_FAMILY_SCAN = "discovery.family.scan";
    public static final Level DISCOVERY_FAMILY_SCAN_LEVEL = INFO;
    static { LEVEL.put(DISCOVERY_FAMILY_SCAN, INFO); }

    // Handling point enumeration for a given device
    public static final String DISCOVERY_DEVICE_ENUMERATE = "discovery.device.enumerate";
    public static final Level DISCOVERY_DEVICE_ENUMERATE_LEVEL = INFO;
    static { LEVEL.put(DISCOVERY_DEVICE_ENUMERATE, INFO); }

    // Relating to describing a particular point
    public static final String DISCOVERY_POINT_DESCRIBE = "discovery.point.describe";
    public static final Level DISCOVERY_POINT_DESCRIBE_LEVEL = INFO;
    static { LEVEL.put(DISCOVERY_POINT_DESCRIBE, INFO); }

    // About receiving a blob update
    public static final String BLOBSET_BLOB_RECEIVE = "blobset.blob.receive";
    public static final Level BLOBSET_BLOB_RECEIVE_LEVEL = DEBUG;
    static { LEVEL.put(BLOBSET_BLOB_RECEIVE, DEBUG); }

    // Fetching a blob update
    public static final String BLOBSET_BLOB_FETCH = "blobset.blob.fetch";
    public static final Level BLOBSET_BLOB_FETCH_LEVEL = DEBUG;
    static { LEVEL.put(BLOBSET_BLOB_FETCH, DEBUG); }

    // Applying a blob update
    public static final String BLOBSET_BLOB_APPLY = "blobset.blob.apply";
    public static final Level BLOBSET_BLOB_APPLY_LEVEL = NOTICE;
    static { LEVEL.put(BLOBSET_BLOB_APPLY, NOTICE); }
}
