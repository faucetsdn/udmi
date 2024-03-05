package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.pod.UdmiServicePod.UDMI_VERSION;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.Common.VERSION_KEY;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * Handle and process messages from the "target" message channel (e.g. PubSub topic). Currently,
 * this is just a simple pass-through with no logic or functionality. It's essentially a TAP point
 * for all events flowing through the system.
 */
@ComponentName("target")
public class TargetProcessor extends ProcessorBase {

  Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

  public TargetProcessor(EndpointConfiguration config) {
    super(config);
  }

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    MessageContinuation continuation = getContinuation(defaultedMessage);
    Envelope envelope = continuation.getEnvelope();
    final String deviceRegistryId = envelope.deviceRegistryId;
    final String transactionId = envelope.transactionId;
    final SubFolder subFolder = envelope.subFolder;
    final String deviceId = envelope.deviceId;

    defaultFields(defaultedMessage);

    publish(defaultedMessage);

    if (deviceId == null) {
      notice("Dropping message with no deviceId: " + stringifyTerse(envelope));
      return;
    }

    if (envelope.subType == null) {
      envelope.subType = SubType.EVENT;
    }
    SubType subType = envelope.subType;
    if (subType != SubType.EVENT) {
      debug("Dropping non-event type " + subType);
      return;
    }

    updateLastSeen(envelope);

    String message = stringify(defaultedMessage);
    debug(format("Reflecting target message %s %s %s %s %s %s", deviceRegistryId, deviceId, subType,
        subFolder, transactionId, defaultedMessage.getClass().getSimpleName()));
    reflectMessage(envelope, message);
  }

  @Override
  protected SubType getExceptionSubType() {
    return SubType.EVENT;
  }

  private void defaultFields(Object defaultedMessage) {
    maybeDefaultField(defaultedMessage, TIMESTAMP_KEY, getDefaultDate());
    maybeDefaultField(defaultedMessage, VERSION_KEY, UDMI_VERSION);
  }

  private Date getDefaultDate() {
    return new Date();
  }

  private <V> void maybeDefaultField(Object defaultedMessage, String fieldName, V defaultValue) {
    //noinspection rawtypes
    if (defaultedMessage instanceof Map messageMap) {
      //noinspection unchecked
      messageMap.computeIfAbsent(fieldName, k -> defaultValue);
      return;
    }

    try {
      ifNotNullThen(defaultedMessage.getClass().getField(fieldName), field -> {
        try {
          if (field.get(defaultedMessage) == null) {
            field.set(defaultedMessage, defaultValue);
          }
        } catch (IllegalAccessException iae) {
          debug("No timestamp field access for " + defaultedMessage.getClass().getName());
        }
      });
    } catch (NoSuchFieldException nfe) {
      debug(format("Skipping default timestamp for " + defaultedMessage.getClass().getName()));
    }
  }

  private void updateLastSeen(Envelope envelope) {
    Instant timestamp =
        ofNullable(envelope.publishTime).map(Date::toInstant).orElseGet(Instant::now);
    lastSeen.compute(envelope.deviceRegistryId,
        (id, was) -> (was == null || was.isBefore(timestamp)) ? timestamp : was);
  }

  public Instant getLastSeen(String registryId) {
    return lastSeen.get(registryId);
  }
}
