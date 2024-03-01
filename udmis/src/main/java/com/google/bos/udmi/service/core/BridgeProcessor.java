package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.MessagePipe;
import udmi.schema.EndpointConfiguration;

/**
 * Component that moves messages between two separate pipes.
 */
public class BridgeProcessor extends ProcessorBase {

  final MessagePipe pipeA;
  final MessagePipe pipeB;

  public BridgeProcessor(EndpointConfiguration config) {
    super(config);
    throw new IllegalStateException("Not supported for bridge processor");
  }

  /**
   * New instance for two message configurations.
   */
  public BridgeProcessor(EndpointConfiguration from, EndpointConfiguration to) {
    // TODO: This probably doesn't need ProcessorBase, or pass in empty configuration.
    super(from);
    this.pipeA = MessagePipe.from(from);
    this.pipeB = MessagePipe.from(to);
  }

  @Override
  public void activate() {
    pipeA.activate(pipeB::publish);
    pipeB.activate(pipeA::publish);
  }

  @Override
  public void shutdown() {
    pipeA.shutdown();
    pipeB.shutdown();
  }
}
