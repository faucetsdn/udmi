package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.MessagePipe.messageHandlerFor;
import static com.google.udmi.util.GeneralUtils.ifNotNull;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.messaging.MessagePipe.HandlerSpecification;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.pod.ComponentBase;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.udmi.util.Common;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.MessageConfiguration;
import udmi.schema.State;

/**
 * Core UDMIS function to process raw State messages from devices and normalize them for
 * the rest of the system. Involves tagging the envelope with the appropriate designators,
 * and splitting up the monolithic block into constituent parts.
 */
public class StateHandler extends ComponentBase {

  private static final Set<String> STATE_SUB_FOLDERS =
      Arrays.stream(SubFolder.values()).map(SubFolder::value).collect(Collectors.toSet());

  private final List<HandlerSpecification> messageHandlers = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::exceptionHandler),
      messageHandlerFor(StateUpdate.class, this::messageHandler)
  );

  final MessagePipe pipe;

  public StateHandler(MessagePipe pipe) {
    this.pipe = pipe;
  }

  private void exceptionHandler(Exception e) {
    info("Received processing exception: " + Common.getExceptionMessage(e));
    e.printStackTrace();
  }

  private void defaultHandler(Object defaultedMessage) {
    MessageContinuation continuation = pipe.getContinuation(defaultedMessage);
    Envelope envelope = continuation.envelope;
    Preconditions.checkState(envelope.subType == null, "subType is not null");
    Preconditions.checkState(envelope.subFolder == null, "subFolder is not null");
    info("Processing untyped (assumed state) message into state update");
    envelope.subType = SubType.STATE;
    envelope.subFolder = SubFolder.UPDATE;
    continuation.reprocess();
  }

  private void messageHandler(State message) {
    info("Sharding state message to pipeline out as incremental updates");
    Arrays.stream(State.class.getFields()).forEach(field -> {
      try {
        if (STATE_SUB_FOLDERS.contains(field.getName())) {
          ifNotNull(field.get(message), x -> pipe.publish(x));
        }
      } catch (Exception e) {
        throw new RuntimeException("While extracting field " + field.getName(), e);
      }
    });
  }

  public static StateHandler forConfig(MessageConfiguration configuration) {
    return new StateHandler(MessagePipe.from(configuration));
  }

  public void activate() {
    pipe.registerHandlers(messageHandlers);
    pipe.activate();
  }
}
