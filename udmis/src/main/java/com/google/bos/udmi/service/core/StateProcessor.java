package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.convertToStrict;

import com.google.bos.udmi.service.messaging.StateUpdate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.State;

/**
 * Core UDMIS function to process raw State messages from devices and normalize them for the rest of
 * the system. Involves tagging the envelope with the appropriate designators, and splitting up the
 * monolithic block into constituent parts.
 */
public class StateProcessor extends UdmisComponent {

  private static final Set<String> STATE_SUB_FOLDERS =
      Arrays.stream(SubFolder.values()).map(SubFolder::value).collect(Collectors.toSet());

  @Override
  protected void registerHandlers() {
    registerHandler(StateUpdate.class, this::stateHandler);
  }

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    stateHandler(convertToStrict(StateUpdate.class, defaultedMessage));
  }

  private void stateHandler(StateUpdate message) {
    info("Sharding state message to pipeline out as incremental updates");
    Arrays.stream(State.class.getFields()).forEach(field -> {
      try {
        if (STATE_SUB_FOLDERS.contains(field.getName())) {
          ifNotNullThen(field.get(message), this::publish);
        }
      } catch (Exception e) {
        throw new RuntimeException("While extracting field " + field.getName(), e);
      }
    });
  }

}
