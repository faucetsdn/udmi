package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static com.google.udmi.util.MessageUpgrader.STATE_SCHEMA;
import static udmi.schema.Envelope.SubFolder.UPDATE;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.udmi.util.MessageUpgrader;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.State;

/**
 * Core UDMIS function to process raw State messages from devices and normalize them for the rest of
 * the system. Involves tagging the envelope with the appropriate designators, and splitting up the
 * monolithic block into constituent parts.
 */
@ComponentName("state")
public class StateProcessor extends ProcessorBase {

  private static final Set<String> STATE_SUB_FOLDERS =
      Arrays.stream(SubFolder.values()).map(SubFolder::value).collect(Collectors.toSet());

  public StateProcessor(EndpointConfiguration config) {
    super(config);
  }

  @Override
  protected void defaultHandler(Object originalMessage) {
    MessageContinuation continuation = getContinuation(originalMessage);
    Envelope envelope = continuation.getEnvelope();
    envelope.subType = SubType.STATE;
    envelope.subFolder = UPDATE;

    reflectMessage(envelope, stringify(originalMessage));

    Object upgradedMessage = new MessageUpgrader(STATE_SCHEMA, originalMessage).upgrade();
    StateUpdate stateMessage = convertTo(StateUpdate.class, upgradedMessage);
    shardStateUpdate(continuation, envelope, stateMessage);

    updateLastStart(getContinuation(originalMessage).getEnvelope(), stateMessage);
  }

  @Override
  protected SubType getExceptionSubType() {
    return SubType.STATE;
  }

  private void shardStateUpdate(MessageContinuation continuation, Envelope envelope,
      StateUpdate message) {
    continuation.publish(message);
    String originalTransaction = envelope.transactionId;
    AtomicInteger txnSuffix = new AtomicInteger();
    info("Sharding state message for %s/%s %s", envelope.deviceRegistryId, envelope.deviceId,
        originalTransaction);
    Arrays.stream(State.class.getFields()).forEach(field -> {
      try {
        if (STATE_SUB_FOLDERS.contains(field.getName())) {
          ifNotNullThen(field.get(message), fieldMessage -> {
            Map<String, Object> stringObjectMap = toMap(fieldMessage);
            stringObjectMap.put("version", message.version);
            stringObjectMap.put("timestamp", message.timestamp);
            envelope.subFolder = SubFolder.fromValue(field.getName());
            envelope.transactionId = originalTransaction + "-" + txnSuffix.getAndIncrement();
            debug("Sharding state %s %s", envelope.subFolder, envelope.transactionId);
            reflectMessage(envelope, stringify(stringObjectMap));
            continuation.publish(stringObjectMap);
          });
        }
      } catch (Exception e) {
        throw new RuntimeException("While extracting field " + field.getName(), e);
      }
    });
  }

  /**
   * Handle state update messages.
   */
  @DispatchHandler
  public void stateHandler(StateUpdate message) {
    MessageContinuation continuation = getContinuation(message);
    Envelope envelope = continuation.getEnvelope();
    reflectMessage(envelope, stringify(message));
    shardStateUpdate(continuation, envelope, message);
  }

}
