package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.time.Duration.between;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.JsonUtil;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import udmi.lib.ProtocolFamily;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.DiscoveryEvents;
import udmi.schema.Envelope;
import udmi.schema.RefDiscovery;

/**
 * Test the basic bitbox legacy discovery adapter.
 */
public class BitboxAdapterTest extends ProcessorTestBase {

  private static final String BITBOX_DISCOVERY_JSON = "src/test/messages/bitbox_discovery.json";
  private static final String POINT_ID = "run_1";
  private static final String POINT_REF = "binary-value_4";
  private final Map<String, Object> bitboxDiscovery = JsonUtil.loadMap(BITBOX_DISCOVERY_JSON);
  private final Map<String, Object> otherMessage = ImmutableMap.of(
      "timestamp", isoConvert(),
      "protocol", "fuzzy");

  protected void initializeTestInstance() {
    initializeTestInstance(BitboxAdapter.class);
    initializeProvider(provider, false);
  }

  static void initializeProvider(IotAccessBase provider, boolean alreadyProvisioned) {
    CloudModel registryModel = new CloudModel();
    registryModel.device_ids = new HashMap<>();

    CloudModel deviceModel = new CloudModel();
    deviceModel.resource_type = Resource_type.DIRECT;
    registryModel.device_ids.put(TEST_DEVICE, deviceModel);

    if (alreadyProvisioned) {
      CloudModel provisionedModel = new CloudModel();
      provisionedModel.resource_type = Resource_type.DIRECT;
      registryModel.device_ids.put(TEST_GATEWAY, provisionedModel);
    }

    when(provider.getRegistries())
        .thenReturn(com.google.common.collect.ImmutableSet.of(TEST_REGISTRY));
    when(provider.listDevices(
        org.mockito.ArgumentMatchers.eq(TEST_REGISTRY),
        org.mockito.ArgumentMatchers.isNull()))
        .thenReturn(registryModel);
    when(provider.fetchDevice(
        org.mockito.ArgumentMatchers.eq(TEST_REGISTRY),
        org.mockito.ArgumentMatchers.eq(TEST_DEVICE)))
        .thenReturn(deviceModel);

    CloudModel gatewayModel = new CloudModel();
    gatewayModel.resource_type = Resource_type.GATEWAY;
    gatewayModel.metadata = new HashMap<>();
    gatewayModel.metadata.put(
        com.google.udmi.util.MetadataMapKeys.UDMI_PROVISION_GENERATION,
        isoConvert(new Date()));

    when(provider.fetchDevice(
        org.mockito.ArgumentMatchers.eq(TEST_REGISTRY),
        org.mockito.ArgumentMatchers.eq(TEST_GATEWAY)))
        .thenReturn(gatewayModel);
  }

  private Envelope getLegacyEnvelope() {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = TEST_REGISTRY;
    envelope.deviceId = TEST_GATEWAY;
    envelope.rawFolder = "discover"; // NB: not 'discovery' (ending in 'y')
    envelope.publishTime = new Date();
    return envelope;
  }

  @Test
  public void bitboxDiscoveryReformat() {
    initializeTestInstance();

    dispatcher.receiveMessage(getLegacyEnvelope(), otherMessage);
    dispatcher.receiveMessage(getLegacyEnvelope(), bitboxDiscovery);
    terminateAndWait();

    assertEquals(1, captured.size(), "expected only one captured event");
    DiscoveryEvents discoveryEvent = (DiscoveryEvents) captured.get(0);
    assertEquals(ProtocolFamily.BACNET, discoveryEvent.family, "family not bacnet");
    long deltaSec = abs(between(discoveryEvent.generation.toInstant(), Instant.now()).toSeconds());
    long deltaDays = deltaSec / 60 / 60 / 24;
    assertTrue(deltaDays < 14, format("generation too far off, was %s days", deltaDays));

    RefDiscovery mapped = discoveryEvent.refs.get(POINT_REF);
    assertEquals(POINT_ID, mapped.point, "first extracted point id");
  }
}