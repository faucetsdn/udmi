package com.google.udmi.util;

import static com.google.udmi.util.Common.UPGRADED_FROM;
import static com.google.udmi.util.Common.VERSION_KEY;
import static com.google.udmi.util.GeneralUtils.OBJECT_MAPPER_RAW;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.MessageDowngrader.convertVersion;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import udmi.util.SchemaVersion;

/**
 * Container class for upgrading UDMI messages from older versions.
 */
public class MessageUpgrader {

  public static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;
  public static final String STATE_SCHEMA = "state";
  public static final String STATE_SYSTEM_SCHEMA = "state_system";
  public static final String EVENTS_SYSTEM_SCHEMA = "events_system";
  public static final String METADATA_SCHEMA = "metadata";
  private static final String TARGET_FORMAT = "%d.%d.%d";
  private static final String RAW_GIT_VERSION = "git";
  private final ObjectNode message;
  private final JsonNode original;
  private final String schemaName;
  private final int major;
  private final String originalVersion;
  private int patch;
  private int minor;

  /**
   * Create basic container for message upgrading.
   *
   * @param schemaName schema name to work with
   * @param message    message to be upgraded
   */
  public MessageUpgrader(String schemaName, JsonNode message) {
    if (!(message instanceof ObjectNode)) {
      String text = message.asText();
      throw new RuntimeException("Target upgrade message is not an object: " + text.substring(0,
          Math.min(text.length(), 20)));
    }

    this.message = (ObjectNode) message;
    this.schemaName = schemaName;
    this.original = message.deepCopy();

    JsonNode version = message.get(VERSION_KEY);
    originalVersion = (version == null ? "1" : convertVersion(version.asText()));

    try {
      String[] components = originalVersion.split("-", 2);
      String[] parts = components[0].split("\\.", 4);
      if (parts.length >= 4) {
        throw new IllegalArgumentException("More than 3 version components");
      }

      if (RAW_GIT_VERSION.equals(parts[0])) {
        major = 0;
        return;
      }

      try {
        major = Integer.parseInt(parts[0]);
        minor = parts.length >= 2 ? Integer.parseInt(parts[1]) : -1;
        patch = parts.length >= 3 ? Integer.parseInt(parts[2]) : -1;
      } catch (NumberFormatException e) {
        throw new RuntimeException("Bad message version string " + components[0]);
      }
    } catch (Exception e) {
      throw new RuntimeException("While parsing version string " + originalVersion, e);
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
    return upgradeRaw(forceUpgrade);
  }

  private Object upgradeRaw(boolean forceUpgrade) {
    if (major == 0) {
      return message;
    }

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
      upgradeTo_1_3_14();
      upgraded |= !before.equals(message);
      patch = 14;
    }

    if (minor < 4) {
      minor = 4;
      patch = 0;
    }

    if (minor == 4 && patch < 1) {
      JsonNode before = message.deepCopy();
      upgradeTo_1_4_1();
      upgraded |= !before.equals(message);
      patch = 1;
    }

    if (minor < 5) {
      JsonNode before = message.deepCopy();
      upgradeTo_1_5_0();
      upgraded |= !before.equals(message);
      patch = 0;
      minor = 5;
    }

    if (minor == 5) {
      upgraded |= patch == 0 && didMessageChange(this::upgradeTo_1_5_1, patchUpdater(1));
      upgraded |= patch == 1 && didMessageChange(this::upgradeTo_1_5_2, patchUpdater(2));
      upgraded |= patch == 2 && didMessageChange(this::upgradeTo_1_5_3, patchUpdater(3));
    }

    String currentVersion = SchemaVersion.CURRENT.key();
    if (upgraded && message.has(VERSION_KEY) && !currentVersion.equals(originalVersion)) {
      message.put(UPGRADED_FROM, originalVersion);
      message.put(VERSION_KEY, currentVersion);
    }

    return message;
  }

  private Runnable patchUpdater(int newPatch) {
    return () -> patch = newPatch;
  }

  private boolean didMessageChange(Runnable updater, Runnable postfix) {
    JsonNode before = message.deepCopy();
    updater.run();
    postfix.run();
    return !before.equals(message);
  }

  private void upgradeTo_1_3_14() {
    if (STATE_SCHEMA.equals(schemaName)) {
      upgradeTo_1_3_14_state();
    }
    if (STATE_SYSTEM_SCHEMA.equals(schemaName)) {
      upgradeTo_1_3_14_state_system(message);
    }
    if (METADATA_SCHEMA.equals(schemaName)) {
      upgradeTo_1_3_14_metadata();
    }
  }

  private void upgradeTo_1_3_14_state() {
    ifNotNullThen((ObjectNode) message.get("system"), this::upgradeTo_1_3_14_state_system);
  }

  private void upgradeTo_1_3_14_state_system(ObjectNode system) {
    upgradeMakeModel(system);
    upgradeFirmware(system);
    upgradeStatuses(system);
  }

  private void upgradeTo_1_3_14_metadata() {
    ObjectNode localnet = (ObjectNode) message.get("localnet");
    if (localnet == null) {
      return;
    }
    ObjectNode subsystem = (ObjectNode) localnet.remove("subsystem");
    if (subsystem != null) {
      localnet.set("families", subsystem);
    }
  }

  private void upgradeTo_1_4_1() {
    if (STATE_SCHEMA.equals(schemaName)) {
      upgradeTo_1_4_1_state();
    }
    if (STATE_SYSTEM_SCHEMA.equals(schemaName)) {
      upgradeTo_1_4_1_state_system(message);
    }
    if (METADATA_SCHEMA.equals(schemaName)) {
      upgradeTo_1_4_1_metadata();
    }
  }

  private void upgradeTo_1_5_1() {
    ifTrueThen(METADATA_SCHEMA.equals(schemaName), this::upgradeTo_1_5_1_metadata);
  }

  private void upgradeTo_1_5_1_metadata() {
    JsonNode tags = message.remove("tags");
    if (tags == null) {
      return;
    }

    if (!message.has("system")) {
      message.put("system", new ObjectNode(NODE_FACTORY));
    }

    ObjectNode system = (ObjectNode) message.get("system");
    system.put("tags", tags);
  }

  private void upgradeTo_1_5_2() {
    ifTrueThen(EVENTS_SYSTEM_SCHEMA.equals(schemaName), this::upgradeTo_1_5_2_events_system);
  }

  private void upgradeTo_1_5_2_events_system() {
    ifNotNullThen(message.remove("event_count"), node -> message.put("event_no", node));
  }

  private void upgradeTo_1_5_3() {
    ifTrueThen(METADATA_SCHEMA.equals(schemaName), this::upgradeTo_1_5_3_metadata);
  }

  private void upgradeTo_1_5_3_metadata() {
    ifNotNullThen((ObjectNode) message.get("cloud"), this::upgradeTo_1_5_3_metadata_cloud);
  }

  private void upgradeTo_1_5_3_metadata_cloud(ObjectNode cloud) {
    ifNotNullThen(cloud.remove("connection_type"), node -> {
      String connection = node.textValue();
      String resource = ifNotNullGet(cloud.remove("resource_type"), JsonNode::textValue);
      if (resource != null && !resource.equals(connection)) {
        throw new RuntimeException(format("Connection/resource mismatch: %s/%s", connection, resource));
      }
      cloud.put("resource_type", connection);
    });
  }

  private void upgradeTo_1_5_0() {
    if (STATE_SCHEMA.equals(schemaName)) {
      upgradeTo_1_5_0_state();
    }
  }

  private void upgradeTo_1_5_0_state() {
    ObjectNode gateway = (ObjectNode) message.get("gateway");
    if (gateway != null && gateway.has("devices")) {
      gateway.remove("devices");
    }
  }

  private void upgradeTo_1_4_1_metadata() {
    JsonNode localnet = message.get("localnet");
    if (localnet == null || !localnet.has("families")) {
      return;
    }
    ObjectNode localnetFamilies = (ObjectNode) localnet.get("families");

    // Rewrite `id` into `addr`
    Iterator<String> families = localnetFamilies.fieldNames();
    families.forEachRemaining(item -> {
      ObjectNode family = (ObjectNode) localnetFamilies.get(item);
      if (!family.has("addr")) {
        TextNode id = (TextNode) family.remove("id");
        family.put("addr", id);
      }
    });

    if (!message.has("gateway")) {
      message.put("gateway", new ObjectNode(NODE_FACTORY));
    }

    ObjectNode gateway = (ObjectNode) message.get("gateway");
    // If gateway already has "target" set (for whatever reason) - don't stomp the changes
    if (gateway.has("target")) {
      return;
    }

    // Gateways at this time would be configured using the values in the `localnet` block.
    // Gateway configuration now lives in the `gateway.target` property. At the time it is only known
    // that gateways which use the localnet value use either "vendor" or "bacnet"
    ObjectNode gatewayTarget = new ObjectNode(NODE_FACTORY);
    gateway.put("target", gatewayTarget);

    final String targetFamily;
    if (gateway.has("family")) {
      targetFamily = gateway.remove("family").asText();
    } else if (localnetFamilies.has("vendor")) {
      targetFamily = "vendor";
    } else if (localnetFamilies.has("bacnet")) {
      targetFamily = "bacnet";
    } else {
      targetFamily = null;
    }

    if (targetFamily != null) {
      gatewayTarget.put("family", targetFamily);
    }
  }

  private void upgradeTo_1_4_1_state() {
    ifNotNullThen((ObjectNode) message.get("system"), this::upgradeTo_1_4_1_state_system);
  }

  private void upgradeTo_1_4_1_state_system(ObjectNode system) {
    if (system.has("operation")) {
      return;
    }
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
      JsonNode version = ((ObjectNode) firmware).remove("version");
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
