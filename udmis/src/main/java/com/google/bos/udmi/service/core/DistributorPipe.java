package com.google.bos.udmi.service.core;

import static java.lang.String.format;

import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.UdmiState;

/**
 * Simple distributor that uses an underlying message pipe.
 */
public class DistributorPipe extends ContainerBase {

  private final MessageDispatcherImpl dispatcher;
  private final String clientId = format("distributor-%08x", System.currentTimeMillis());
  private final ReflectProcessor reflectProcessor;

  /**
   * Create a new distributor given the endpoint configuration.
   */
  public DistributorPipe(EndpointConfiguration config) {
    dispatcher = new MessageDispatcherImpl(config);
    dispatcher.registerHandler(UdmiState.class, this::handleUdmiState);
    debug("Distributing to dispatcher %s as client %s", dispatcher, clientId);
    reflectProcessor = UdmiServicePod.getComponent(ReflectProcessor.class);
  }

  public static ContainerBase from(EndpointConfiguration config) {
    return new DistributorPipe(config);
  }

  private void handleUdmiState(UdmiState message) {
    Envelope envelope = dispatcher.getContinuation(message).getEnvelope();
    if (clientId.equals(envelope.gatewayId)) {
      return;
    }
    debug("Received UdmiState from %s %s", envelope.deviceId, envelope.transactionId);
    reflectProcessor.updateAwareness(envelope, message);
  }

  @Override
  public void activate() {
    super.activate();
    dispatcher.activate();
  }

  @Override
  public void shutdown() {
    dispatcher.shutdown();
    super.shutdown();
  }

  /**
   * Distribute a message (broadcast).
   */
  public void distribute(Envelope envelope, UdmiState toolState) {
    debug("Distributing %s for %s %s", toolState.getClass().getSimpleName(),
        envelope.deviceId, envelope.transactionId);
    envelope.gatewayId = clientId;
    dispatcher.publish(dispatcher.makeMessageBundle(envelope, toolState));
  }

}
