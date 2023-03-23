package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.MessagePipe.messageHandlerFor;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.messaging.MessagePipe.HandlerSpecification;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.pod.ComponentBase;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.udmi.util.Common;
import java.util.List;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.MessageConfiguration;
import udmi.schema.State;

public class StateHandler extends ComponentBase {

  private final List<HandlerSpecification> MESSAGE_HANDLERS = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::exceptionHandler),
      messageHandlerFor(StateUpdate.class, this::messageHandler)
  );

  private final MessagePipe pipe;

  public StateHandler(MessagePipe pipe) {
    this.pipe = pipe;
  }

  private void exceptionHandler(Exception e) {
    info("Received processing exception: " + Common.getExceptionMessage(e));
    e.printStackTrace();
  }

  private void defaultHandler(Object unknown) {
    MessageContinuation continuation = pipe.getContinuation(unknown);
    Envelope envelope = continuation.envelope;
    Preconditions.checkState(envelope.subType == null, "subType is not null");
    Preconditions.checkState(envelope.subFolder == null, "subFolder is not null");
    info("Processing default message, converting to a state update");
    envelope.subType = SubType.STATE;
    envelope.subFolder = SubFolder.UPDATE;
    continuation.reprocess();
  }

  public void messageHandler(State message) {
    info("Passing message to pipeline out");
    pipe.publish(message);
  }

  public static StateHandler forConfig(MessageConfiguration configuration) {
    return new StateHandler(MessagePipe.from(configuration));
  }

  public void activate() {
    pipe.registerHandlers(MESSAGE_HANDLERS);
    pipe.activate();
  }
}
