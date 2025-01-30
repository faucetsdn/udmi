package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.isoConvert;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.time.Duration.between;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.JsonUtil;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import udmi.lib.ProtocolFamily;
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
    ProvisioningEngineTest.initializeProvider(provider, false);
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
    assertEquals(ProtocolFamily.BACNET, discoveryEvent.family, "scan_family");
    long deltaSec = abs(between(discoveryEvent.generation.toInstant(), Instant.now()).toSeconds());
    long deltaDays = deltaSec / 60 / 60 / 24;
    assertTrue(deltaDays < 14, format("generation too far off, was %s days", deltaDays));

    RefDiscovery mapped = discoveryEvent.refs.get(POINT_REF);
    assertEquals(POINT_ID, mapped.point, "first extracted point id");
  }
}