package com.google.daq.mqtt.util;

import static com.google.daq.mqtt.util.Common.VERSION_PROPERTY_KEY;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Container class for upgrading UDMI messages from older versions.
 */
public class MessageUpgrader {

  public static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;
  public static final String STATE_SCHEMA = "state";
  public static final String METADATA_SCHEMA = "metadata";
  private static final String TARGET_FORMAT = "%d.%d.%d";
  private final JsonNode message;
  private final String schemaName;
  private final int major;
  private int patch;
  private int minor;

  /**
   * Create basic container for message upgrading.
   *
   * @param schemaName schema name to work with
   * @param message    message to be upgraded
   */
  public MessageUpgrader(String schemaName, JsonNode message) {
    this.message = message;
    this.schemaName = schemaName;

    JsonNode version = message.get(VERSION_PROPERTY_KEY);
    String verStr =
        version != null ? version.isNumber() ? Integer.toString(version.asInt()) : version.asText()
            : "1";
    String[] components = verStr.split("-", 2);
    String[] parts = components[0].split("\\.", 4);
    major = Integer.parseInt(parts[0]);
    minor = parts.length >= 2 ? Integer.parseInt(parts[1]) : -1;
    patch = parts.length >= 3 ? Integer.parseInt(parts[2]) : -1;

    if (parts.length >= 4) {
      throw new IllegalArgumentException("Unexpected version " + verStr);
    }
  }

  /**
   * Update message to the latest standard.
   *
   * @param forceUpgrade true to force a complete upgrade pass irrespective of original version
   * @return true if the message has been altered
   */
  public boolean upgrade(boolean forceUpgrade) {
    if (major != 1) {
      throw new IllegalArgumentException("Starting major version " + major);
    }

    final JsonNode original = message.deepCopy();
    boolean upgraded = false;

    if (forceUpgrade || minor < 0) {
      minor = 0;
      patch = 0;
      upgraded = true;
    }

    if (minor < 3) {
      minor = 3;
      patch = 0;
    }

    if (minor == 3 && patch < 14) {
      JsonNode before = message.deepCopy();
      upgrade_1_3_14();
      upgraded |= !before.equals(message);
      patch = 14;
    }

    if (minor < 4) {
      minor = 4;
      patch = 0;
    }

    if (minor == 4 && patch < 1) {
      JsonNode before = message.deepCopy();
      upgrade_1_4_1();
      upgraded |= !before.equals(message);
      patch = 0;
    }

    if (upgraded && message.has(VERSION_PROPERTY_KEY)) {
      ((ObjectNode) message).put(VERSION_PROPERTY_KEY,
          String.format(TARGET_FORMAT, major, minor, patch));
    }

    return !original.equals(message);
  }

  private void upgrade_1_3_14() {
    if (STATE_SCHEMA.equals(schemaName)) {
      upgrade_1_3_14_state();
    }
    if (METADATA_SCHEMA.equals(schemaName)) {
      upgrade_1_3_14_metadata();
    }
  }

  private void upgrade_1_3_14_state() {
    ObjectNode system = (ObjectNode) message.get("system");
    if (system != null) {
      upgradeMakeModel(system);
      upgradeFirmware(system);
      upgradeStatuses(system);
    }
  }

  private void upgrade_1_3_14_metadata() {
    ObjectNode localnet = (ObjectNode) message.get("localnet");
    if (localnet == null) {
      return;
    }
    ObjectNode subsystem = (ObjectNode) localnet.remove("subsystem");
    if (subsystem != null) {
      localnet.set("families", subsystem);
    }
  }

  private void upgrade_1_4_1() {
    if (STATE_SCHEMA.equals(schemaName)) {
      upgrade_1_4_1_state();
    }
  }

  private void upgrade_1_4_1_state() {
    ObjectNode system = (ObjectNode) message.get("system");
    if (system != null) {
      assertFalse("operation key in older version", system.has("operation"));
      JsonNode operational = system.remove("operational");
      if (operational != null) {
        ObjectNode operation = new ObjectNode(NODE_FACTORY);
        system.set("operation", operation);
        operation.set("operational", operational);
      }
    }
  }

  private void assertFalse(String message, boolean value) {
    if (value) {
      throw new RuntimeException(message);
    }
  }

  private void upgradeStatuses(ObjectNode system) {
    JsonNode statuses = system.remove("statuses");
    if (statuses != null && !system.has("status") && statuses.size() != 0) {
      system.set("status", statuses.get(0));
    }
  }

  private void upgradeFirmware(ObjectNode system) {
    JsonNode firmware = system.remove("firmware");
    if (firmware != null) {
      JsonNode version = ((ObjectNode) firmware).remove(VERSION_PROPERTY_KEY);
      if (version != null && !system.has("software")) {
        ObjectNode softwareNode = new ObjectNode(NODE_FACTORY);
        softwareNode.put("firmware", version.asText());
        system.set("software", softwareNode);
      }
    }
  }

  private void upgradeMakeModel(ObjectNode system) {
    JsonNode makeModel = system.remove("make_model");
    if (makeModel != null && !system.has("hardware")) {
      ObjectNode hardwareNode = new ObjectNode(NODE_FACTORY);
      hardwareNode.put("model", makeModel.asText());
      hardwareNode.put("make", "unknown");
      system.set("hardware", hardwareNode);
    }
  }
}
