package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;

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
  private static final int FAKE_GENERATION_SEC = 60 * 60;

  public BitboxAdapter(EndpointConfiguration config) {
    super(config);
  }

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    MessageContinuation continuation = getContinuation(defaultedMessage);

    Envelope envelope = continuation.getEnvelope();
    envelope.rawFolder = null;

    if (!iotAccess.getRegistries().contains(envelope.deviceRegistryId)) {
      warn("Registry for %s/%s not found, ignoring.", envelope.deviceRegistryId, envelope.deviceId);
      return;
    }

    continuation.publish(convertDiscovery(defaultedMessage));
  }

  private DiscoveryEvent convertDiscovery(Object defaultedMessage) {
    try {
      Map<String, Object> map = JsonUtil.asMap(defaultedMessage);
      if (!"bitbox_bacnet".equals(map.get("type"))) {
        return null;
      }

      DiscoveryEvent discoveryEvent = new DiscoveryEvent();
      discoveryEvent.scan_family = ProtocolFamily.fromValue((String) map.get("protocol"));
      discoveryEvent.scan_addr = (String) map.get("id");
      discoveryEvent.generation = fabricateGeneration();
      return discoveryEvent;
    } catch (Exception e) {
      error("While converting legacy message to DiscoveryEvent: " + friendlyStackTrace(e));
      return null;
    }
  }

  /**
   * Create a fake generation for bucketing discovery results.
   */
  private Date fabricateGeneration() {
    long seconds = Instant.now().getEpochSecond();
    return Date.from(Instant.ofEpochSecond(seconds - seconds % FAKE_GENERATION_SEC));
  }
}
