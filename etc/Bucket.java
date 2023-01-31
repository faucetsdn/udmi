package udmi.schema;

import java.util.HashMap;
import java.util.Map;

// This class is manually curated, auto-generated, and then copied into the gencode directory.
// Look for the proper source and don't be fooled! Ultimately sourced from docs/specs/buckets.md

public enum Bucket {
    @@ gencode stuff goes here
    
    private final String value;

    Bucket(String value) {
        this.value = value;
    }

    public String value() {
    return this.value;
  }
}
