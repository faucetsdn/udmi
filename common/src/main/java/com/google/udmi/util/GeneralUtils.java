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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class GeneralUtils {

  public static final Joiner CSV_JOINER = Joiner.on(", ");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  private static final String SEPARATOR = "\n  ";
  private static final Joiner INDENTED_LINES = Joiner.on(SEPARATOR);

  public static String[] arrayOf(String... args) {
    return args;
  }

  public static String changedLines(List<String> nullableChanges) {
    List<String> changes = Optional.ofNullable(nullableChanges).orElse(ImmutableList.of());
    String terminator = changes.size() == 0 ? "." : ":";
    String header = String.format("Changed %d fields%s%s", changes.size(), terminator, SEPARATOR);
    return (header + INDENTED_LINES.join(changes)).trim();
  }

  /**
   * Shallow all fields from one class to another existing class. This can be used, for example, if
   * the target class is "final" but the fields themselves need to be updated.
   *
   * @param from source object
   * @param to target object
   * @param <T> type of object
   */
  public static <T> void copyFields(T from, T to, boolean includeNull) {
    Field[] fields = from.getClass().getDeclaredFields();
    for (Field field : fields) {
      if (!Modifier.isStatic(field.getModifiers())) {
        try {
          Object value = field.get(from);
          if (includeNull || value != null) {
            field.set(to, value);
          }
        } catch (Exception e) {
          throw new RuntimeException("While copying field " + field.getName(), e);
        }
      }
    }
  }

  public static String decodeBase64(String payload) {
    return new String(Base64.getDecoder().decode(payload));
  }

  public static <T> T deepCopy(T object) {
    if (object == null) {
      return null;
    }
    Class<?> targetClass = object.getClass();
    try {
      @SuppressWarnings("unchecked")
      T t = (T) OBJECT_MAPPER.readValue(toJsonString(object), targetClass);
      return t;
    } catch (Exception e) {
      throw new RuntimeException("While making deep copy of " + targetClass.getName(), e);
    }
  }

  public static String encodeBase64(String payload) {
    return encodeBase64(payload.getBytes());
  }

  public static String encodeBase64(byte[] payload) {
    return Base64.getEncoder().encodeToString(payload);
  }

  /**
   * Get a "friendly" (cause messages only) stack trace string.
   */
  public static String friendlyStackTrace(Throwable e) {
    List<String> messages = new ArrayList<>();
    while (e != null) {
      messages.add(Optional.ofNullable(e.getMessage()).orElseGet(e::toString));
      e = e.getCause();
    }
    return CSV_JOINER.join(messages);
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

  public static Map<String, Object> getSubMap(Map<String, Object> input, String field) {
    //noinspection unchecked
    return ifNotNullGet(input, map -> (Map<String, Object>) map.get(field));
  }

  public static Map<String, Object> getSubMapDefault(Map<String, Object> input, String field) {
    //noinspection unchecked
    return ifNotNullGet(input,
        map -> (Map<String, Object>) map.computeIfAbsent(field, k -> new HashMap<>()));
  }

  public static <T, V> V ifNotNullGet(T value, Function<T, V> converter) {
    return ifNotNullGet(value, converter, null);
  }

  public static <T, V> V ifNotNullGet(T value, Function<T, V> converter, V elseResult) {
    return value == null ? elseResult : converter.apply(value);
  }

  public static <T, V> V ifNotNullGet(T value, Supplier<V> converter) {
    return value == null ? null : converter.get();
  }

  public static <T> void ifNotNullThen(T value, Consumer<T> consumer) {
    Optional.ofNullable(value).ifPresent(consumer);
  }

  public static <T> void ifNotNullThen(T value, Runnable action) {
    Optional.ofNullable(value).ifPresent(derp -> action.run());
  }

  public static <T> void ifNotTrueThen(Object conditional, Runnable action) {
    if (!isTrue(conditional)) {
      action.run();
    }
  }

  public static <T> void ifTrueThen(Object conditional, Runnable action) {
    if (isTrue(conditional)) {
      action.run();
    }
  }

  public static boolean isTrue(Object value) {
    return Boolean.TRUE.equals(value);
  }

  public static <U> U mapReplace(U previous, U added) {
    return added;
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

        Object fieldValue = field.get(target);
        if (fieldValue instanceof Boolean) {
          ifTrueThen(fieldValue, () -> options.add(field.getName()));
        } else if (fieldValue != null) {
          options.add(field.getName() + "=" + fieldValue);
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException("While accessing field " + field.getName(), e);
      }
    }
    return String.join(" ", options);
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

  public static String sha256(String input) {
    return sha256(input.getBytes());
  }

  public static String sha256(byte[] bytes) {
    return Hashing.sha256().hashBytes(bytes).toString();
  }

  public static <T> Collector<Entry<String, T>, ?, TreeMap<String, String>> sortedMapCollector(
      Function<Entry<String, T>, String> prioritizer) {
    return Collectors.toMap(prioritizer, Entry::getKey, GeneralUtils::mapReplace, TreeMap::new);
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

  public static void toJsonFile(File file, Object target) {
    try {
      OBJECT_MAPPER.writeValue(file, target);
    } catch (Exception e) {
      throw new RuntimeException("While writing target " + file.getAbsolutePath(), e);
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

  public static <T> T using(T variable, Consumer<T> consumer) {
    consumer.accept(variable);
    return variable;
  }
}