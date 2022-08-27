package com.google.daq.mqtt.util;

import static com.google.daq.mqtt.util.ConfigDiffEngine.toJsonString;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.daq.mqtt.validator.CleanDateFormat;
import java.util.Date;

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
      String dateString = toJsonString(date);
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
}
