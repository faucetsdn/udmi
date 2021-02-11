package com.google.daq.mqtt.validator;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class ExpectedMessage {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setSerializationInclusion(Include.NON_NULL);

  private static final String JSON_SUFFIX = ".json";
  private final JsonNode message;
  private final File source;
  private final String subType;
  private final String subFolder;

  public ExpectedMessage(File source) {
    try {
      this.source = source;
      this.message = OBJECT_MAPPER.readTree(source);
      String[] parts = getSendParts();
      subType = parts[1];
      subFolder = parts[2];
    } catch (Exception e) {
      throw new RuntimeException("While creating message from " + source.getAbsolutePath(), e);
    }
  }

  public List<String> matches(Map<String, Object> against,
      Map<String, String> attributes) {
    String messageSubType = getMessageSubType(attributes);
    String messageSubFolder = attributes.get("subFolder");
    List<String> errors = new ArrayList<>();
    if (!subType.equals(messageSubType)) {
      errors.add(String.format("expected subType %s == %s", subType, messageSubType));
    }
    if (!subFolder.equals(messageSubFolder)) {
      errors.add(String.format("expected subFolder %s == %s", subFolder, messageSubFolder));
    }
    if (errors.isEmpty()) {
      matches(message, against, errors);
    }
    return errors;
  }

  private String getMessageSubType(Map<String, String> attributes) {
    String subType = attributes.get("subType");
    return subType == null ? null : subType.substring(0, subType.length() - 1);
  }

  private static boolean matches(JsonNode source, Map<String, Object> target, List<String> errors) {
    Iterator<String> fieldNames = source.fieldNames();
    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next();
      JsonNode subSource = source.get(fieldName);
      if (!target.containsKey(fieldName)) {
        if (subSource.isNull()) {
          return true;
        }
        errors.add(String.format("missing '%s'", fieldName));
        return false;
      }
      Object againstNode = target.get(fieldName);
      final boolean matches;
      final String comparison;
      switch (subSource.getNodeType()) {
        case BOOLEAN:
          comparison = String.format("%s == %s", subSource.asBoolean(), againstNode);
          matches = againstNode.equals(subSource.asBoolean());
          break;
        case NUMBER:
          if (subSource.canConvertToInt()) {
            comparison = String.format("%d == %s", subSource.asInt(), againstNode);
            matches = againstNode.equals(subSource.asInt());
          } else {
            comparison = String.format("%f == %s", subSource.asDouble(), againstNode);
            matches = againstNode.equals(subSource.asDouble());
          }
          break;
        case OBJECT:
          comparison = "object";
          Map<String, Object> subTarget = (Map<String, Object>) target.get(fieldName);
          matches = matches(subSource, subTarget, errors);
          break;
        case STRING:
          comparison = String.format("'%s' == '%s'", subSource.asText(), againstNode);
          matches = againstNode.equals(subSource.asText());
          break;
        default:
          throw new RuntimeException("Unsupported JSON node type " + subSource.getNodeType());
      }
      if (!matches) {
        errors.add(String.format("expected '%s' %s", fieldName, comparison));
        return false;
      }
    }
    return true;
  }

  public boolean isSendMessage() {
    return "config".equals(subType);
  }

  public String getSendTopic() {
    return String.format("%s/%s", subType, subFolder);
  }

  private String[] getSendParts() {
    String name = source.getName();
    String[] parts = name.substring(0, name.length() - JSON_SUFFIX.length()).split("_");
    if (!name.endsWith(JSON_SUFFIX) || parts.length != 3) {
      throw new IllegalArgumentException("Unrecognized sequence file " + source.getAbsolutePath());
    }
    return parts;
  }

  public String createMessage() {
    try {
      return OBJECT_MAPPER.writeValueAsString(message);
    } catch (Exception e) {
      throw new RuntimeException("While generating message for " + getName(), e);
    }
  }

  String getName() {
    return source.getName();
  }
}
