package com.google.daq.mqtt.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.cloudiot.v1.CloudIotScopes;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Collection of utilities for managing configuration.
 */
public abstract class ConfigUtil {

  public static final String EXCEPTIONS_JSON = "exceptions.json";
  public static final String UDMI_VERSION = "1.3.14";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setDateFormat(new ISO8601DateFormat());

  /**
   * Read cloud configuration from a file.
   *
   * @param configFile file ot parse
   * @return cloud configuration information
   */
  public static CloudIotConfig readCloudIotConfig(File configFile) {
    try {
      return OBJECT_MAPPER.readValue(configFile, CloudIotConfig.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + configFile.getAbsolutePath(), e);
    }
  }

  static GoogleCredential authorizeServiceAccount(File credFile) {
    try (FileInputStream credStream = new FileInputStream(credFile)) {
      return GoogleCredential
          .fromStream(credStream)
          .createScoped(CloudIotScopes.all());
    } catch (Exception e) {
      throw new RuntimeException("While reading cred file " + credFile.getAbsolutePath(), e);
    }
  }

  static AllDeviceExceptions loadExceptions(File siteConfig) {
    File exceptionsFile = new File(siteConfig, EXCEPTIONS_JSON);
    if (!exceptionsFile.exists()) {
      return null;
    }
    try {
      AllDeviceExceptions all = OBJECT_MAPPER.readValue(exceptionsFile, AllDeviceExceptions.class);
      all.forEach((prefix, device) ->
          device.forEach((pattern, target) ->
              device.patterns.add(Pattern.compile(pattern))));
      return all;
    } catch (Exception e) {
      throw new RuntimeException(
          "While reading exceptions file " + exceptionsFile.getAbsolutePath(), e);
    }
  }

  /**
   * Read a validator configuration file.
   *
   * @param configFile file to read
   * @return parsed validator config
   */
  public static ValidatorConfig readValidatorConfig(File configFile) {
    try {
      return OBJECT_MAPPER.readValue(configFile, ValidatorConfig.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + configFile.getAbsolutePath(), e);
    }
  }

  static class AllDeviceExceptions extends HashMap<String, DeviceExceptions> {

  }

  static class DeviceExceptions extends HashMap<String, Object> {

    public List<Pattern> patterns = new ArrayList<>();
  }

}
