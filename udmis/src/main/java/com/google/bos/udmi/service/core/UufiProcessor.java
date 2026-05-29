package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static com.google.udmi.util.JsonUtil.toObject;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import java.util.Map;
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

  public static final String PAYLOAD_KEY = "payload";

  public UufiProcessor(EndpointConfiguration config) {
    super(config);
  }

  @Override
  protected void defaultHandler(Object message) {
    MessageContinuation continuation = getContinuation(message);
    Envelope envelope = continuation.getEnvelope();
    Map<String, Object> messageMap = toMap(message);

    try {
      if (isHandshake(envelope, message)) {
        handleHandshake(envelope, convertTo(UdmiState.class, message));
      } else if (messageMap.containsKey(PAYLOAD_KEY)) {
        handleUufiMessage(envelope, messageMap);
      } else {
        // If it's not a UUFI-wrapped message, it might be a raw UDMI message 
        // from the internal system that we need to wrap and send to clients.
        forwardToClients(envelope, message);
      }
    } catch (Exception e) {
      error("Error processing UUFI message: " + e.getMessage());
    }
  }

  private boolean isHandshake(Envelope envelope, Object message) {
    // UUFI handshake Step 1: Client publishes state to /uufi/c/state/udmi
    return envelope.subType == SubType.STATE && envelope.subFolder == SubFolder.UDMI;
  }

  private void handleHandshake(Envelope envelope, UdmiState state) {
    debug("Received UUFI handshake from " + envelope.source);
    
    UdmiConfig config = UdmiServicePod.getUdmiConfig(state);
    
    Envelope replyEnvelope = new Envelope();
    replyEnvelope.subType = SubType.CONFIG;
    replyEnvelope.subFolder = SubFolder.UDMI;
    replyEnvelope.principal = envelope.source;
    replyEnvelope.transactionId = envelope.transactionId;
    
    // Mark this as a UUFI response so the pipe knows how to handle it
    replyEnvelope.rawFolder = "uufi"; 

    debug("Sending UUFI handshake reply to " + envelope.source);
    publish(replyEnvelope, config);
  }

  private void handleUufiMessage(Envelope envelope, Map<String, Object> messageMap) {
    Map<String, Object> mutableMap = toMap(messageMap);
    Object payloadRaw = mutableMap.remove(PAYLOAD_KEY);
    Object innerPayload = (payloadRaw instanceof String) 
        ? toObject(decodeBase64((String) payloadRaw)) 
        : payloadRaw;
    
    Envelope innerEnvelope = convertTo(Envelope.class, mutableMap);
    innerEnvelope.payload = null; 
    
    debug("Forwarding UUFI message %s/%s from %s", 
        innerEnvelope.subType, innerEnvelope.subFolder, envelope.source);
    
    // Publish it to the internal bus so other processors can see it.
    publish(innerEnvelope, innerPayload);
  }

  private void forwardToClients(Envelope envelope, Object message) {
    // This is a message from the system (e.g. telemetry, state update)
    // that should be forwarded to UUFI clients.
    
    // Wrap it in a UUFI envelope
    Map<String, Object> uufiMessage = toMap(envelope);
    uufiMessage.put(PAYLOAD_KEY, message);
    
    Envelope uufiEnvelope = new Envelope();
    uufiEnvelope.subType = envelope.subType;
    uufiEnvelope.subFolder = envelope.subFolder;
    uufiEnvelope.deviceRegistryId = envelope.deviceRegistryId;
    uufiEnvelope.deviceId = envelope.deviceId;
    uufiEnvelope.rawFolder = "uufi"; // Indicator for UUFI wrapping
    
    debug("Forwarding system message %s/%s to UUFI clients", 
        envelope.subType, envelope.subFolder);
    publish(uufiEnvelope, uufiMessage);
  }
}
