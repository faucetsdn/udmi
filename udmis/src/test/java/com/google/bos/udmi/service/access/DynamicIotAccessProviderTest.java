package com.google.bos.udmi.service.access;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.bos.udmi.service.messaging.impl.MessageTestCore;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import udmi.schema.Envelope;
import udmi.schema.IotAccess;

class DynamicIotAccessProviderTest extends MessageTestCore {

  private IotAccessProvider mockImplicitProvider;
  private IotAccessProvider mockPubSubProvider;

  @BeforeEach
  void setUp() {
    mockImplicitProvider = mock(IotAccessProvider.class);
    when(mockImplicitProvider.isEnabled()).thenReturn(true);
    when(mockImplicitProvider.fetchRegistryMetadata(TEST_REGISTRY, "udmi_provisioned"))
        .thenReturn("2026-01-01T00:00:00Z");

    mockPubSubProvider = mock(IotAccessProvider.class);
    when(mockPubSubProvider.isEnabled()).thenReturn(true);
    when(mockPubSubProvider.fetchRegistryMetadata(TEST_REGISTRY, "udmi_provisioned"))
        .thenReturn("2026-01-01T00:00:01Z");

    UdmiServicePod.putComponent("implicit", () -> mockImplicitProvider);
    UdmiServicePod.putComponent("pubsub", () -> mockPubSubProvider);
  }

  @AfterEach
  void tearDown() {
    UdmiServicePod.resetForTest();
  }

  @Test
  void testProviderAffinityWithInvalidSource() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.project_id = "implicit,pubsub";
    DynamicIotAccessProvider provider = new DynamicIotAccessProvider(iotAccess);
    provider.activate();

    // Pass "+debug" as providerId (omitted transport)
    provider.setProviderAffinity("UDMI-REFLECT", "UDMI-REFLECT", "+debug");

    // Modifying config for a device should not throw NPE due to empty provider key
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = TEST_REGISTRY;
    envelope.deviceId = TEST_DEVICE;

    assertDoesNotThrow(() -> provider.modifyConfig(envelope, pair -> "{}"));
  }

  @Test
  void testProviderAffinityWithValidSource() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.project_id = "implicit,pubsub";
    DynamicIotAccessProvider provider = new DynamicIotAccessProvider(iotAccess);
    provider.activate();

    // Pass "pubsub+debug" as providerId
    provider.setProviderAffinity("UDMI-REFLECT", "UDMI-REFLECT", "pubsub+debug");

    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = TEST_REGISTRY;
    envelope.deviceId = TEST_DEVICE;

    assertDoesNotThrow(() -> provider.modifyConfig(envelope, pair -> "{}"));
  }
}
