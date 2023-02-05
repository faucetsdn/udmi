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
  private String deviceId;

  /**
   * Create message downgrader.
   *
   * @param deviceId    device id for downgrading
   * @param schemaName  schema to downgrade
   * @param messageJson message json
   */
  public MessageDowngrader(String deviceId, String schemaName, JsonNode messageJson) {
    this.deviceId = deviceId;
    if (!"config".equals(schemaName)) {
      throw new IllegalArgumentException("Can only downgrade config messages");
    }
    this.message = (ObjectNode) messageJson;
  }

  /**
   * Downgrade a message to a target version.
   *
   * @param newVersion target downgrade version (as a JsonNode)
   */
  public void downgrade(JsonNode newVersion) {
    final String version = convertVersion(newVersion);
    String[] components = version.split("-", 2);
    String[] parts = components[0].split("\\.", 4);
    major = Integer.parseInt(parts[0]);
    minor = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
    patch = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;
    JsonNode oldVersion = message.get(VERSION_PROPERTY_KEY);
    message.set(VERSION_PROPERTY_KEY, newVersion);

    if (oldVersion.equals(newVersion)) {
      return;
    }

    System.err.printf("Downgrading %s config from %s to %s%n", deviceId, oldVersion, newVersion);

    if (parts.length >= 4) {
      throw new IllegalArgumentException("Unexpected version " + version);
    }

    if (major > 1) {
      return;
    }

    if (major == 1 && minor > 4) {
      return;
    }

    if (major == 1 && minor == 4 && patch > 0) {
      return;
    }

    downgrade_localnet_1_4_0();
    downgrade_system_1_4_0();

    if (major == 1 && minor > 3) {
      return;
    }

    if (major == 1 && minor == 3 && patch > 13) {
      return;
    }

    downgrade_localnet_1_3_13();
  }

  private void downgrade_system_1_4_0() {
    ObjectNode system = (ObjectNode) message.get("system");
    if (system != null) {
      system.remove("operation");
    }
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

  private void downgrade_localnet_1_4_0() {
    ObjectNode localnet = (ObjectNode) message.get("localnet");
    if (localnet == null) {
      return;
    }
    JsonNode networks = localnet.remove("networks");
    if (networks != null) {
      localnet.set("families", networks);
    }
  }

  private void downgrade_localnet_1_3_13() {
    ObjectNode localnet = (ObjectNode) message.get("localnet");
    if (localnet == null) {
      return;
    }
    JsonNode networks = localnet.remove("families");
    if (networks != null) {
      localnet.set("subsystem", networks);
    }
  }

}
