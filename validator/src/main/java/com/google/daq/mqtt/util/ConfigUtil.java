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

public class ConfigUtil {

  public static final String CLOUD_IOT_CONFIG_JSON = "cloud_iot_config.json";
  public static final String EXCEPTIONS_JSON = "exceptions.json";
  public static final String UDMI_VERSION = "1.3.14";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setDateFormat(new ISO8601DateFormat());

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

  public static AllDeviceExceptions loadExceptions(File siteConfig) {
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

  public static ValidatorConfig readValidatorConfig(File configFile) {
    try {
      return OBJECT_MAPPER.readValue(configFile, ValidatorConfig.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + configFile.getAbsolutePath(), e);
    }
  }

  public static String getTimestamp() {
    try {
      String dateString = OBJECT_MAPPER.writeValueAsString(new Date());
      return dateString.substring(1, dateString.length() - 1);
    } catch (Exception e) {
      throw new RuntimeException("Creating timestamp", e);
    }
  }

  public static class AllDeviceExceptions extends HashMap<String, DeviceExceptions> {

  }

  public static class DeviceExceptions extends HashMap<String, Object> {

    public List<Pattern> patterns = new ArrayList<>();
  }

}
