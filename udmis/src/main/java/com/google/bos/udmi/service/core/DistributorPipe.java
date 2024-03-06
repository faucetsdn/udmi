package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;

import com.google.bos.udmi.service.pod.SimpleHandler;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;

/**
 * Simple distributor that uses an underlying message pipe.
 */
public class DistributorPipe extends ProcessorBase {

  public static final String ROUTE_SEPERATOR = "/";
  private final String clientId = makeClientId();

  private static String makeClientId() {
    final String podId;
    // Quick heuristic to see if we're running in k8s or not...
    if (System.getenv("UDMIS_BROKER_SERVICE_PORT") != null) {
      String hostname = System.getenv("HOSTNAME");
      int index = hostname.lastIndexOf('-');
      podId = hostname.substring(index + 1);
    } else {
      podId = format("%05x", (long) (Math.random() * 0x100000));
    }
    return "distributor-" + podId;
  }

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
    debug("Handling distribution from " + stringifyTerse(envelope));
    try {
      String[] routeId = envelope.gatewayId.split(ROUTE_SEPERATOR, 2);
      if (clientId.equals(routeId[0])) {
        debug("Rejecting loopback client " + clientId);
        return;
      }
      Object component = UdmiServicePod.getComponent(routeId[1]);
      debug("Routing result to " + component.getClass().getSimpleName());
      if (component instanceof SimpleHandler handler) {
        handler.processMessage(envelope, message);
      } else {
        throw new RuntimeException("Unknown component class " + component.getClass().getName());
      }
    } catch (Exception e) {
      throw new RuntimeException("While handling distribution", e);
    }
  }

  /**
   * Distribute a message (broadcast).
   */
  public void publish(Envelope rawEnvelope, Object message, String source) {
    try {
      Envelope envelope = deepCopy(rawEnvelope);
      String routeId = getRouteId(source);
      debug("Distributing %s for %s/%s as %s", message.getClass().getSimpleName(),
          envelope.deviceRegistryId, envelope.deviceId, routeId);
      envelope.gatewayId = routeId;
      publish(envelope, message);
    } catch (Exception e) {
      throw new RuntimeException("Error distributing update", e);
    }
  }

  public String getRouteId(String source) {
    return format("%s%s%s", clientId, ROUTE_SEPERATOR, source);
  }
}
