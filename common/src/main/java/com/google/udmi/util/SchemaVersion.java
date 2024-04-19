package com.google.udmi.util;

import static com.google.common.base.Preconditions.checkState;

import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;

/**
 * Formalized enums of the UDMI schema version.
 */
public enum SchemaVersion {
  VERSION_1_5_0("1.5.0", 105000),
  VERSION_1_4_2("1.4.2", 104020),
  VERSION_1_4_1("1.4.1", 104010),
  VERSION_1_4_0("1.4.0", 104000),
  VERSION_1_3_14("1.3.14", 103014),
  VERSION_1_3_13("1.3.13", 103013),
  VERSION_1("1", 100000);

  public static final SchemaVersion CURRENT;

  public static final Integer LEGACY_REPLACEMENT = 1;

  private static final Map<String, SchemaVersion> CONSTANTS = new HashMap<>();

  static {
    SimpleEntry<SchemaVersion, Integer> max = new SimpleEntry<>(null, 0);
    for (SchemaVersion c : values()) {
      CONSTANTS.put(c.key, c);
      if (c.value > max.getValue()) {
        max = new SimpleEntry<>(c, c.value);
      }
    }
    CURRENT = max.getKey();
  }

  private final String key;
  private final int value;

  SchemaVersion(String key, int value) {
    this.key = key;
    this.value = value;
  }

  public static SchemaVersion fromKey(String key) {
    checkState(CONSTANTS.containsKey(key), "unrecognized schema version " + key);
    return CONSTANTS.get(key);
  }

  public String key() {
    return key;
  }

  public int value() {
    return value;
  }

}
