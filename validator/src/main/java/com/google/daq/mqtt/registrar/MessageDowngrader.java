package com.google.daq.mqtt.registrar;

import com.fasterxml.jackson.databind.JsonNode;

public class MessageDowngrader {

  private final JsonNode message;
  private int major;
  private int minor;
  private int patch;

  public MessageDowngrader(String schemaName, JsonNode configJson) {
    if (!"config".equals(schemaName)) {
      throw new IllegalArgumentException("Can only downgrade config messages");
    }
    this.message = configJson;
  }

  public void downgrade(String version) {
    String[] components = version.split("-", 2);
    String[] parts = components[0].split("\\.", 4);
    major = Integer.parseInt(parts[0]);
    minor = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
    patch = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;

    if (parts.length >= 4) {
      throw new IllegalArgumentException("Unexpected version " + version);
    }

    if (major > 1 || minor > 1 || patch > 13) {
      return;
    }

    throw new RuntimeException("Downgrading config to " + version);
  }
}
