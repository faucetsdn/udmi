package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static udmi.schema.Envelope.SubFolder.UPDATE;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.udmi.util.GeneralUtils;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
public class StateProcessor extends ProcessorBase {

  public static final String IOT_ACCESS_COMPONENT = "iot_access";
  private static final Set<String> STATE_SUB_FOLDERS =
      Arrays.stream(SubFolder.values()).map(SubFolder::value).collect(Collectors.toSet());

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    MessageContinuation continuation = getContinuation(defaultedMessage);
    StateUpdate stateMessage = convertToStrict(StateUpdate.class, defaultedMessage);
    shardStateUpdate(stateMessage, continuation);
    updateLastStart(stateMessage, continuation);
  }

  @Override
  protected SubType getExceptionSubType() {
    return SubType.STATE;
  }

  @Override
  protected void registerHandlers() {
    registerHandler(StateUpdate.class, this::stateHandler);
  }

  private void shardStateUpdate(StateUpdate message, MessageContinuation continuation) {
    info("Sharding state message to pipeline out as incremental updates");
    Envelope envelope = continuation.getEnvelope();
    envelope.subType = SubType.STATE;
    envelope.subFolder = UPDATE;
    reflectMessage(envelope, stringify(message));

    Arrays.stream(State.class.getFields()).forEach(field -> {
      try {
        if (STATE_SUB_FOLDERS.contains(field.getName())) {
          ifNotNullThen(field.get(message), fieldMessage -> {
            Map<String, Object> stringObjectMap = toMap(fieldMessage);
            stringObjectMap.put("version", message.version);
            stringObjectMap.put("timestamp", message.timestamp);
            envelope.subFolder = SubFolder.fromValue(field.getName());
            debug("Sharding state " + envelope.subFolder);
            reflectMessage(envelope, stringify(stringObjectMap));
            continuation.publish(stringObjectMap);
          });
        }
      } catch (Exception e) {
        throw new RuntimeException("While extracting field " + field.getName(), e);
      }
    });
    envelope.subFolder = UPDATE;
  }

  private void stateHandler(StateUpdate message) {
    MessageContinuation continuation = getContinuation(message);
    shardStateUpdate(message, continuation);
  }

  private void updateLastStart(StateUpdate message, MessageContinuation continuation) {
    if (message == null || message.system == null || message.system.operation == null
        || message.system.operation.last_start == null) {
      return;
    }

    try {
      Date newLastStart = message.system.operation.last_start;
      Envelope envelope = continuation.getEnvelope();
      processConfigChange(envelope, new HashMap<>(), newLastStart);
    } catch (Exception e) {
      debug("Could not process config last_state update, skipping: "
          + GeneralUtils.friendlyStackTrace(e));
    }
  }

}
