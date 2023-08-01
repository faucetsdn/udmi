package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;

import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.UdmiState;

public class DistributorPipe extends ContainerBase {

  private final MessageDispatcherImpl dispatcher;
  private final String clientId = format("distributor-%08x", System.currentTimeMillis());
  private final ReflectProcessor reflectProcessor;

  public static ContainerBase from(EndpointConfiguration config) {
    return new DistributorPipe(config);
  }

  public DistributorPipe(EndpointConfiguration config) {
    dispatcher = new MessageDispatcherImpl(config);
    dispatcher.registerHandler(UdmiState.class, this::handleUdmiState);
    debug("Distributing to dispatcher %s as client %s", dispatcher, clientId);
    reflectProcessor = UdmiServicePod.getComponent(ReflectProcessor.class);
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

  private void handleUdmiState(UdmiState message) {
    Envelope envelope = dispatcher.getContinuation(message).getEnvelope();
    if (clientId.equals(envelope.gatewayId)) {
      return;
    }
    debug("Received distributed state for %s: %s", envelope.deviceId, stringify(message));
    reflectProcessor.updateAwareness(envelope, message);
  }

  public void distribute(Envelope envelope, UdmiState toolState) {
    debug("Distributing message from %s type %s", envelope.deviceRegistryId,
        toolState.getClass().getSimpleName());
    envelope.gatewayId = clientId;
    dispatcher.publish(dispatcher.makeMessageBundle(envelope, toolState));
  }
}
