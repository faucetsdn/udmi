package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.udmi.util.JsonUtil;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryEvent;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.PointDiscovery;

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

    ifNotNullThen(convertDiscovery(defaultedMessage), continuation::publish);
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
      discoveryEvent.points = extractPoints(map.get("data"));
      return discoveryEvent;
    } catch (Exception e) {
      error("While converting legacy message to DiscoveryEvent: " + friendlyStackTrace(e));
      return null;
    }
  }

  private Map<String, PointDiscovery> extractPoints(Object data) {
    Map<String, Object> map = JsonUtil.asMap(data);
    return map.entrySet().stream().map(this::pointMapper)
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  /**
   * Create a fake generation for bucketing discovery results.
   */
  private Date fabricateGeneration() {
    long seconds = Instant.now().getEpochSecond();
    return Date.from(Instant.ofEpochSecond(seconds - seconds % FAKE_GENERATION_SEC));
  }

  private Entry<String, PointDiscovery> pointMapper(Entry<String, Object> rawInput) {
    String ref = rawInput.getKey();
    Map<String, String> pointMap = JsonUtil.toStringMap(rawInput.getValue());
    String pointName = pointMap.get("object-name");
    PointDiscovery pointDiscovery = new PointDiscovery();
    pointDiscovery.ref = ref;
    pointDiscovery.ancillary = JsonUtil.toMap(rawInput.getValue());
    return Map.entry(pointName, pointDiscovery);
  }
}
