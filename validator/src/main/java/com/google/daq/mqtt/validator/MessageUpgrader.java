package com.google.daq.mqtt.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Container class for upgrading UDMI messages from older versions.
 */
public class MessageUpgrader {

  public static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;
  private static final String TARGET_FORMAT = "%d.%d.%d";
  public static final String STATE_SCHEMA = "state";
  public static final String METADATA_SCHEMA = "metadata";
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

    JsonNode version = message.get("version");
    String verStr =
        version != null ? version.isNumber() ? Integer.toString(version.asInt()) : version.asText()
            : "1";

    String[] components = verStr.split("-", 2);
    String[] parts = components[0].split("\\.", 4);
    major = Integer.parseInt(parts[0]);
    minor = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
    patch = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;

    if (parts.length >= 4) {
      throw new IllegalArgumentException("Unexpected version " + verStr);
    }
  }

  /**
   * Update message to the latest standard.
   */
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

  private void upgradeStatuses(ObjectNode system) {
    JsonNode statuses = system.remove("statuses");
    if (statuses != null) {
      if (system.has("status")) {
        throw new IllegalStateException("Node already has status field");
      }
      if (statuses.size() == 0) {
        return;
      }
      if (statuses.size() > 1) {
        throw new IllegalStateException("More than one statuses to upgrade");
      }
      system.set("status", statuses.get(0));
    }
  }

  private void upgradeFirmware(ObjectNode system) {
    JsonNode firmware = system.remove("firmware");
    if (firmware != null) {
      if (system.has("software")) {
        throw new IllegalStateException("Node already has software field");
      }
      JsonNode version = ((ObjectNode) firmware).remove("version");
      if (version != null) {
        ObjectNode softwareNode = new ObjectNode(NODE_FACTORY);
        softwareNode.put("firmware", version.asText());
        system.set("software", softwareNode);
      }
    }
  }

  private void upgradeMakeModel(ObjectNode system) {
    JsonNode makeModel = system.remove("make_model");
    if (makeModel != null) {
      if (system.has("hardware")) {
        throw new IllegalStateException("Node already has hardware field");
      }
      ObjectNode hardwareNode = new ObjectNode(NODE_FACTORY);
      hardwareNode.put("model", makeModel.asText());
      hardwareNode.put("make", "");
      system.set("hardware", hardwareNode);
    }
  }
}
