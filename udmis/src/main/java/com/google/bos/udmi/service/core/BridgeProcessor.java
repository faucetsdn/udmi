package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.MessagePipe;
import udmi.schema.EndpointConfiguration;

public class BridgeProcessor extends UdmisComponent {

  private final MessagePipe pipeA;
  private final MessagePipe pipeB;

  public BridgeProcessor(EndpointConfiguration from, EndpointConfiguration to) {
    this.pipeA = MessagePipe.from(from);
    this.pipeB = MessagePipe.from(to);
  }
}
