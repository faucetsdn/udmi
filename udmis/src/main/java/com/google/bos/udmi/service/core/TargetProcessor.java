package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import java.util.Optional;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * Handle and process messages from the "target" message channel (e.g. PubSub topic). Currently,
 * this is just a simple pass-through with no logic or functionality. It's essentially a TAP point
 * for all events flowing through the system.
 */
public class TargetProcessor extends ProcessorBase {

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    MessageContinuation continuation = getContinuation(defaultedMessage);
    Envelope envelope = continuation.getEnvelope();
    String deviceId = envelope.deviceId;

    if (deviceId == null) {
      debug("Dropping message with no deviceId");
      return;
    }

    SubType subType = Optional.ofNullable(envelope.subType).orElse(SubType.EVENT);
    if (subType != SubType.EVENT) {
      debug("Dropping non-event type " + subType);
      return;
    }

    String deviceRegistryId = envelope.deviceRegistryId;
    String transactionId = envelope.transactionId;
    SubFolder subFolder = envelope.subFolder;

    String message = stringify(defaultedMessage);
    debug(format("Reflecting %s %s %s %s %s %s", deviceRegistryId, deviceId, subType, subFolder,
        message, transactionId));
    reflectMessage(envelope, message);
  }
}
