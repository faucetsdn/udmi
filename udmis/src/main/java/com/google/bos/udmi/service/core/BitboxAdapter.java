package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.udmi.util.JsonUtil;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalUnit;
import java.util.Date;
import java.util.Map;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryEvent;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;

/**
 * Adapter class for consuming raw bitbox (non-UDMI format) messages and rejiggering them to conform
 * to the normalized schema.
 */
@ComponentName("bitbox")
public class BitboxAdapter extends ProcessorBase {

  private static final String BACNET_PROTOCOL = "bacnet";
  private static final int FAKE_GENERATION_SEC = 10 * 60;

  public BitboxAdapter(EndpointConfiguration config) {
    super(config);
  }

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    MessageContinuation continuation = getContinuation(defaultedMessage);

    Envelope envelope = continuation.getEnvelope();
    envelope.rawFolder = null;

    Map<String, Object> stringObjectMap = JsonUtil.asMap(defaultedMessage);
    if (!BACNET_PROTOCOL.equals(stringObjectMap.get("protocol"))) {
      return;
    }

    if (!iotAccess.getRegistries().contains(envelope.deviceRegistryId)) {
      warn("Registry %s not found, ignoring.", envelope.deviceRegistryId);
      return;
    }

    continuation.publish(convertDiscovery(defaultedMessage));
  }

  private DiscoveryEvent convertDiscovery(Object defaultedMessage) {
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = ProtocolFamily.BACNET;
    discoveryEvent.generation = fabricateGeneration();
    return discoveryEvent;
  }

  /**
   * Create a fake generation for bucketing discovery results.
   */
  private Date fabricateGeneration() {
    long seconds = Instant.now().getEpochSecond();
    return new Date(seconds - seconds % FAKE_GENERATION_SEC);
  }
}
