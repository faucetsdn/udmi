package com.google.daq.mqtt.registrar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MessageDowngrader {

  private final ObjectNode message;
  private int major;
  private int minor;
  private int patch;

  public MessageDowngrader(String schemaName, JsonNode configJson) {
    if (!"config".equals(schemaName)) {
      throw new IllegalArgumentException("Can only downgrade config messages");
    }
    this.message = (ObjectNode) configJson;
  }

  public void downgrade(JsonNode versionNode) {
    final String version = convertVersion(versionNode);
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

    downgradeLocalnet();

    message.set("version", versionNode);
  }

  private String convertVersion(JsonNode versionNode) {
    if (versionNode == null) {
      return "1";
    }
    if (versionNode.isTextual()) {
      return versionNode.asText();
    }
    if (versionNode.isIntegralNumber()) {
      return Integer.toString(versionNode.asInt());
    }
    throw new IllegalStateException("Unrecognized version node " + versionNode.asText());
  }

  private void downgradeLocalnet() {
    ObjectNode localnet = (ObjectNode) message.get("localnet");
    if (localnet == null) {
      return;
    }
    JsonNode families = localnet.remove("families");
    if (families != null) {
      localnet.set("subsystem", families);
    }
  }
}
