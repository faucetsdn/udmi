package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.MessagePipe.messageHandlerFor;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.messaging.MessagePipe.HandlerSpecification;
import com.google.bos.udmi.service.pod.ComponentBase;
import com.google.common.collect.ImmutableList;
import com.google.udmi.util.Common;
import java.util.List;
import udmi.schema.Envelope;
import udmi.schema.MessageConfiguration;
import udmi.schema.State;

public class StateHandler extends ComponentBase {

  private final List<HandlerSpecification> MESSAGE_HANDLERS = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::exceptionHandler),
      messageHandlerFor(State.class, this::messageHandler)
  );

  private final MessagePipe pipe;

  public StateHandler(MessagePipe pipe) {
    this.pipe = pipe;
  }

  private void exceptionHandler(Exception e) {
    info("Received processing exception: " + Common.getExceptionMessage(e));
  }

  private void defaultHandler(Object unknown) {
    Envelope envelope = pipe.getEnvelopeFor(unknown);
    info("Received unknown message type: " + envelope.subType);
  }

  public void messageHandler(State message) {
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
