package com.google.bos.iot.core.bambi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utils defined to handle how specific fields should be rendered on the UI and processed by
 * the backend.
 * TODO: In the next phase, this should be controlled by a "Presentation Layer".
 */
public class Utils {

  public static final Pattern LIST_TYPE_REGEX = Pattern.compile("\\[.*]");
  public static final Set<String> LIST_TYPE_HEADERS = Set.of(
      "gateway.proxy_ids",
      "system.tags",
      "tags"
  );
  public static final Set<Pattern> NON_NUMERIC_HEADERS_REGEX = Set.of(
      Pattern.compile("pointset\\.points\\..*\\.ref"),
      Pattern.compile("localnet\\.families\\..*\\.addr")
  );

  /**
   * Remove brackets from specific keys where values are lists to reflect on the UI as a
   * comma-separated list without square brackets.
   */
  public static Map<String, String> removeBracketsFromListValues(Map<String, String> map) {
    Map<String, String> output = new LinkedHashMap<>(map);
    for (Entry<String, String> entry : output.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (LIST_TYPE_HEADERS.contains(key) && value.matches(LIST_TYPE_REGEX.pattern())) {
        value = value.substring(1, value.length() - 1);
      }
      output.put(key, value);
    }
    return output;
  }


  /**
   * Populates the map, expanding comma-separated values for specific keys
   * while preserving the order of elements.
   */
  public static Map<String, String> handleArraysInMap(Map<String, String> metadataMap) {
    Map<String, String> result = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : metadataMap.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (LIST_TYPE_HEADERS.contains(key)) {
        String[] arrayValues = value.split(",");
        for (int i = 0; i < arrayValues.length; i++) {
          result.put(key + "." + i, arrayValues[i].trim());
        }
      } else {
        result.put(key, value);
      }
    }
    return result;
  }

}
