package udmi.schema;

import java.util.HashMap;
import java.util.Map;

// This class is manually curated, auto-generated, and then copied into the gencode directory.
// Look for the proper source and don't be fooled! Ultimately sourced from docs/specs/buckets.md

public enum Bucket {

    // IoT connection endpoint management
    ENDPOINT("endpoint"),

    // Endpoint configuration updates
    ENDPOINT_CONFIG("endpoint.config"),

    // Basic device property enumeration capability
    ENUMERATION("enumeration"),

    // Enumerating available points of a device
    ENUMERATION_POINTSET("enumeration.pointset"),

    // Enumerating the features a device supports
    ENUMERATION_FEATURES("enumeration.features"),

    // Enumerating the network families of the device
    ENUMERATION_FAMILIES("enumeration.families"),

    // Automated discovery capabilities
    DISCOVERY("discovery"),

    // Scanning a network for devices
    DISCOVERY_SCAN("discovery.scan"),

    // UDMI gateway capabilities
    GATEWAY("gateway"),

    // Pointset and telemetry capabilities
    POINTSET("pointset"),

    // Basic system operations
    SYSTEM("system"),

    // System mode
    SYSTEM_MODE("system.mode"),

    // Writeback related operations
    WRITEBACK("writeback"),

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
