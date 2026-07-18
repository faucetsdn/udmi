package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.toMap;
import static com.google.udmi.util.JsonUtil.toObject;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import java.util.Map;
import java.util.Optional;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
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

        // Loopback protection using instance ID
        if (UdmiServicePod.INSTANCE_ID.equals(principal)) {
          debug("Ignoring loopback of message from this instance: " + UdmiServicePod.INSTANCE_ID);
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
    // Set instance ID as principal for loop detection
    replyEnvelope.principal = UdmiServicePod.INSTANCE_ID;
    replyEnvelope.transactionId = transactionId;
    replyEnvelope.gatewayId = "uufi";

    info("Sending UUFI handshake reply to %s (txn: %s)", source, transactionId);

    Map<String, Object> wrappedConfig = toMap(replyEnvelope);
    UdmiConfig config = UdmiServicePod.getUdmiConfig(state);
    wrappedConfig.put(PAYLOAD_KEY, config);
    wrappedConfig.put("principal", UdmiServicePod.INSTANCE_ID); // For loopback protection

    publish(replyEnvelope, wrappedConfig);
  }

  private void handleUufiInbound(Envelope envelope, Map<String, Object> messageMap) {
    Map<String, Object> mutableMap = toMap(messageMap);
    final Object payloadRaw = mutableMap.remove(PAYLOAD_KEY);

    Envelope innerEnvelope = convertTo(Envelope.class, mutableMap);
    innerEnvelope.payload = null;
    innerEnvelope.gatewayId = null; // Remove the uufi marker for internal bus
    if (innerEnvelope.deviceRegistryId == null) {
      innerEnvelope.deviceRegistryId = envelope.deviceRegistryId;
    }
    if (innerEnvelope.deviceId == null) {
      innerEnvelope.deviceId = envelope.deviceId;
    }
    if (innerEnvelope.subType == null) {
      innerEnvelope.subType = envelope.subType;
    }
    if (innerEnvelope.subFolder == null) {
      innerEnvelope.subFolder = envelope.subFolder;
    }

    debug("Forwarding UUFI message %s/%s from %s to internal bus",
        innerEnvelope.subType, innerEnvelope.subFolder, envelope.source);

    Object innerPayload = (payloadRaw instanceof String)
        ? toObject(decodeBase64((String) payloadRaw))
        : payloadRaw;

    // Standard UDMI devices expect config on the base topic, not folder-specific sub-topics.
    if (innerEnvelope.subType == SubType.CONFIG) {
      innerEnvelope.subFolder = null;
    }

    publish(innerEnvelope, innerPayload);
  }

  private void handleUufiOutbound(Envelope envelope, Object message) {
    // Wrap system message for UUFI clients
    Map<String, Object> uufiMessage = toMap(envelope);
    uufiMessage.put(PAYLOAD_KEY, message);
    uufiMessage.put("principal", UdmiServicePod.INSTANCE_ID); // For loopback protection

    Envelope uufiEnvelope = new Envelope();
    uufiEnvelope.subType = envelope.subType;
    uufiEnvelope.subFolder = envelope.subFolder;
    uufiEnvelope.deviceRegistryId = envelope.deviceRegistryId;
    uufiEnvelope.deviceId = envelope.deviceId;
    uufiEnvelope.principal = UdmiServicePod.INSTANCE_ID; // Set instance ID
    uufiEnvelope.gatewayId = "uufi";

    debug("Forwarding system message %s/%s to UUFI clients",
        envelope.subType, envelope.subFolder);
    publish(uufiEnvelope, uufiMessage);
  }
}
