package com.google.udmi.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GeneralUtils {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  /**
   * Returns a string of enabled options and values.
   */
  public static String optionsString(Object target) {
    List<String> options = new ArrayList<>();
    Class<?> clazz = target.getClass();
    Field[] fields = clazz.getDeclaredFields();

    for (Field field : fields) {
      try {
        if (field.get(target) != null && Boolean.TRUE.equals(field.get(target))) {
          options.add(field.getName());
        } else if (field.get(target) != null) {
          options.add(field.getName() + "=" + field.get(target));
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    return String.join(" ", options);
  }

  public static <T> T fromJsonFile(File path, Class<T> valueType) {
    try {
      return OBJECT_MAPPER.readValue(path, valueType);
    } catch (Exception e) {
      throw new RuntimeException("While loading json file " + path.getAbsolutePath(), e);
    }
  }

  public static <T> T fromJsonString(String body, Class<T> valueType) {
    try {
      if (body == null) {
        return null;
      }
      return OBJECT_MAPPER.readValue(body, valueType);
    } catch (Exception e) {
      throw new RuntimeException("While loading json string", e);
    }
  }

  public static String toJsonString(Object object) {
    try {
      if (object == null) {
        return null;
      }
      return OBJECT_MAPPER.writeValueAsString(object);
    } catch (Exception e) {
      throw new RuntimeException("While converting object to json", e);
    }
  }

  public static void toJsonFile(File file, Object target) {
    try {
      OBJECT_MAPPER.writeValue(file, target);
    } catch (Exception e) {
      throw new RuntimeException("While writing target " + file.getAbsolutePath(), e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T deepCopy(T object) {
    Class<?> targetClass = object.getClass();
    try {
      return (T) OBJECT_MAPPER.readValue(toJsonString(object), targetClass);
    } catch (Exception e) {
      throw new RuntimeException("While making deep copy of " + targetClass.getName(), e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T deepMergeDefaults(Object destination, Object source) {
    deepMergeDefaults(JsonUtil.asMap(destination), JsonUtil.asMap(source));
    return (T) JsonUtil.convertTo(destination.getClass(), destination);
  }

  @SuppressWarnings("unchecked")
  public static void deepMergeDefaults(Map<String, Object> destination, Map<String, Object> source) {
    for (String key : source.keySet()) {
      Object value2 = source.get(key);
      if (destination.containsKey(key)) {
        Object value1 = destination.get(key);
        // When destination and source both contain key, deep copy maps but not other key/values,
        // which would produce config override rather than defaults.
        if (value1 instanceof Map && value2 instanceof Map) {
          deepMergeDefaults((Map<String, Object>) value1, (Map<String, Object>) value2);
        }
      } else {
        destination.put(key, value2);
      }
    }
  }
}
