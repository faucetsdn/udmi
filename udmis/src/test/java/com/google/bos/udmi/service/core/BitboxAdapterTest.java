package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.JsonUtil;
import java.util.Map;
import org.junit.jupiter.api.Test;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Envelope;

/**
 * Test the basic bitbox legacy discovery adapter.
 */
public class BitboxAdapterTest extends ProcessorTestBase {

  private static final String BITBOX_DISCOVERY_JSON = "src/test/messages/bitbox_discovery.json";
  private final Map<String, Object> bitboxDiscovery = JsonUtil.loadMap(BITBOX_DISCOVERY_JSON);
  private final Map<String, Object> otherMessage = ImmutableMap.of(
      "timestamp", isoConvert(),
      "protocol", "fuzzy");

  protected void initializeTestInstance() {
    initializeTestInstance(BitboxAdapter.class);
    MappingAgentTest.initializeProvider(provider);
  }

  private Envelope getLegacyEnvelope() {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = TEST_REGISTRY;
    envelope.deviceId = TEST_GATEWAY;
    envelope.rawFolder = "discover"; // NB: not 'discovery' (ending in 'y')
    return envelope;
  }

  @Test
  public void bitboxDiscoveryReformat() {
    initializeTestInstance();

    dispatcher.receiveMessage(getLegacyEnvelope(), otherMessage);
    dispatcher.receiveMessage(getLegacyEnvelope(), bitboxDiscovery);
    terminateAndWait();

    assertEquals(1, captured.size(), "expected only one captured event");
    DiscoveryEvent discoveryEvent = (DiscoveryEvent) captured.get(0);
    assertEquals(ProtocolFamily.BACNET, discoveryEvent.scan_family, "scan_family");
  }
}