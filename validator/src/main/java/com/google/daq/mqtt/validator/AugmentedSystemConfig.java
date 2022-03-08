package com.google.daq.mqtt.validator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import udmi.schema.SystemConfig;

public class AugmentedSystemConfig extends SystemConfig {
  /**
   * Extra field for the config, used for testing.
   */
  @JsonProperty("extra_field")
  @JsonPropertyDescription("Extra field in config block")
  public String extraField;
}
