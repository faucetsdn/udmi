package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.fromStringStrict;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.util.Objects.requireNonNull;
import static udmi.schema.Envelope.SubFolder.UPDATE;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.udmi.util.GeneralUtils;
import java.util.Arrays;
import java.util.Date;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.Config;
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

  private static final Set<String> STATE_SUB_FOLDERS =
      Arrays.stream(SubFolder.values()).map(SubFolder::value).collect(Collectors.toSet());
  public static final String IOT_ACCESS_COMPONENT = "iot_access";

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    MessageContinuation continuation = getContinuation(defaultedMessage);
    Envelope envelope = continuation.getEnvelope();
    String registryId = envelope.deviceRegistryId;
    String deviceId = envelope.deviceId;
    StateUpdate stateMessage = convertToStrict(StateUpdate.class, defaultedMessage);
    updateLastStart(stateMessage, registryId, deviceId);
    shardStateUpdate(stateMessage, continuation);
  }

  @Override
  protected void registerHandlers() {
    registerHandler(StateUpdate.class, this::stateHandler);
  }

  private void stateHandler(StateUpdate message) {
    MessageContinuation continuation = getContinuation(message);
    shardStateUpdate(message, continuation);
  }

  private void shardStateUpdate(StateUpdate message, MessageContinuation continuation) {
    info("Sharding state message to pipeline out as incremental updates");
    Envelope envelope = continuation.getEnvelope();
    envelope.subType = SubType.STATE;
    Arrays.stream(State.class.getFields()).forEach(field -> {
      try {
        if (STATE_SUB_FOLDERS.contains(field.getName())) {
          ifNotNullThen(field.get(message), fieldMessage -> {
            envelope.subFolder = SubFolder.fromValue(field.getName());
            debug("Sharding state " + envelope.subFolder);
            reflectMessage(envelope, stringify(message));
            continuation.publish(fieldMessage);
          });
        }
      } catch (Exception e) {
        throw new RuntimeException("While extracting field " + field.getName(), e);
      }
    });
  }

  private void updateLastStart(StateUpdate message, String registryId, String deviceId) {
    if (message == null || message.system == null || message.system.operation == null
        || message.system.operation.last_start == null) {
      return;
    }
    try {
      IotAccessBase iotAccess = UdmiServicePod.getComponent(IOT_ACCESS_COMPONENT);
      Date newLastStart = message.system.operation.last_start;
      Entry<String, String> configEntry = iotAccess.fetchConfig(registryId, deviceId);
      Config configMessage = fromStringStrict(Config.class, configEntry.getValue());
      Date oldLastStart = configMessage.system.operation.last_start;
      boolean shouldUpdate = oldLastStart == null || oldLastStart.before(newLastStart);
      debug("Last start was %s, now %s, updating %s", getTimestamp(oldLastStart),
          getTimestamp(newLastStart), shouldUpdate);
      if (shouldUpdate) {
        configMessage.system.operation.last_start = newLastStart;
        iotAccess.modifyConfig(registryId, deviceId, UPDATE, stringify(configMessage));
      }
    } catch (Exception e) {
      debug("Could not process config last_state update, skipping: "
          + GeneralUtils.friendlyStackTrace(e));
    }
  }

}
