package com.google.udmi.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GeneralUtils {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  private static final String SEPARATOR = "\n  ";
  private static final Joiner INDENTED_LINES = Joiner.on(SEPARATOR);

  /**
   * Returns a string of enabled options and values.
   */
  public static String optionsString(Object target) {
    List<String> options = new ArrayList<>();
    Class<?> clazz = target.getClass();
    Field[] fields = clazz.getDeclaredFields();

    for (Field field : fields) {
      try {
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
          continue;
        }

        if (field.get(target) != null && Boolean.TRUE.equals(field.get(target))) {
          options.add(field.getName());
        } else if (field.get(target) != null) {
          options.add(field.getName() + "=" + field.get(target));
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException("While accessing field " + field.getName(), e);
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

  public static String sha256(String input) {
    return sha256(input.getBytes());
  }

  public static String sha256(byte[] bytes) {
    return Hashing.sha256().hashBytes(bytes).toString();
  }

  public static String encodeBase64(String payload) {
    return encodeBase64(payload.getBytes());
  }

  public static String encodeBase64(byte[] payload) {
    return Base64.getEncoder().encodeToString(payload);
  }

  public static String decodeBase64(String payload) {
    return new String(Base64.getDecoder().decode(payload));
  }

  /**
   * Get a string of the java stack trace.
   *
   * @param e stack to trace
   * @return stack trace string
   */
  public static String stackTraceString(Throwable e) {
    OutputStream outputStream = new ByteArrayOutputStream();
    try (PrintStream ps = new PrintStream(outputStream)) {
      e.printStackTrace(ps);
    }
    return outputStream.toString();
  }

  public static List<String> runtimeExec(String... command) {
    ProcessBuilder processBuilder = new ProcessBuilder().command(command);
    try {
      Process process = processBuilder.start();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream()))) {
        int exitVal = process.waitFor();
        if (exitVal != 0) {
          throw new RuntimeException("Process exited with exit code " + exitVal);
        }
        return reader.lines().collect(Collectors.toList());
      }
    } catch (Exception e) {
      throw new RuntimeException("While executing subprocess " + String.join(" ", command));
    }
  }

  /**
   * Shallow all fields from one class to another existing class. This can be used, for example, if
   * the target class is "final" but the fields themselves need to be updated.
   *
   * @param from source object
   * @param to   target object
   * @param <T>  type of object
   */
  public static <T> void copyFields(T from, T to) {
    Field[] fields = to.getClass().getDeclaredFields();
    for (Field field : fields) {
      if (!Modifier.isStatic(field.getModifiers())) {
        try {
          field.set(to, field.get(from));
        } catch (Exception e) {
          throw new RuntimeException("While copying field " + field.getName(), e);
        }
      }
    }
  }

  public static String changedLines(List<String> nullableChanges) {
    List<String> changes = Optional.ofNullable(nullableChanges).orElse(ImmutableList.of());
    String terminator = changes.size() == 0 ? "." : ":";
    String header = String.format("Changed %d fields%s%s", changes.size(), terminator, SEPARATOR);
    return (header + INDENTED_LINES.join(changes)).trim();
  }
}