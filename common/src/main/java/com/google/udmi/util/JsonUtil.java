package com.google.udmi.util;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

/**
 * Collection of utilities for working with json things.
 */
public abstract class JsonUtil {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(Feature.ALLOW_COMMENTS)
      .enable(SerializationFeature.INDENT_OUTPUT)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .setDateFormat(new CleanDateFormat())
      .setSerializationInclusion(Include.NON_NULL);
  public static final String JSON_SUFFIX = ".json";

  /**
   * Get a proper JSON string representation of the given Date.
   *
   * @param date thing to convert
   * @return converted to a string
   */
  public static String getTimestamp(Date date) {
    try {
      if (date == null) {
        return "null";
      }
      String dateString = stringify(date);
      // Remove the encapsulating quotes included because it's a JSON string-in-a-string.
      return dateString.substring(1, dateString.length() - 1);
    } catch (Exception e) {
      throw new RuntimeException("Creating timestamp", e);
    }
  }

  /**
   * Get a current timestamp string.
   *
   * @return current ISO timestamp
   */
  public static String getTimestamp() {
    return getTimestamp(CleanDateFormat.cleanDate());
  }

  /**
   * Sleep and catch-and-rethrow any exceptions.
   *
   * @param logClearTimeMs duration to sleep
   */
  public static void safeSleep(long logClearTimeMs) {
    try {
      Thread.sleep(logClearTimeMs);
    } catch (Exception e) {
      throw new RuntimeException("Interruped sleep", e);
    }
  }

  public static <T> T fromString(Class<T> targetClass, String messageString) {
    try {
      return OBJECT_MAPPER.readValue(messageString, checkNotNull(targetClass, "target class"));
    } catch (Exception e) {
      throw new RuntimeException("While converting message to " + targetClass.getName(), e);
    }
  }

  /**
   * Convert a generic object ot one of a specific class.
   *
   * @param targetClass result class
   * @param message     object to convert
   * @param <T>         class parameter
   * @return converted object
   */
  public static <T> T convertTo(Class<T> targetClass, Object message) {
    return message == null ? null : fromString(targetClass, stringify(message));
  }

  /**
   * Convert the pojo to a mapped representaiton.
   *
   * @param message input object to convert
   * @return object-as-map
   */
  public static Map<String, Object> toMap(Object message) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = convertTo(TreeMap.class, message);
    return map;
  }

  /**
   * Convert an object to a json string.
   *
   * @param target object to convert
   * @return json string representation
   */
  public static String stringify(Object target) {
    try {
      return OBJECT_MAPPER.writeValueAsString(target);
    } catch (Exception e) {
      throw new RuntimeException("While stringifying object", e);
    }
  }

  /**
   * Load a file to given type.
   *
   * @param clazz class of result
   * @param file  file to load
   * @param <T>   type of result
   * @return loaded object
   */
  public static <T> T loadFile(Class<T> clazz, File file) {
    try {
      return file.exists() ? OBJECT_MAPPER.readValue(file, clazz) : null;
    } catch (Exception e) {
      throw new RuntimeException("While loading " + file.getAbsolutePath(), e);
    }
  }

  /**
   * Write json representation to a file.
   *
   * @param target object to write
   * @param file   output file
   */
  public static void writeFile(Object target, File file) {
    try {
      OBJECT_MAPPER.writeValue(file, target);
    } catch (Exception e) {
      throw new RuntimeException("While writing " + file.getAbsolutePath(), e);
    }
  }

  /**
   * Get a date object parsed from a string representation.
   *
   * @param timestamp string representation
   * @return Date object
   */
  public static Date getDate(String timestamp) {
    return timestamp == null ? null : Date.from(Instant.parse(timestamp));
  }

  /**
   * Convert the json string to a generic map object.
   *
   * @param input input string
   * @return input as map object
   */
  public static Map<String, Object> asMap(String input) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = fromString(TreeMap.class, input);
    return map;
  }

  /**
   * Convert the json object to a generic map object.
   *
   * @param input input object
   * @return input as map object
   */
  public static Map<String, Object> asMap(Object input) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = convertTo(TreeMap.class, input);
    return map;
  }
}
