package com.google.daq.mqtt.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.api.core.ApiFuture;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import com.google.daq.mqtt.validator.ReportingDevice;
import com.google.daq.mqtt.validator.Validator.MetadataReport;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import udmi.schema.AuditEvent;
import udmi.schema.Entry;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.Level;
import udmi.schema.Target;
import udmi.schema.ValidationEvent;

/**
 * Enable pushing validation results to a PubSub topic.
 */
public class PubSubDataSink implements DataSink {

  private static final String SUB_FOLDER_ATTRIBUTE_KEY = "subFolder";
  private static final String SUB_TYPE_ATTRIBUTE_KEY = "subType";
  private static final String VALIDATION_EVENT_TOPIC = "validation/event";
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
  private static final String AUDIT_SUB_FOLDER = SubFolder.AUDIT.toString();
  private static final String VALIDATION_DEVICE = "_validator";
  private final PubSubPusher pubSubPusher;
  private final String registryId;
  private final String projectId;

  public PubSubDataSink(String projectId, String registryId, String target) {
    this.projectId = projectId;
    this.registryId = registryId;
    pubSubPusher = new PubSubPusher(projectId, target);
  }

  @Override
  public void validationResult(Map<String, String> origAttributes, Object message,
      ReportingDevice reportingDevice) {
    try {
      Map<String, String> attributes = new HashMap<>(origAttributes);
      final String subFolder = attributes.get(SUB_FOLDER_ATTRIBUTE_KEY);
      final String rawSubType = attributes.get(SUB_TYPE_ATTRIBUTE_KEY);
      final String subType = rawSubType == null ? SubType.EVENT.toString() : rawSubType;

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
      attributes.put("subType", SubType.EVENT.value());
      attributes.put("subFolder", AUDIT_SUB_FOLDER);
      String messageString = OBJECT_MAPPER.writeValueAsString(auditEvent);
      pubSubPusher.sendMessage(attributes, messageString);
    } catch (Exception e) {
      throw new RuntimeException("While publishing validation message");
    }
  }

  @Override
  public void validationReport(MetadataReport metadataReport) {
    try {
      ValidationEvent validationEvent = new ValidationEvent();
      System.err.println("Sending validation report for " + registryId);
      String subFolder = String.format("events/%s/%s", VALIDATION_DEVICE, VALIDATION_EVENT_TOPIC);
      Map<String, String> attributes = Map.of(
          "deviceId", VALIDATION_DEVICE,
          "projectId", projectId,
          "subFolder", subFolder,
          "deviceId", registryId // intentional b/c of udmi_reflect function
      );
      pubSubPusher.sendMessage(attributes, OBJECT_MAPPER.writeValueAsString(validationEvent));
    } catch (Exception e) {
      System.err.println("Exception handling periodic report");
      e.printStackTrace();
    }

  }
}
