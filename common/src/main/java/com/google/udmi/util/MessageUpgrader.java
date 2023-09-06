package com.google.udmi.util;

import static com.google.udmi.util.Common.VERSION_KEY;
import static com.google.udmi.util.GeneralUtils.OBJECT_MAPPER_RAW;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Container class for upgrading UDMI messages from older versions.
 */
public class MessageUpgrader {

  public static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;
  public static final String STATE_SCHEMA = "state";
  public static final String STATE_SYSTEM_SCHEMA = "state_system";
  public static final String METADATA_SCHEMA = "metadata";
  private static final String TARGET_FORMAT = "%d.%d.%d";
  private final ObjectNode message;
  private final JsonNode original;
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
    this.message = (ObjectNode) message;
    this.schemaName = schemaName;
    this.original = message.deepCopy();

    JsonNode version = message.get(VERSION_KEY);
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

  public MessageUpgrader(String schemaName, Object originalMessage) {
    this(schemaName, OBJECT_MAPPER_RAW.valueToTree(originalMessage));
  }

  public boolean wasUpgraded() {
    return !original.equals(message);
  }

  /**
   * Update message to the latest standard, if necessary.
   */
  public Object upgrade() {
    return upgrade(false);
  }

  /**
   * Update message to the latest standard.
   *
   * @param forceUpgrade true to force a complete upgrade pass irrespective of original version
   */
  public Object upgrade(boolean forceUpgrade) {
    if (major != 1) {
      throw new IllegalArgumentException("Starting major version " + major);
    }

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
      patch = 1;
    }

    if (upgraded && message.has(VERSION_KEY)) {
      message.put(VERSION_KEY, String.format(TARGET_FORMAT, major, minor, patch));
    }

    return message;
  }

  private void upgrade_1_3_14() {
    if (STATE_SCHEMA.equals(schemaName)) {
      upgrade_1_3_14_state();
    }
    if (STATE_SYSTEM_SCHEMA.equals(schemaName)) {
      upgrade_1_3_14_state_system(message);
    }
    if (METADATA_SCHEMA.equals(schemaName)) {
      upgrade_1_3_14_metadata();
    }
  }

  private void upgrade_1_3_14_state() {
    ifNotNullThen((ObjectNode) message.get("system"), this::upgrade_1_3_14_state_system);
  }

  private void upgrade_1_3_14_state_system(ObjectNode system) {
    upgradeMakeModel(system);
    upgradeFirmware(system);
    upgradeStatuses(system);
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
    if (STATE_SYSTEM_SCHEMA.equals(schemaName)) {
      upgrade_1_4_1_state_system(message);
    }
  }

  private void upgrade_1_4_1_state() {
    ifNotNullThen((ObjectNode) message.get("system"), this::upgrade_1_4_1_state_system);
  }

  private void upgrade_1_4_1_state_system(ObjectNode system) {
    assertFalse("operation key in older version", system.has("operation"));
    JsonNode operational = system.remove("operational");
    if (operational != null) {
      ObjectNode operation = new ObjectNode(NODE_FACTORY);
      system.set("operation", operation);
      operation.set("operational", operational);
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
      JsonNode version = ((ObjectNode) firmware).remove(VERSION_KEY);
      if (version != null && !system.has("software")) {
        ObjectNode softwareNode = new ObjectNode(NODE_FACTORY);
        softwareNode.set("firmware", sanitizeFirmwareVersion(version));
        system.set("software", softwareNode);
      }
    }
  }

  private TextNode sanitizeFirmwareVersion(JsonNode version) {
    if (version.isArray()) {
      List<String> values = new ArrayList<>();
      Iterator<JsonNode> elements = version.elements();
      elements.forEachRemaining(item -> values.add(item.asText()));
      String collect = values.stream().collect(Collectors.joining(", "));
      return new TextNode(collect);
    }
    return (TextNode) version;
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
