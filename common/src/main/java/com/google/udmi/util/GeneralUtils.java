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

  public static <T> T deepCopy(T object) {
    Class<?> targetClass = object.getClass();
    try {
      @SuppressWarnings("unchecked")
      T t = (T) OBJECT_MAPPER.readValue(toJsonString(object), targetClass);
      return t;
    } catch (Exception e) {
      throw new RuntimeException("While making deep copy of " + targetClass.getName(), e);
    }
  }

  public static <T> T mergeObject(Object destination, Object source) {
    Map<String, Object> target = JsonUtil.asMap(destination);
    mergeObject(target, JsonUtil.asMap(source));
    @SuppressWarnings("unchecked")
    T t = (T) JsonUtil.convertTo(destination.getClass(), target);
    return t;
  }

  public static void mergeObject(Map<String, Object> target, Map<String, Object> source) {
    for (String key : source.keySet()) {
      Object targetValue = target.get(key);
      Object sourceValue = source.get(key);
      if (targetValue instanceof Map && sourceValue instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> castTarget = (Map<String, Object>) targetValue;
        @SuppressWarnings("unchecked")
        Map<String, Object> castSource = (Map<String, Object>) sourceValue;
        mergeObject(castTarget, castSource);
      } else {
        target.put(key, sourceValue);
      }
    }
  }
}
