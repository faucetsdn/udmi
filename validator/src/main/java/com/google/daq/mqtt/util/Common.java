package com.google.daq.mqtt.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.util.List;
import java.util.MissingFormatArgumentException;

/**
 * Collection of common constants and minor utilities.
 */
public abstract class Common {

  public static final String STATE_QUERY_TOPIC = "query/state";
  public static final String TIMESTAMP_ATTRIBUTE = "timestamp";
  public static final String NO_SITE = "--";
  public static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(Feature.ALLOW_COMMENTS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
  public static final String JSON_SUFFIX = ".json";
  public static final String GCP_REFLECT_KEY_PKCS8 = "validator/rsa_private.pkcs8";

  /**
   * Remove the next item from the list in an exception-safe way.
   *
   * @param argList list of arguments
   * @return removed argument
   */
  public static String removeNextArg(List<String> argList) {
    if (argList.isEmpty()) {
      throw new MissingFormatArgumentException("Missing argument");
    }
    return argList.remove(0);
  }
}
