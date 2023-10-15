package com.google.udmi.util;

import static com.google.udmi.util.Common.DOWNGRADED_FROM;
import static com.google.udmi.util.Common.VERSION_KEY;
import static com.google.udmi.util.GeneralUtils.OBJECT_MAPPER_RAW;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.isNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Map;
import udmi.schema.Envelope.SubType;

/**
 * Downgrade a message to a previous UDMI schema version.
 */
public class MessageDowngrader {

  private static final TextNode LEGACY_VERSION = new TextNode("1");
  private static final JsonNode LEGACY_REPLACEMENT = new IntNode(1);
  private final ObjectNode message;
  private final String schema;
  private int major;
  private int minor;
  private int patch;

  /**
   * Create message down-grader.
   *
   * @param schemaName  schema to downgrade
   * @param messageJson message json
   */
  public MessageDowngrader(String schemaName, JsonNode messageJson) {
    schema = schemaName;
    this.message = (ObjectNode) messageJson;
  }

  /**
   * Create message down-grader.
   *
   * @param schemaName schema to downgrade
   * @param message    message object
   */
  public MessageDowngrader(String schemaName, Object message) {
    this(schemaName, OBJECT_MAPPER_RAW.valueToTree(message));
  }

  static String convertVersion(JsonNode versionNode) {
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

  /**
   * Downgrade a message to a target version.
   *
   * @param version target downgrade version (as a SchemaVersion)
   *
   * @return downgraded object
   */
  public Map<String, Object> downgrade(SchemaVersion version) {
    return downgrade(new TextNode(version.key()));
  }

  /**
   * Downgrade a message to a target version.
   *
   * @param targetVersion target downgrade version (as a JsonNode)
   *
   * @return downgraded object
   */
  public Map<String, Object> downgrade(JsonNode targetVersion) {
    return switch (schema) {
      case "config" -> downgradeConfig(targetVersion);
      case "state" -> downgradeState(targetVersion);
      default ->
        throw new IllegalArgumentException("Unknown downgrade schema " + schema);
    };
  }

  private Map<String, Object> downgradeState(JsonNode targetVersion) {
    final String version = convertVersion(targetVersion);

    ObjectNode system = (ObjectNode) message.get("system");
    if (version.equals(SchemaVersion.VERSION_1_4_0.key())) {
      ifNotNullThen(system, map -> {
        JsonNode operation = map.remove("operation");
        ifNotNullThen(operation, src -> map.set("operational", src.get("operational")));
      });
    } else {
      throw new RuntimeException("Unknown target legacy version " + version);
    }
    return JsonUtil.asMap(message);
  }

  private Map<String, Object> downgradeConfig(JsonNode targetVersion) {
    downgradeConfigRaw(targetVersion);
    return JsonUtil.asMap(message);
  }

  private void downgradeConfigRaw(JsonNode targetVersion) {
    final String version = convertVersion(targetVersion);
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
      ObjectNode system = (ObjectNode) message.get("system");
      if (system != null) {
        system.remove("operation");
      }
    }

    JsonNode useVersion = targetVersion.equals(LEGACY_VERSION) ? LEGACY_REPLACEMENT : targetVersion;

    message.set(DOWNGRADED_FROM, message.get(VERSION_KEY));
    message.set(VERSION_KEY, useVersion);
  }

  private void downgradeLocalnet() {
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
