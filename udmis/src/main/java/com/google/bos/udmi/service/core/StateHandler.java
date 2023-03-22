package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.messaging.MessagePipe.HandlerSpecification;
import com.google.bos.udmi.service.pod.ComponentBase;
import java.util.function.Consumer;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubType;
import udmi.schema.MessageConfiguration;

public class StateHandler extends ComponentBase {

  private final MessagePipe pipe;

  public StateHandler(MessagePipe pipe) {
    this.pipe = pipe;
    pipe.registerHandler(new HandlerSpecification(Object.class, this::messageHandler));
  }

  public void messageHandler(Envelope envelope, Object message) {
    envelope.subType = SubType.STATE;
    pipe.publish(envelope, message);
  }

  public static StateHandler forConfig(MessageConfiguration configuration) {
    return new StateHandler(MessagePipe.from(configuration));
  }

  public void activate() {
    pipe.activate();
  }
}
