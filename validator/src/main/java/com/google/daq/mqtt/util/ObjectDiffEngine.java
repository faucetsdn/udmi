package com.google.daq.mqtt.util;

import static com.google.udmi.util.DiffEntry.DiffAction.ADD;
import static com.google.udmi.util.DiffEntry.DiffAction.REMOVE;
import static com.google.udmi.util.DiffEntry.DiffAction.SET;
import static com.google.udmi.util.JsonUtil.isoConvert;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.sequencer.semantic.SemanticList;
import com.google.daq.mqtt.sequencer.semantic.SemanticValue;
import com.google.udmi.util.DiffEntry;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import udmi.schema.State;

/**
 * Utility class to help with detecting differences in object fields.
 */
public class ObjectDiffEngine {

  private final Map<String, String> descriptions = new HashMap<>();
  private final Map<String, String> describedValues = new HashMap<>();
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
  public List<DiffEntry> computeChanges(Object updatedObject) {
    // TODO: Hack alert! State should be handled semantically, but for now show raw changes.
    ignoreSemantics = updatedObject instanceof State;
    Map<String, Object> updated = extractDefinitions(updatedObject);
    List<DiffEntry> updates = new ArrayList<>();
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
    descriptions.clear();
    describedValues.clear();
    previous = extractDefinitions(updatedObject);
  }

  private Map<String, Object> extractValues(Object thing) {
    return traverseExtract(thing, true);
  }

  private Map<String, Object> extractDefinitions(Object thing) {
    return traverseExtract(thing, false);
  }

  private Object traverseObject(Object thing, boolean asValues) {
    if (thing instanceof List<?> listThing) {
      if (!asValues && thing instanceof SemanticList<?> semanticList) {
        return semanticList.getDescription();
      }
      return listThing.stream().map(item -> convertValue(item, asValues)).toList();
    }
    return traverseExtract(thing, asValues);
  }

  private Map<String, Object> traverseExtract(Object thing, boolean asValues) {
    if (thing == null) {
      return ImmutableMap.of();
    }
    if (thing instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<Object, Object> asMap = (Map<Object, Object>) thing;
      return asMap.keySet().stream().collect(
          Collectors.toMap(Object::toString, key -> traverseObject(asMap.get(key), asValues)));
    }
    return Arrays.stream(thing.getClass().getFields())
        .filter(field -> isNotNull(thing, field)).collect(
            Collectors.toMap(Field::getName,
                field -> extractField(thing, field, asValues)));
  }

  private Object extractField(Object thing, Field field, boolean asValue) {
    try {
      Object result = field.get(thing);
      if (isBaseType(field) || isBaseType(result)) {
        return convertValue(result, asValue);
      } else {
        return traverseObject(result, asValue);
      }
    } catch (Exception e) {
      throw new RuntimeException("While converting field " + field.getName(), e);
    }
  }

  @Nullable
  private static Object convertValue(Object result, boolean asValue) {
    boolean useValue = asValue || !SemanticValue.isSemanticValue(result);
    return useValue ? SemanticValue.getValue(result) : SemanticValue.getDescription(result);
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
      List<DiffEntry> updates) {
    right.forEach((key, value) -> {
      String describedKey = describedKey(prefix, key);
      String raw = describeValue(prefix, key, semanticValue(value));
      int index = raw.indexOf('\n');
      String describedValue = index < 0 ? raw : (raw.substring(0, index) + "...");
      if (left != null && left.containsKey(key)) {
        Object leftValue = left.get(key);
        if (SemanticValue.equals(value, leftValue)) {
          return;
        }
        if (isBaseType(value)) {
          updates.add(new DiffEntry(SET, describedKey, describedValue));
        } else {
          accumulateDifference((prefix + key) + ".", (Map<String, Object>) leftValue,
              (Map<String, Object>) value, updates);
        }
      } else {
        updates.add(new DiffEntry(ADD, describedKey, describedValue));
      }
    });
    if (left != null) {
      left.forEach((key, value) -> {
        if (!right.containsKey(key)) {
          String describedKey = describedKey(prefix, key);
          updates.add(new DiffEntry(REMOVE, describedKey, null));
        }
      });
    }
  }

  private String describeValue(String prefix, String key, String value) {
    String fullKey = prefix + key;
    return descriptions.containsKey(fullKey) ? describedValues.get(fullKey) : value;
  }

  private String describedKey(String prefix, String key) {
    String fullKey = prefix + key;
    String keyPath = prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix;
    String formattedKey = String.format("%s[%s]", keyPath, descriptions.get(fullKey));
    return descriptions.containsKey(fullKey) ? formattedKey : fullKey;
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
    String wrapper = isSemantic ? "_" : "`";
    if (value instanceof Date && !isSemantic) {
      if (ignoreSemantics) {
        return wrapper + isoConvert((Date) value) + wrapper;
      } else {
        throw new IllegalArgumentException(
            "Unexpected non-semantic Date in semantic value calculation");
      }
    }
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
  public List<DiffEntry> diff(Object startObject, Object endObject) {
    Map<String, Object> left = extractValues(startObject);
    Map<String, Object> right = extractValues(endObject);
    List<DiffEntry> updates = new ArrayList<>();
    accumulateDifference("", left, right, updates);
    return updates;
  }

  /**
   * Set up a semantic mapping for a key/value pair.
   *
   * @param keyPath        path to container
   * @param keyName        key name
   * @param description    semantic description of key
   * @param describedValue semantic description of value
   */
  public void mapSemanticKey(String keyPath, String keyName, String description,
      String describedValue) {
    String fullKey = keyPath + "." + keyName;
    descriptions.put(fullKey, description);
    describedValues.put(fullKey, describedValue);
  }

  public boolean isInitialized() {
    return previous != null;
  }
}
