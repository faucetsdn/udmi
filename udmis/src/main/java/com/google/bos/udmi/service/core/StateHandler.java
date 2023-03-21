package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.messaging.MessagePipe.Bundle;
import com.google.bos.udmi.service.pod.ComponentBase;
import com.google.common.collect.ImmutableMap;
import udmi.schema.MessageConfiguration;

public class StateHandler extends ComponentBase {

  private final MessagePipe pipe;

  public StateHandler(MessagePipe pipe) {
    this.pipe = pipe;
    pipe.registerHandler(this::handleMessage, null, null);
  }

  private void handleMessage(Bundle message) {
    message.attributes = ImmutableMap.<String, String>builder().putAll(message.attributes)
        .put("subType", "state").build();
    pipe.publish(message);
  }

  public static StateHandler forConfig(MessageConfiguration configuration) {
    return new StateHandler(MessagePipe.from(configuration));
  }

  public void activate() {
    pipe.activate();
  }
}
