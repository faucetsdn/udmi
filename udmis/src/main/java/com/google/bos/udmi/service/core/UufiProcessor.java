package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.toMap;
import static com.google.udmi.util.JsonUtil.toObject;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import udmi.schema.CloudModel;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.Metadata;
import udmi.schema.SystemModel;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

/**
 * Processor for the Unified UDMI Functional Interface (UUFI).
 */
@ComponentName("uufi")
public class UufiProcessor extends ProcessorBase {

  public UufiProcessor(EndpointConfiguration config) {
    super(config);
    info("UUFI Processor initialized");
  }

  @Override
  protected void defaultHandler(Object message) {
    MessageContinuation continuation = getContinuation(message);
    Envelope envelope = continuation.getEnvelope();
    Map<String, Object> messageMap = toMap(message);

    boolean isUufi = "uufi".equals(envelope.gatewayId);
    String principal = (String) messageMap.get("principal");

    info("Received UUFI message: type=%s, folder=%s, gateway=%s, source=%s, principal=%s",
        envelope.subType, envelope.subFolder, envelope.gatewayId, envelope.source,
        principal);

    try {
      if (isUufi) {
        // Message from MQTT (Client or Loopback)

        // Loopback protection using source attribute
        if ("udmis".equals(envelope.source) || "udmis".equals(messageMap.get("source"))) {
          debug("Ignoring loopback of message from udmis source");
          return;
        }

        if (isHandshake(envelope, message)) {
          info("Processing UUFI handshake request");
          handleHandshake(envelope, convertTo(UdmiState.class, message));
        } else if (messageMap.containsKey(PAYLOAD_KEY)) {
          // Wrapped message from MQTT.

          // If it's a config/udmi, it's likely a loopback of our own handshake reply.
          if (envelope.subType == SubType.CONFIG && envelope.subFolder == SubFolder.UDMI) {
            info("Ignoring loopback of UUFI handshake reply");
            return;
          }
          debug("UUFI message keys: " + messageMap.keySet());
          // Ignore loopbacks of system messages forwarded to MQTT
          if (messageMap.containsKey("gatewayId")) {
            String gatewayId = (String) messageMap.get("gatewayId");
            debug("UUFI gatewayId: " + gatewayId + ", clientId: " + DistributorPipe.clientId);
            if (gatewayId != null && gatewayId.startsWith(DistributorPipe.clientId)) {
              info("Ignoring loopback of system message from gateway " + gatewayId);
              return;
            }
          }
          info("Processing UUFI inbound message %s/%s", envelope.subType, envelope.subFolder);
          handleUufiInbound(envelope, messageMap);
        } else {
          info("Received UUFI message without payload key: %s/%s",
              envelope.subType, envelope.subFolder);
        }
      } else {
        // Message from Internal Bus (System) - wrap and send to MQTT
        info("Processing UUFI outbound message %s/%s", envelope.subType, envelope.subFolder);
        handleUufiOutbound(envelope, message);
      }
    } catch (Exception e) {
      error("Error processing UUFI message: " + friendlyStackTrace(e));
    }
  }

  private boolean isHandshake(Envelope envelope, Object message) {
    return envelope.subType == SubType.STATE && envelope.subFolder == SubFolder.UDMI;
  }

  private void handleHandshake(Envelope envelope, UdmiState state) {
    String source = Optional.ofNullable(state.setup).map(setup -> setup.msg_source)
        .orElse(envelope.source);
    String transactionId = Optional.ofNullable(state.setup).map(setup -> setup.transaction_id)
        .orElse(envelope.transactionId);

    debug("Received UUFI handshake from %s (txn: %s)", source, transactionId);

    Envelope replyEnvelope = new Envelope();
    replyEnvelope.subType = SubType.CONFIG;
    replyEnvelope.subFolder = SubFolder.UDMI;
    replyEnvelope.source = "udmis";
    replyEnvelope.principal = Optional.ofNullable(envelope.principal).orElse(source);
    replyEnvelope.transactionId = transactionId;
    replyEnvelope.gatewayId = "uufi";

    info("Sending UUFI handshake reply to %s (txn: %s)", source, transactionId);

    Map<String, Object> wrappedConfig = toMap(replyEnvelope);
    UdmiConfig config = UdmiServicePod.getUdmiConfig(state);
    wrappedConfig.put(PAYLOAD_KEY, config);

    publish(replyEnvelope, wrappedConfig);
  }

  private void handleUufiInbound(Envelope envelope, Map<String, Object> messageMap) {
    Map<String, Object> mutableMap = toMap(messageMap);
    final Object payloadRaw = mutableMap.remove(PAYLOAD_KEY);

    Envelope innerEnvelope = convertTo(Envelope.class, mutableMap);
    if (innerEnvelope.subType == null || innerEnvelope.subFolder == null
        || (envelope.deviceRegistryId != null && innerEnvelope.deviceRegistryId == null)
        || (envelope.deviceId != null && innerEnvelope.deviceId == null)) {
      String msg = String.format("Missing required UUFI envelope fields: "
          + "subType=%s, subFolder=%s, registry=%s, device=%s",
          innerEnvelope.subType, innerEnvelope.subFolder,
          innerEnvelope.deviceRegistryId, innerEnvelope.deviceId);
      throw new IllegalArgumentException(msg);
    }
    boolean registryMismatch = envelope.deviceRegistryId != null
        && !Objects.equals(envelope.deviceRegistryId, innerEnvelope.deviceRegistryId);
    boolean deviceMismatch = envelope.deviceId != null
        && !Objects.equals(envelope.deviceId, innerEnvelope.deviceId);
    if ((envelope.subType != null && innerEnvelope.subType != envelope.subType)
        || (envelope.subFolder != null && innerEnvelope.subFolder != envelope.subFolder)
        || registryMismatch || deviceMismatch) {
      String msg = String.format("Mismatch between topic coordinates (%s/%s/%s/%s) "
          + "and inner envelope fields (%s/%s/%s/%s)",
          envelope.deviceRegistryId, envelope.deviceId,
          envelope.subType, envelope.subFolder,
          innerEnvelope.deviceRegistryId, innerEnvelope.deviceId,
          innerEnvelope.subType, innerEnvelope.subFolder);
      throw new IllegalArgumentException(msg);
    }
    innerEnvelope.payload = null;
    innerEnvelope.gatewayId = null; // Remove the uufi marker for internal bus

    debug("Forwarding UUFI message %s/%s from %s to internal bus",
        innerEnvelope.subType, innerEnvelope.subFolder, envelope.source);

    Object innerPayload = (payloadRaw instanceof String)
        ? toObject(decodeBase64((String) payloadRaw))
        : payloadRaw;

    // Standard UDMI devices expect a complete config merged with previous state.
    if (innerEnvelope.subType == SubType.CONFIG) {
      String configUpdate = processConfigChange(innerEnvelope, innerPayload, null);
      if (configUpdate != null) {
        innerEnvelope.subFolder = null;
        publish(innerEnvelope, com.google.udmi.util.JsonUtil.toMap(configUpdate));
      }
      return;
    }

    publish(innerEnvelope, innerPayload);
  }

  private void handleUufiOutbound(Envelope envelope, Object message) {
    boolean isStateUpdate = envelope.subType == SubType.STATE
        && (envelope.subFolder == null || envelope.subFolder == SubFolder.UPDATE);
    if (isStateUpdate) {
      try {
        StateUpdate stateUpdate = convertTo(StateUpdate.class, message);
        Arrays.stream(udmi.schema.State.class.getFields()).forEach(field -> {
          try {
            String fieldName = field.getName();
            Object fieldMessage = field.get(stateUpdate);
            if (fieldMessage != null) {
              SubFolder subFolder = catchToNull(() -> SubFolder.fromValue(fieldName));
              if (subFolder != null) {
                Map<String, Object> shardedPayload = toMap(fieldMessage);
                shardedPayload.put("version", stateUpdate.version);
                shardedPayload.put("timestamp", stateUpdate.timestamp);

                Envelope shardedEnvelope = deepCopy(envelope);
                shardedEnvelope.subFolder = subFolder;

                handleUufiOutbound(shardedEnvelope, shardedPayload);
              }
            }
          } catch (Exception e) {
            error("Error sharding outbound state field " + field.getName() + ": "
                + friendlyStackTrace(e));
          }
        });
        return;
      } catch (Exception e) {
        error("Error processing monolithic outbound state update: " + friendlyStackTrace(e));
      }
    }

    Object outboundMessage = message;

    if (envelope.subFolder == SubFolder.SYSTEM && message instanceof CloudModel) {
      CloudModel cloudModel = (CloudModel) message;
      String metadataStr = (cloudModel.metadata != null)
          ? cloudModel.metadata.get("udmi_metadata") : null;
      SystemModel systemModel = null;
      if (metadataStr != null) {
        Metadata metadata = convertTo(Metadata.class, metadataStr);
        systemModel = metadata.system;
      }
      if (systemModel == null) {
        systemModel = new SystemModel();
      }
      Map<String, Object> payloadMap = toMap(systemModel);
      payloadMap.put("timestamp", envelope.publishTime);
      payloadMap.put("version", Optional.ofNullable(cloudModel.version).orElse("1.5.2"));
      outboundMessage = payloadMap;
    }

    // Wrap system message for UUFI clients
    Map<String, Object> uufiMessage = toMap(envelope);
    uufiMessage.put(PAYLOAD_KEY, outboundMessage);
    uufiMessage.put("source", "udmis"); // For loopback protection
    if (envelope.principal != null) {
      uufiMessage.put("principal", envelope.principal);
    } else {
      uufiMessage.remove("principal");
    }

    Envelope uufiEnvelope = new Envelope();
    uufiEnvelope.subType = envelope.subType;
    uufiEnvelope.subFolder = envelope.subFolder;
    uufiEnvelope.deviceRegistryId = envelope.deviceRegistryId;
    uufiEnvelope.deviceId = envelope.deviceId;
    uufiEnvelope.source = "udmis"; // Set udmis source
    uufiEnvelope.principal = envelope.principal;
    uufiEnvelope.gatewayId = "uufi";

    debug("Forwarding system message %s/%s to UUFI clients",
        envelope.subType, envelope.subFolder);
    publish(uufiEnvelope, uufiMessage);
  }
}
