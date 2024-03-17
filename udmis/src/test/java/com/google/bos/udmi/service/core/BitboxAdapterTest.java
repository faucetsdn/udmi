package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.isoConvert;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.JsonUtil;
import java.util.Map;
import org.junit.jupiter.api.Test;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Envelope;

public class BitboxAdapterTest extends ProcessorTestBase {

  private static final String BITBOX_DISCOVERY_JSON = "src/test/messages/bitbox_discovery.json";
  private final Map<String, Object> bitboxDiscovery = JsonUtil.loadMap(BITBOX_DISCOVERY_JSON);
  private final Map<String, Object> otherMessage = ImmutableMap.of(
      "timestamp", isoConvert(),
      "protocol", "fuzzy");

  protected void initializeTestInstance() {
    initializeTestInstance(BitboxAdapter.class);
  }

  private Envelope getLegacyEnvelope() {
    Envelope envelope = new Envelope();
    envelope.rawFolder = "discover"; // NB: not 'discovery' (ending in 'y')
    return envelope;
  }

  @Test
  public void bitboxDiscoveryReformat() {
    initializeTestInstance();
    getReverseDispatcher().receiveMessage(getLegacyEnvelope(), otherMessage);
    getReverseDispatcher().receiveMessage(getLegacyEnvelope(), bitboxDiscovery);
    getReverseDispatcher().waitForMessageProcessed(DiscoveryEvent.class);
    terminateAndWait();

    assertEquals(1, captured.size(), "expected only one captured event");
    DiscoveryEvent discoveryEvent = (DiscoveryEvent) captured.get(0);
    assertEquals(ProtocolFamily.BACNET, discoveryEvent.scan_family, "scan_family");
  }
}