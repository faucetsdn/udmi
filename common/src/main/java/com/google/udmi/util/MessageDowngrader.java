package com.google.udmi.util;

import static com.google.udmi.util.Common.DOWNGRADED_FROM;
import static com.google.udmi.util.Common.VERSION_KEY;
import static com.google.udmi.util.GeneralUtils.OBJECT_MAPPER_RAW;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.MessageUpgrader.NODE_FACTORY;
import static java.lang.Integer.parseInt;
import static udmi.util.SchemaVersion.LEGACY_REPLACEMENT;
import static udmi.util.SchemaVersion.VERSION_1;
import static udmi.util.SchemaVersion.VERSION_1_3_13;
import static udmi.util.SchemaVersion.VERSION_1_4_0;
import static udmi.util.SchemaVersion.VERSION_1_4_1;
import static udmi.util.SchemaVersion.VERSION_1_4_2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import udmi.lib.ProtocolFamily;
import udmi.schema.Metadata;
import udmi.util.SchemaVersion;

/**
 * Downgrade a message to a previous UDMI schema version.
 */
public class MessageDowngrader {

  private final ObjectNode message;
  private final ObjectNode original;
  private final String schema;
  private final Metadata metadata;


  /**
   * Create message down-grader.
   *
   * @param schemaName  schema to downgrade
   * @param messageJson message json
   * @param metadata
   */
  public MessageDowngrader(String schemaName, JsonNode messageJson, Metadata metadata) {
    schema = schemaName;
    message = (ObjectNode) messageJson;
    original = message.deepCopy();
    this.metadata = metadata;
  }

  /**
   * Create message down-grader.
   *
   * @param schemaName schema to downgrade
   * @param message    message object
   */
  public MessageDowngrader(String schemaName, Object message, Metadata metadata) {
    this(schemaName, OBJECT_MAPPER_RAW.valueToTree(message), metadata);
  }

  static String convertVersion(String version) {
    if (version == null) {
      return "1";
    }
    try {
      Integer integerVersion = parseInt(version);
      return Integer.toString(integerVersion);
    } catch (NumberFormatException e) {
      return version;
    }
  }

  /**
   * Downgrade a message to a target version.
   *
   * @param version target downgrade version (as a SchemaVersion)
   * @return downgraded object
   */
  public Map<String, Object> downgrade(SchemaVersion version) {
    return downgrade(version.key());
  }

  /**
   * Downgrade a message to a target version.
   *
   * @param targetVersion target downgrade version (as a JsonNode)
   * @return downgraded object
   */
  public Map<String, Object> downgrade(String targetVersion) {
    return switch (schema) {
      case "config" -> downgradeConfig(targetVersion);
      case "state" -> downgradeState(targetVersion);
      default -> throw new IllegalArgumentException("Unknown downgrade schema " + schema);
    };
  }

  private Map<String, Object> downgradeState(String targetVersion) {
    SchemaVersion schemaVersion = SchemaVersion.fromKey(convertVersion(targetVersion));

    if (schemaVersion.equals(VERSION_1_4_0)) {
      ObjectNode system = (ObjectNode) message.get("system");
      ifNotNullThen(system, map -> {
        JsonNode operation = map.remove("operation");
        ifNotNullThen(operation, src -> map.set("operational", src.get("operational")));
      });
    }

    return JsonUtil.asMap(message);
  }

  private Map<String, Object> downgradeConfig(String targetVersion) {
    downgradeConfigRaw(targetVersion);
    return JsonUtil.asMap(message);
  }

  /**
   * Returns the current version of the configuration message
   *
   * @return version
   */
  private Integer currentVersion() {
    return SchemaVersion.fromKey(message.get(VERSION_KEY).asText()).value();
  }

  private void setCurrentVersion(SchemaVersion version) {
    message.put(VERSION_KEY, version.key());
  }

  public boolean wasDowngraded() {
    return !original.equals(message);
  }

  private void downgradeConfigRaw(String targetVersionString) {
    final SchemaVersion targetVersionEnum = SchemaVersion.fromKey(targetVersionString);
    final Integer targetVersion = targetVersionEnum.value();

    if (currentVersion() > VERSION_1_4_2.value() && currentVersion() > targetVersion) {
      downgradeLocalnetTo_1_4_2_From_1_5_2();
      setCurrentVersion(VERSION_1_4_2);
    }

    if (currentVersion() > VERSION_1_4_1.value() && currentVersion() > targetVersion) {
      downgradeLocalnetTo_1_4_1_From_1_5_0();
      setCurrentVersion(VERSION_1_4_1);
    }

    if (currentVersion() > VERSION_1_4_0.value() && currentVersion() > targetVersion) {
      downgradeOperational_From_1_4_0();
      setCurrentVersion(VERSION_1_4_0);
    }

    if (currentVersion() > VERSION_1_3_13.value() && currentVersion() > targetVersion) {
      downgradeLocalnetTo_1_3_13_From_1_4_1();
      setCurrentVersion(VERSION_1_3_13);
    }

    if (currentVersion() > VERSION_1.value() && currentVersion() > targetVersion) {
      downgradeLocalnetTo_1_From_1_3_13();
      setCurrentVersion(VERSION_1);
    }

    if (wasDowngraded()) {
      message.set(DOWNGRADED_FROM, original.get(VERSION_KEY));
    }

    // Version "1" (string) is actually Version 1 (integer)
    if (message.get(VERSION_KEY).asText().equals(VERSION_1.key())) {
      message.put(VERSION_KEY, LEGACY_REPLACEMENT);
    }

  }

  private void downgradeLocalnetTo_1_4_2_From_1_5_2() {
    String bacAddr = catchToNull(() -> metadata.localnet.families.get(ProtocolFamily.BACNET).addr);
    ifNotNullThen(bacAddr,
        () -> ensure(message, "localnet", "families", ProtocolFamily.BACNET).put("addr", bacAddr));
  }

  private ObjectNode ensure(ObjectNode message, String... keys) {
    AtomicReference<ObjectNode> reference = new AtomicReference<>(message);
    Arrays.stream(keys).forEach(key -> {
      ObjectNode ref = reference.get();
      ifNotTrueThen(ref.has(key), () -> ref.put(key, new ObjectNode(NODE_FACTORY)));
      reference.set((ObjectNode) ref.get(key));
    });
    return reference.get();
  }

  private void downgradeOperational_From_1_4_0() {
    message.remove("operation");
  }

  private void downgradeLocalnetTo_1_4_1_From_1_5_0() {
    ObjectNode gateway = (ObjectNode) message.get("gateway");
    if (gateway == null) {
      return;
    }

    JsonNode target = gateway.remove("target");
    if (target == null) {
      return;
    }

    String targetFamily = target.get("family").asText();
    String targetAddr = target.get("addr").asText();

    // Create localnet block
    if (!message.has("localnet")) {
      message.put("localnet", new ObjectNode(NODE_FACTORY));
    }
    ObjectNode localnet = (ObjectNode) message.get("localnet");
    ObjectNode localnetFamilies = (ObjectNode) localnet.get("families");

    localnetFamilies.set(targetFamily, new ObjectNode(NODE_FACTORY));
    ObjectNode localnetFamily = (ObjectNode) localnetFamilies.get(targetFamily);
    localnetFamily.put("addr", targetAddr);
  }

  private void downgradeLocalnetTo_1_3_13_From_1_4_1() {
    ObjectNode localnet = (ObjectNode) message.get("localnet");
    if (localnet == null) {
      return;
    }
    JsonNode downgradeLocalnetTo_1_From_1_4_1 = localnet.get("families");
    if (downgradeLocalnetTo_1_From_1_4_1 != null) {
      downgradeLocalnetTo_1_From_1_4_1.fieldNames().forEachRemaining(familyName -> {
        ObjectNode family = (ObjectNode) downgradeLocalnetTo_1_From_1_4_1.get(familyName);
        JsonNode removedNode = family.remove("addr");
        if (removedNode != null) {
          family.set("id", removedNode);
        }
      });
    }
  }

  private void downgradeLocalnetTo_1_From_1_3_13() {
    ObjectNode localnet = (ObjectNode) message.get("localnet");
    if (localnet == null) {
      return;
    }
    JsonNode families = localnet.remove("families");
    if (families != null) {
      localnet.set("subsystem", families);
      families.fieldNames().forEachRemaining(familyName -> {
        ObjectNode family = (ObjectNode) families.get(familyName);
        JsonNode removedNode = family.remove("addr");
        if (removedNode != null) {
          family.set("id", removedNode);
        }
      });
    }
  }
}
