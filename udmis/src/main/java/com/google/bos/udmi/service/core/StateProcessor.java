package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.convertToStrict;
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

  @Override
  protected void defaultHandler(Object originalMessage) {
    Object upgradedMessage = new MessageUpgrader(STATE_SCHEMA, originalMessage).upgrade();
    StateUpdate stateMessage = convertToStrict(StateUpdate.class, upgradedMessage);
    shardStateUpdate(getContinuation(originalMessage), stateMessage);
    updateLastStart(getContinuation(originalMessage).getEnvelope(), stateMessage);
  }

  @Override
  protected SubType getExceptionSubType() {
    return SubType.STATE;
  }

  @Override
  protected void registerHandlers() {
    registerHandler(StateUpdate.class, this::stateHandler);
  }

  private void shardStateUpdate(MessageContinuation continuation, StateUpdate message) {
    info("Sharding state message to pipeline as incremental updates");
    Envelope envelope = continuation.getEnvelope();
    envelope.subType = SubType.STATE;
    envelope.subFolder = UPDATE;
    reflectMessage(envelope, stringify(message));
    String originalTransaction = envelope.transactionId;
    AtomicInteger txnSuffix = new AtomicInteger();
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

  private void stateHandler(StateUpdate message) {
    shardStateUpdate(getContinuation(message), message);
  }

}
