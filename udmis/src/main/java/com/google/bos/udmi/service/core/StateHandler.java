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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.Common;
import com.google.udmi.util.GeneralUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.MessageConfiguration;
import udmi.schema.State;

public class StateHandler extends ComponentBase {

  private static final Set<String> stateSubFolders =
      Arrays.stream(SubFolder.values()).map(SubFolder::value).collect(Collectors.toSet());

  private final List<HandlerSpecification> MESSAGE_HANDLERS = ImmutableList.of(
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

  public void messageHandler(State message) {
    info("Sharding state message to pipeline out as incremental updates");
    Arrays.stream(State.class.getFields()).forEach(field -> {
      try {
        if (stateSubFolders.contains(field.getName())) {
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
    pipe.registerHandlers(MESSAGE_HANDLERS);
    pipe.activate();
  }
}
