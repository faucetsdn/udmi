package udmi.schema;

import java.util.HashMap;
import java.util.Map;

// This class is manually curated, auto-generated, and then copied into the gencode directory.
// Look for the proper source and don't be fooled! Ultimately sourced from docs/specs/buckets.md

public enum Bucket {

    // Basic platform property enumeration
    ENUMERATION("enumeration"),

    // Conditions specific to an entire address family (e.g. bacnet)
    ENUMERATION_POINTSET("enumeration.pointset"),

    // Conditions specific to an entire address family (e.g. bacnet)
    ENUMERATION_FEATURES("enumeration.features"),

    // Conditions specific to an entire address family (e.g. bacnet)
    ENUMERATION_FAMILIES("enumeration.families"),

    // Handling on-prem discovery flow
    DISCOVERY("discovery"),

    // (**INFO**) Relating to scanning a particular address family
    DISCOVERY_SCAN("discovery.scan"),

    // Handling on-prem discovery flow
    ENDPOINT("endpoint"),

    // Handling on-prem discovery flow
    ENDPOINT_CONFIG("endpoint.config"),

    // Basic system operations
    SYSTEM("system"),

    // System mode testing
    SYSTEM_MODE("system.mode"),

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
