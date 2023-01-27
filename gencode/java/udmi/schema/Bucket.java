package udmi.schema;

import java.util.HashMap;
import java.util.Map;

// This class is manually curated, auto-generated, and then copied into the gencode directory.
// Look for the proper source and don't be fooled! Ultimately sourced from docs/specs/buckets.md

public enum Bucket {

    // Basic system operation
    SYSTEM("system"),

    // Baseline system operational messages
    SYSTEM_BASE("system.base"),

    // (**NOTICE**) System is in the process of (re)starting and essentially offline
    SYSTEM_BASE_START("system.base.start"),

    // (**NOTICE**) System is shutting down
    SYSTEM_BASE_SHUTDOWN("system.base.shutdown"),

    // (**NOTICE**) System is fully ready for operation
    SYSTEM_BASE_READY("system.base.ready"),

    // Baseline message handling
    SYSTEM_BASE_COMMS("system.base.comms"),

    // Configuration message handling
    SYSTEM_CONFIG("system.config"),

    // (**DEBUG**) Receiving a config message
    SYSTEM_CONFIG_RECEIVE("system.config.receive"),

    // (**DEBUG**) Parsing a received message
    SYSTEM_CONFIG_PARSE("system.config.parse"),

    // (**NOTICE**) Application of a parsed config message
    SYSTEM_CONFIG_APPLY("system.config.apply"),

    // Network (IP) message handling
    SYSTEM_NETWORK("system.network"),

    // (**NOTICE**) Connected to the network
    SYSTEM_NETWORK_CONNECTION("system.network.connection"),

    // (**NOTICE**) Disconnected from a network
    SYSTEM_NETWORK_DISCONNECT("system.network.disconnect"),

    // Authentication to local application (e.g. web server, SSH)
    SYSTEM_AUTH("system.auth"),

    // (**NOTICE**) Successful login. The entry message should include the username and application
    SYSTEM_AUTH_LOGIN("system.auth.login"),

    // (**NOTICE**) Successful logout 
    SYSTEM_AUTH_LOGOUT("system.auth.logout"),

    // (**WARNING**) Failed authentication attempt. The entry message should include the application
    SYSTEM_AUTH_FAIL("system.auth.fail"),

    // Handling managing data point conditions
    POINTSET("pointset"),

    // Conditions relating to a specific point, the entry `message` should start with "Point _pointname_"
    POINTSET_POINT("pointset.point"),

    // (**INFO**) The `set_value` for a point has been applied
    POINTSET_POINT_APPLIED("pointset.point.applied"),

    // (**NOTICE**) The point is in the process of updating
    POINTSET_POINT_UPDATING("pointset.point.updating"),

    // (**WARNING**) The reported value has been overridden locally
    POINTSET_POINT_OVERRIDDEN("pointset.point.overridden"),

    // (**ERROR**) The system failed to read/write the point
    POINTSET_POINT_FAILURE("pointset.point.failure"),

    // (**ERROR**) A `config` parameter for the point is invalid in some way
    POINTSET_POINT_INVALID("pointset.point.invalid"),

    // Handling on-prem discovery flow
    DISCOVERY("discovery"),

    // Conditions specific to an entire address family (e.g. bacnet)
    DISCOVERY_FAMILY("discovery.family"),

    // (**INFO**) Relating to scanning a particular address family
    DISCOVERY_FAMILY_SCAN("discovery.family.scan"),

    // Conditions specific to device scanning
    DISCOVERY_DEVICE("discovery.device"),

    // (**INFO**) Handling point enumeration for a given device
    DISCOVERY_DEVICE_ENUMERATE("discovery.device.enumerate"),

    // Conditions specific to point enumeration
    DISCOVERY_POINT("discovery.point"),

    // (**INFO**) Relating to describing a particular point
    DISCOVERY_POINT_DESCRIBE("discovery.point.describe"),

    // Mapping processing for devices
    MAPPING("mapping"),

    // Relating to a specific individual device
    MAPPING_DEVICE("mapping.device"),

    // (**INFO**) Stage of applying a device mapping
    MAPPING_DEVICE_APPLY("mapping.device.apply"),

    // Handling update of device data blobs
    BLOBSET("blobset"),

    // Conditions specific to an individual blob
    BLOBSET_BLOB("blobset.blob"),

    // (**DEBUG**) About receiving a blob update
    BLOBSET_BLOB_RECEIVE("blobset.blob.receive"),

    // (**DEBUG**) Fetching a blob update
    BLOBSET_BLOB_FETCH("blobset.blob.fetch"),

    // (**NOTICE**) Applying a blob update
    BLOBSET_BLOB_APPLY("blobset.blob.apply"),

    // Handling validation pipeline messages
    VALIDATION("validation"),

    // Conditions specific to processing a given device message.
    VALIDATION_DEVICE("validation.device"),

    // (**DEBUG**) Receiving/processing a message for validation.
    VALIDATION_DEVICE_RECEIVE("validation.device.receive"),

    // (**INFO**) Basic schema and structure validation.
    VALIDATION_DEVICE_SCHEMA("validation.device.schema"),

    // (**INFO**) Errors validating semantic content of the message.
    VALIDATION_DEVICE_CONTENT("validation.device.content"),

    // (**INFO**) Multiple issues reported.
    VALIDATION_DEVICE_MULTIPLE("validation.device.multiple"),

    // Conditions specific to an overall site summary.
    VALIDATION_SUMMARY("validation.summary"),

    // (**INFO**) The validation summary report.
    VALIDATION_SUMMARY_REPORT("validation.summary.report"),

    // unknown default value
    UNKNOWN_DEFAULT("unknown");
    
    private final String value;

    Bucket(String value) {
        this.value = value;
    }

    public String value() {
    return this.value;
  }
}
