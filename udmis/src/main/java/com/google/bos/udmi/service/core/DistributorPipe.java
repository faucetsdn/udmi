package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;

import com.google.bos.udmi.service.pod.UdmiServicePod;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;

/**
 * Simple distributor that uses an underlying message pipe.
 */
public class DistributorPipe extends ProcessorBase {

  private final String clientId = format("distributor-%08x", System.currentTimeMillis());

  /**
   * Create a new distributor given the endpoint configuration.
   */
  public DistributorPipe(EndpointConfiguration config) {
    super(config);
    debug("Distributing to dispatcher %s as client %s", dispatcher, clientId);
  }

  @Override
  protected void defaultHandler(Object message) {
    Envelope envelope = getContinuation(message).getEnvelope();
    try {
      String[] routeId = envelope.gatewayId.split("/", 2);
      if (clientId.equals(routeId[0])) {
        return;
      }
      Object component = UdmiServicePod.getComponent(routeId[1]);
      if (component instanceof ProcessorBase processorBase) {
        processorBase.processMessage(envelope, message);
      } else {
        throw new RuntimeException("Unknown component class " + component.getClass().getName());
      }
    } catch (Exception e) {
      throw new RuntimeException("While handling distributed update " + stringifyTerse(envelope));
    }
  }

  /**
   * Distribute a message (broadcast).
   */
  public void publish(Envelope rawEnvelope, Object message, String source) {
    try {
      Envelope envelope = deepCopy(rawEnvelope);
      String routeId = format("%s/%s", clientId, source);
      debug("Distributing %s for %s/%s as %s", message.getClass().getSimpleName(),
          envelope.deviceRegistryId, envelope.deviceId, routeId);
      envelope.gatewayId = routeId;
      super.publish(envelope, message);
    } catch (Exception e) {
      error("Error distributing update: " + friendlyStackTrace(e));
    }
  }

}
