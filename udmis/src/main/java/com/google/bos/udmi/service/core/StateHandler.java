package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.MessagePipe.messageHandlerFor;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.messaging.MessagePipe.HandlerSpecification;
import com.google.bos.udmi.service.pod.ComponentBase;
import com.google.common.collect.ImmutableList;
import java.util.List;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubType;
import udmi.schema.MessageConfiguration;

public class StateHandler extends ComponentBase {

  private final List<HandlerSpecification> MESSAGE_HANDLERS = ImmutableList.of(
      messageHandlerFor(Object.class, this::messageHandler)
  );

  private final MessagePipe pipe;

  public StateHandler(MessagePipe pipe) {
    this.pipe = pipe;
  }

  public void messageHandler(Envelope envelope, Object message) {
    envelope.subType = SubType.STATE;
    pipe.publish(envelope, message);
  }

  public static StateHandler forConfig(MessageConfiguration configuration) {
    return new StateHandler(MessagePipe.from(configuration));
  }

  public void activate() {
    pipe.registerHandlers(MESSAGE_HANDLERS);
    pipe.activate();
  }
}
