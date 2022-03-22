package com.google.daq.mqtt.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MessageUpgrader {

  public static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;
  private static final String TARGET_VERSION = "1.3.14";
  private final JsonNode message;
  private final String schemaName;
  private final int major;
  private final int patch;
  private final int minor;

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
    if (major != 1) {
      throw new IllegalArgumentException("Starting major version " + major);
    }
  }

  public void upgrade() {
    if ("state".equals(schemaName)) {
      if (major <= 1 && minor <= 3) {
        upgradeState1_3();
      }
    }
    if (message.has("version")) {
      ((ObjectNode) message).put("version", TARGET_VERSION);
    }
  }

  private void upgradeState1_3() {
    ObjectNode system = (ObjectNode) message.get("system");
    if (system != null && patch < 14) {
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
