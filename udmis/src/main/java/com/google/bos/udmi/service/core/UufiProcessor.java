package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.decodeBase64;
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

    debug("Received UUFI message: type=%s, folder=%s, gateway=%s, source=%s, isUufi=%s",
        envelope.subType, envelope.subFolder, envelope.gatewayId, envelope.source, isUufi);

    try {
      if (isUufi) {
        // Message from MQTT (Client or Loopback)
        if (isHandshake(envelope, message)) {
          handleHandshake(envelope, convertTo(UdmiState.class, message));
        } else if (messageMap.containsKey(PAYLOAD_KEY)) {
          // Wrapped message from MQTT. 
          // If it's a config/udmi, it's likely a loopback of our own handshake reply.
          if (envelope.subType == SubType.CONFIG && envelope.subFolder == SubFolder.UDMI) {
            debug("Ignoring loopback of UUFI handshake reply");
            return;
          }
          handleUufiInbound(envelope, messageMap);
        }
      } else {
        // Message from Internal Bus (System) - wrap and send to MQTT
        handleUufiOutbound(envelope, message);
      }
    } catch (Exception e) {
      error("Error processing UUFI message: " + e.getMessage());
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
    replyEnvelope.principal = source;
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
    innerEnvelope.payload = null; 
    innerEnvelope.gatewayId = null; // Remove the uufi marker for internal bus
    
    debug("Forwarding UUFI message %s/%s from %s to internal bus", 
        innerEnvelope.subType, innerEnvelope.subFolder, envelope.source);
    
    Object innerPayload = (payloadRaw instanceof String)
        ? toObject(decodeBase64((String) payloadRaw))
        : payloadRaw;
    publish(innerEnvelope, innerPayload);
  }

  private void handleUufiOutbound(Envelope envelope, Object message) {
    // Wrap system message for UUFI clients
    Map<String, Object> uufiMessage = toMap(envelope);
    uufiMessage.put(PAYLOAD_KEY, message);
    
    Envelope uufiEnvelope = new Envelope();
    uufiEnvelope.subType = envelope.subType;
    uufiEnvelope.subFolder = envelope.subFolder;
    uufiEnvelope.deviceRegistryId = envelope.deviceRegistryId;
    uufiEnvelope.deviceId = envelope.deviceId;
    uufiEnvelope.gatewayId = "uufi"; 
    
    debug("Forwarding system message %s/%s to UUFI clients", 
        envelope.subType, envelope.subFolder);
    publish(uufiEnvelope, uufiMessage);
  }
}
