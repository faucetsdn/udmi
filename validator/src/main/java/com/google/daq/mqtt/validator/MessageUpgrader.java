package com.google.daq.mqtt.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MessageUpgrader {

  public static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;
  private static final String TARGET_FORMAT = "%d.%d.%d";
  private final JsonNode message;
  private final String schemaName;
  private final int major;
  private int patch;
  private int minor;

  public MessageUpgrader(String schemaName, JsonNode message) {
    this.message = message;
    this.schemaName = schemaName;

    JsonNode version = message.get("version");
    String verStr =
        version != null ? version.isNumber() ? Integer.toString(version.asInt()) : version.asText()
            : "1";

    String[] parts = verStr.split("\\.", 4);
    major = Integer.parseInt(parts[0]);
    minor = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
    patch = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;

    if (parts.length >= 4) {
      throw new IllegalArgumentException("Unexpected version " + verStr);
    }
  }

  public void upgrade() {
    if (major != 1) {
      throw new IllegalArgumentException("Starting major version " + major);
    }
    if (minor < 3) {
      upgrade_1_3();
    }
    if (patch < 14) {
      upgrade_1_3_14();
    }
    if (message.has("version")) {
      ((ObjectNode) message).put("version", String.format(TARGET_FORMAT, major, minor, patch));
    }
  }

  private void upgrade_1_3() {
    minor = 3;
    patch = 0;
  }

  private void upgrade_1_3_14() {
    patch = 14;
    if ("state".equals(schemaName)) {
      upgrade_1_3_14_state();
    }
  }

  private void upgrade_1_3_14_state() {
    ObjectNode system = (ObjectNode) message.get("system");
    if (system != null) {
      if (system.has("hardware") || system.has("software")) {
        throw new IllegalStateException("Node already has hardware/software field");
      }
      JsonNode make_model = system.remove("make_model");
      if (make_model != null) {
        ObjectNode hardwareNode = new ObjectNode(NODE_FACTORY);
        hardwareNode.put("model", make_model.asText());
        system.set("hardware", hardwareNode);
      }
      JsonNode firmware = system.remove("firmware");
      if (firmware != null) {
        JsonNode version = ((ObjectNode) firmware).remove("version");
        if (version != null) {
          ObjectNode softwareNode = new ObjectNode(NODE_FACTORY);
          softwareNode.put("firmware", version.asText());
          system.set("software", softwareNode);
        }
      }
    }
  }
}
