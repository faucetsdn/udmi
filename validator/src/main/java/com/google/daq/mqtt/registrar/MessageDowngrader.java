package com.google.daq.mqtt.registrar;

import static com.google.daq.mqtt.util.Common.VERSION_PROPERTY_KEY;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Downgrade a message to a previous UDMI schema version.
 */
public class MessageDowngrader {

  private final ObjectNode message;
  private int major;
  private int minor;
  private int patch;

  /**
   * Create message downgrader.
   *
   * @param schemaName  schema to downgrade
   * @param messageJson message json
   */
  public MessageDowngrader(String schemaName, JsonNode messageJson) {
    if (!"config".equals(schemaName)) {
      throw new IllegalArgumentException("Can only downgrade config messages");
    }
    this.message = (ObjectNode) messageJson;
  }

  /**
   * Downgrade a message to a target version.
   *
   * @param versionNode target downgrade version (as a JsonNode)
   */
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

    if (major == 1 && (minor < 4 || (minor == 4 && patch < 1))) {
      ((ObjectNode) message.get("system")).remove("operation");
    }

    message.set(VERSION_PROPERTY_KEY, versionNode);
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
