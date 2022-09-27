package com.google.daq.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.util.Map;
import udmi.schema.Level;

/**
 * Common set of values for testing all things UDMI.
 */
public class TestCommon {

  public static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
  public static final String PROJECT_ID = "unit-testing";
  public static final String DEVICE_ID = "AHU-1";
  public static final String UDMI_VERSION = "1.3.14";
  public static final String DEVICE_NUM_ID = "97216312321";
  public static final String REGISTRY_ID = "ZZ-TRI-FECTA";
  public static final String TOOL_ROOT = "..";
  public static final String SCHEMA_SPEC = TOOL_ROOT + "/schema";
  public static final String SITE_DIR = TOOL_ROOT + "/sites/udmi_site_model";
  public static final String LOG_LEVEL = Level.DEBUG.name();
  public static final String KEY_FILE = SITE_DIR + "/validator/rsa_private.pkcs8";
}
