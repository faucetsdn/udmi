package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.getDate;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.MetadataMapKeys;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import udmi.schema.CloudModel;
import udmi.schema.DiscoveryEvents;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.PointDiscovery;

/**
 * Adapter class for consuming raw bitbox (non-UDMI format) messages and rejiggering them to conform
 * to the normalized schema.
 */
@ComponentName("bitbox")
public class BitboxAdapter extends ProcessorBase {

  private static final Duration PROVISIONING_WINDOW = Duration.ofMinutes(10);

  public BitboxAdapter(EndpointConfiguration config) {
    super(config);
  }

  @Override
  protected void defaultHandler(Object defaultedMessage) {
    MessageContinuation continuation = getContinuation(defaultedMessage);

    Envelope envelope = continuation.getEnvelope();
    envelope.rawFolder = null; // Remove original 'discover' subFolder.

    String deviceRegistryId = envelope.deviceRegistryId;
    if (!iotAccess.getRegistries().contains(deviceRegistryId)) {
      warn("Registry %s not found, ignoring.", deviceRegistryId);
      return;
    }
    String deviceId = envelope.deviceId;
    Date generation = getProvisioningGeneration(deviceRegistryId, deviceId);
    if (generation == null) {
      warn("Generation for %s/%s not found, ignoring.", deviceRegistryId, deviceId);
      return;
    }
    Date publishTime = envelope.publishTime;
    Date endTime = Date.from(generation.toInstant().plus(PROVISIONING_WINDOW));
    if (publishTime.after(endTime)) {
      warn("Discovery for %s/%s at %s expired %ss after %s, ignoring.",
          isoConvert(publishTime), deviceRegistryId, deviceId,
          PROVISIONING_WINDOW.getSeconds(), isoConvert(generation));
      return;
    }
    ifNotNullThen(convertDiscovery(generation, defaultedMessage), continuation::publish);
  }

  private DiscoveryEvents convertDiscovery(Date generation, Object defaultedMessage) {
    Map<String, Object> map = JsonUtil.asMap(defaultedMessage);
    if (!"bitbox_bacnet".equals(map.get("type"))) {
      return null;
    }

    try {
      DiscoveryEvents discoveryEvent = new DiscoveryEvents();
      discoveryEvent.scan_family = (String) map.get("protocol");
      discoveryEvent.scan_addr = (String) map.get("id");
      discoveryEvent.generation = generation;
      discoveryEvent.points = extractPoints(map.get("data"));
      return discoveryEvent;
    } catch (Exception e) {
      error("While converting legacy message to DiscoveryEvents: " + friendlyStackTrace(e));
      e.printStackTrace();
      return null;
    }
  }

  private Map<String, PointDiscovery> extractPoints(Object data) {
    Map<String, Object> map = JsonUtil.asMap(data);
    return map.entrySet().stream().map(this::pointMapper)
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private Date getProvisioningGeneration(String registryId, String deviceId) {
    CloudModel cloudModel = iotAccess.fetchDevice(registryId, deviceId);
    return getDate(
        catchToNull(() -> cloudModel.metadata.get(MetadataMapKeys.UDMI_PROVISION_GENERATION)));
  }

  private Entry<String, PointDiscovery> pointMapper(Entry<String, Object> rawInput) {
    String ref = rawInput.getKey();
    Map<String, String> pointMap = JsonUtil.toStringMap(rawInput.getValue());
    String pointName = ofNullable(pointMap.get("object-name")).orElse(ref);
    PointDiscovery pointDiscovery = new PointDiscovery();
    pointDiscovery.ref = ref;
    pointDiscovery.ancillary = JsonUtil.toMap(rawInput.getValue());
    return Map.entry(pointName, pointDiscovery);
  }
}
