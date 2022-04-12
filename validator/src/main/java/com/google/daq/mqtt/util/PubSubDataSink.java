package com.google.daq.mqtt.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import com.google.daq.mqtt.validator.Validator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.units.qual.A;
import udmi.schema.AuditEvent;
import udmi.schema.Entry;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.Level;
import udmi.schema.Target;

/**
 * Enable pushing validation results to a PubSub topic.
 */
public class PubSubDataSink implements DataSink {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
  public static final String SUB_FOLDER_ATTRIBUTE_KEY = "subFolder";
  public static final String SUB_TYPE_ATTRIBUTE_KEY = "subType";

  private final PubSubPusher pubSubPusher;
  private final static String AUDIT_SUB_FOLDER = SubFolder.AUDIT.toString();

  public PubSubDataSink(String projectId, String target) {
    pubSubPusher = new PubSubPusher(projectId, target);
  }

  @Override
  public void validationResult(String deviceId, String schemaId, Map<String, String> origAttributes,
      Object message, ErrorTree errorTree) {
    try {
      Map<String, String> attributes = new HashMap<>(origAttributes);
      String subFolder = attributes.get(SUB_FOLDER_ATTRIBUTE_KEY);
      String rawSubType = attributes.get(SUB_TYPE_ATTRIBUTE_KEY);
      String subType = rawSubType == null ? SubType.EVENT.toString() : rawSubType;

      // Do not report auditing of audit messages to prevent message loops.
      if (AUDIT_SUB_FOLDER.equals(subFolder)) {
        return;
      }

      AuditEvent auditEvent = new AuditEvent();
      auditEvent.version = ConfigUtil.UDMI_VERSION;
      auditEvent.timestamp = new Date();
      auditEvent.target = new Target();
      auditEvent.target.subFolder = subFolder;
      auditEvent.target.subType = subType;
      String detailString = String.format("Processing message %s/%s", subType, subFolder);
      auditEvent.status = getResultStatus(detailString, errorTree);
      attributes.put("subType", SubType.EVENT.value());
      attributes.put("subFolder", AUDIT_SUB_FOLDER);
      String messageString = OBJECT_MAPPER.writeValueAsString(auditEvent);
      pubSubPusher.sendMessage(attributes, messageString);
    } catch (Exception e) {
      throw new RuntimeException("While publishing validation message for " + deviceId);
    }
  }

  private Entry getResultStatus(String detailHeader, ErrorTree errorTree) {
    Entry entry = new Entry();
    if (errorTree == null) {
      entry.level = Level.INFO.value();
      entry.message = "No message errors found";
      entry.category = "audit.message.clean";
      entry.detail = detailHeader;
    } else {
      entry.level = Level.ERROR.value();
      entry.message = "Message validation errors";
      entry.category = "audit.message.error";
      entry.detail = detailHeader + "\n" + errorTree.asString();
    }
    return entry;
  }
}
