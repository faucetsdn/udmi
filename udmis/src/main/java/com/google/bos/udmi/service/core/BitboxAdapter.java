package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import udmi.schema.DiscoveryEvent;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;

/**
 * Adapter class for consuming raw bitbox (non-UDMI format) messages and rejiggering them to conform
 * to the normalized schema.
 */
@ComponentName("bitbox")
public class BitboxAdapter extends ProcessorBase {

  public BitboxAdapter(EndpointConfiguration config) {
    super(config);
  }

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    MessageContinuation continuation = getContinuation(defaultedMessage);
    Envelope envelope = continuation.getEnvelope();
    envelope.rawFolder = "TAP";
    continuation.publish(convertDiscovery(defaultedMessage));
  }

  private DiscoveryEvent convertDiscovery(Object defaultedMessage) {
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    return discoveryEvent;
  }
}
