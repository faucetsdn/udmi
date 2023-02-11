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
    return ensureValueMap().containsKey(key);
  }

  public static Bucket fromValue(String key) {
    return ensureValueMap().get(key);
  }

  private static Map<String, Bucket> ensureValueMap() {
    if (allValues.isEmpty()) {
      for (Bucket bucket : values()) {
        allValues.put(bucket.value, bucket);
      }
    }
    return allValues;
  }

  /**
   * Return just the trailing part of th full bucket name. So from
   * "endpoint.mods.gcp" it would return just "gcp", used as a map key.
   */
  public String key() {
    return value.substring(value.lastIndexOf(".") + 1);
  }
}
