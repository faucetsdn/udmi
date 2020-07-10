package com.google.daq.mqtt.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.cloudiot.v1.CloudIotScopes;

import java.io.File;
import java.io.FileInputStream;

public class ConfigUtil {
  public static final String CLOUD_IOT_CONFIG_JSON = "cloud_iot_config.json";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static CloudIotConfig readCloudIotConfig(File configFile) {
    try {
      return OBJECT_MAPPER.readValue(configFile, CloudIotConfig.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading config file "+ configFile.getAbsolutePath(), e);
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
}
