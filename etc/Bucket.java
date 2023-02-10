package udmi.schema;

import java.util.HashMap;
import java.util.Map;

// This class is manually curated, auto-generated, and then copied into the gencode directory.
// Look for the proper source and don't be fooled! Ultimately sourced from docs/specs/buckets.md

public enum Bucket {
    @@ gencode stuff goes here

    private final String value;

    private static final Map<String, Bucket> allValues = new HashMap<>();

    Bucket(String value) {
        this.value = value;
    }

    public String value() {
    return this.value;
  }

    public static boolean contains(String key) {
      if (allValues.isEmpty()) {
        for (Bucket bucket : values()) {
          allValues.put(bucket.value, bucket);
        }
      }
      return allValues.containsKey(key);
    }
}
