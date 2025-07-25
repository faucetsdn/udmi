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
  public static final Pattern POINTS_KEY_REGEX = Pattern.compile("pointset\\.points\\..*");
  public static final String EMPTY_STRING = "\"\"";
  public static final String EMPTY_MARKER = "__EMPTY__";
  public static final String DELETE_MARKER = "__DELETE__";

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

  /**
   * Make any empty value from dataFromDisk explicit so that it shows up in BAMBI as the
   * empty string "" instead of an empty cell. This will enable both the user and BambiService
   * to distinguish between a set empty value vs an unset value.
   *
   * @param dataFromDisk key-value data from disk
   */
  public static void makeEmptyValuesExplicit(Map<String, String> dataFromDisk) {
    for (Entry<String, String> entry : dataFromDisk.entrySet()) {
      if (entry.getValue().isEmpty()) {
        dataFromDisk.put(entry.getKey(), EMPTY_STRING);
      }
    }
  }

  /**
   * If a user wants a field to be empty, they can specify that in BAMBI by populating the cell
   * with the empty string - "". For merging dataFromBambi with the data on disk, replace it
   * with a special marker.
   *
   * @param dataFromBambi key-value data received from BAMBI
   */
  public static void handleExplicitlyEmptyValues(Map<String, String> dataFromBambi) {
    for (Entry<String, String> entry : dataFromBambi.entrySet()) {
      if (entry.getValue() != null && entry.getValue().equals(EMPTY_STRING)) {
        dataFromBambi.put(entry.getKey(), EMPTY_MARKER);
      }
    }
  }

}
