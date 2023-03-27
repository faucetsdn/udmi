package com.google.daq.mqtt.util;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.sequencer.semantic.SemanticValue;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import udmi.schema.State;

/**
 * Utility class to help with detecting differences in object fields.
 */
public class ObjectDiffEngine {

  private Map<String, Object> previous;
  private boolean ignoreSemantics;

  public ObjectDiffEngine() {
  }

  /**
   * Compute the changes in an object from the previous version, as stored in the class.
   *
   * @param updatedObject new object
   * @return list of differences against the previous object
   */
  public List<String> computeChanges(Object updatedObject) {
    // TODO: Hack alert! State should be handled semantically, but for now show raw changes.
    ignoreSemantics = updatedObject instanceof State;
    Map<String, Object> updated = extractDefinitions(updatedObject);
    List<String> updates = new ArrayList<>();
    accumulateDifference("", previous, updated, updates);
    previous = updated;
    return updates;
  }

  /**
   * Just quietly reset the internal state. No need for any diff computation.
   *
   * @param updatedObject new object state
   */
  public void resetState(Object updatedObject) {
    previous = extractDefinitions(updatedObject);
  }

  private Map<String, Object> extractValues(Object thing) {
    return traverseExtract(thing, true);
  }

  private Map<String, Object> extractDefinitions(Object thing) {
    return traverseExtract(thing, false);
  }

  private Map<String, Object> traverseExtract(Object thing, boolean asValues) {
    if (thing == null) {
      return ImmutableMap.of();
    }
    if (thing instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> asMap = (Map<String, Object>) thing;
      return asMap.keySet().stream()
          .collect(Collectors.toMap(key -> key, key -> traverseExtract(asMap.get(key), asValues)));
    }
    return Arrays.stream(thing.getClass().getFields())
        .filter(field -> isNotNull(thing, field)).collect(
            Collectors.toMap(Field::getName, field -> extractField(thing, field, asValues)));
  }

  private Object extractField(Object thing, Field field, boolean asValue) {
    try {
      Object result = field.get(thing);
      if (isBaseType(field) || isBaseType(result)) {
        boolean useValue = asValue || !SemanticValue.isSemanticValue(result);
        return useValue ? SemanticValue.getValue(result) : SemanticValue.getDescription(result);
      } else {
        return traverseExtract(result, asValue);
      }
    } catch (Exception e) {
      throw new RuntimeException("While converting field " + field.getName(), e);
    }
  }

  private boolean isNotNull(Object thing, Field field) {
    try {
      return field.get(thing) != null;
    } catch (Exception e) {
      throw new RuntimeException("While checking for null " + field.getName(), e);
    }
  }

  private boolean isBaseType(Object value) {
    Class<?> type = value instanceof Field ? ((Field) value).getType() : value.getClass();
    return type.isPrimitive()
        || type.isEnum()
        || Boolean.class.isAssignableFrom(type)
        || Integer.class.isAssignableFrom(type)
        || String.class.isAssignableFrom(type)
        || Date.class.isAssignableFrom(type);
  }

  @SuppressWarnings("unchecked")
  void accumulateDifference(String prefix, Map<String, Object> left, Map<String, Object> right,
      List<String> updates) {
    right.forEach((key, value) -> {
      if (left != null && left.containsKey(key)) {
        Object leftValue = left.get(key);
        if (SemanticValue.equals(value, leftValue)) {
          return;
        }
        if (isBaseType(value)) {
          updates.add(String.format("Set `%s%s` = %s", prefix, key, semanticValue(value)));
        } else {
          String newPrefix = prefix + key + ".";
          accumulateDifference(newPrefix, (Map<String, Object>) leftValue,
              (Map<String, Object>) value, updates);
        }
      } else {
        updates.add(String.format("Add `%s%s` = %s", prefix, key, semanticValue(value)));
      }
    });
    if (left != null) {
      left.forEach((key, value) -> {
        if (!right.containsKey(key)) {
          updates.add(String.format("Remove `%s%s`", prefix, key));
        }
      });
    }
  }

  private String singleLine(String toJsonString) {
    return Joiner.on(' ').join(
        Arrays.stream(toJsonString.split("\n")).map(String::trim).collect(Collectors.toList()));
  }

  @SuppressWarnings("unchecked")
  private String semanticValue(Object value) {
    if (value instanceof Map) {
      return semanticMapValue((Map<String, Object>) value);
    }
    boolean isSemantic = SemanticValue.isSemanticValue(value);
    if (!ignoreSemantics && value instanceof Date && !isSemantic) {
      throw new IllegalArgumentException(
          "Unexpected non-semantic Date in semantic value calculation");
    }
    String wrapper = isSemantic ? "_" : "`";
    return wrapper + (isSemantic ? SemanticValue.getDescription(value) : value) + wrapper;
  }

  private String semanticMapValue(Map<String, Object> map) {
    String contents = map.keySet().stream()
        .map(key -> String.format("\"%s\": %s", key, semanticMapValue(map, key))).collect(
            Collectors.joining(", "));
    return String.format("{ %s }", contents);
  }

  @SuppressWarnings("unchecked")
  private String semanticMapValue(Map<String, Object> map, String key) {
    return semanticValue(map.get(key));
  }

  /**
   * Compare two objects, taking into consideration any semantic tags/values.
   *
   * @param oneObject one object to compare
   * @param twoObject second object to compare
   * @return equality comparison with semantic considerations
   */
  public boolean equals(Object oneObject, Object twoObject) {
    return diff(oneObject, twoObject).isEmpty();
  }

  /**
   * Return the accumulated differences between two objects.
   *
   * @param startObject starting object
   * @param endObject   ending object
   * @return list of differences going from start to end objects
   */
  public List<String> diff(Object startObject, Object endObject) {
    Map<String, Object> left = extractValues(startObject);
    Map<String, Object> right = extractValues(endObject);
    List<String> updates = new ArrayList<>();
    accumulateDifference("", left, right, updates);
    return updates;
  }
}