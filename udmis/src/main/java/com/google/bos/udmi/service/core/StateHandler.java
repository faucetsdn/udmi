package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.MessagePipe.messageHandlerFor;
import static com.google.udmi.util.GeneralUtils.ifNotNull;
import static com.google.udmi.util.JsonUtil.convertTo;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.messaging.MessagePipe.HandlerSpecification;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.common.collect.ImmutableList;
import com.google.udmi.util.Common;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.MessageConfiguration;
import udmi.schema.State;

/**
 * Core UDMIS function to process raw State messages from devices and normalize them for the rest of
 * the system. Involves tagging the envelope with the appropriate designators, and splitting up the
 * monolithic block into constituent parts.
 */
public class StateHandler extends ContainerBase {

  private static final Set<String> STATE_SUB_FOLDERS =
      Arrays.stream(SubFolder.values()).map(SubFolder::value).collect(Collectors.toSet());
  private final MessagePipe pipe;
  int exceptionCount;
  int defaultCount;
  private final List<HandlerSpecification> messageHandlers = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::exceptionHandler),
      messageHandlerFor(StateUpdate.class, this::stateHandler)
  );

  public StateHandler(MessagePipe pipe) {
    this.pipe = pipe;
  }

  public static StateHandler forConfig(MessageConfiguration configuration) {
    return new StateHandler(MessagePipe.from(configuration));
  }

  private void defaultHandler(Object defaultedMessage) {
    defaultCount++;
    stateHandler(convertTo(State.class, defaultedMessage));
  }

  private void exceptionHandler(Exception e) {
    exceptionCount++;
    info("Received processing exception: " + Common.getExceptionMessage(e));
    e.printStackTrace();
  }

  private void stateHandler(State message) {
    info("Sharding state message to pipeline out as incremental updates");
    Arrays.stream(State.class.getFields()).forEach(field -> {
      try {
        if (STATE_SUB_FOLDERS.contains(field.getName())) {
          ifNotNull(field.get(message), pipe::publish);
        }
      } catch (Exception e) {
        throw new RuntimeException("While extracting field " + field.getName(), e);
      }
    });
  }

  public void activate() {
    pipe.registerHandlers(messageHandlers);
    pipe.activate();
  }
}
