package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.MessagePipe;
import udmi.schema.EndpointConfiguration;

/**
 * Component that moves messages between two separate pipes.
 */
public class BridgeProcessor extends UdmisComponent {

  final MessagePipe pipeA;
  final MessagePipe pipeB;

  /**
   * New instance for two message configurations.
   */
  public BridgeProcessor(EndpointConfiguration from, EndpointConfiguration to) {
    this.pipeA = MessagePipe.from(from);
    this.pipeB = MessagePipe.from(to);

    pipeA.activate(pipeB::publish);
    pipeB.activate(pipeA::publish);
  }

  public void shutdown() {
    pipeA.shutdown();
    pipeB.shutdown();
  }
}
