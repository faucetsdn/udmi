package com.google.bos.udmi.service.access;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.udmi.util.Common;
import java.util.Map.Entry;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import udmi.schema.Envelope;
import udmi.schema.IotAccess;

class DynamicIotAccessProviderTest {

  private static final String TEST_REGISTRY = "test-reg";
  private static final String TEST_DEVICE = "test-dev";
  private static final String PROVIDER_ONE = "implicit";
  private static final String PROVIDER_TWO = "mock";

  private DynamicIotAccessProvider provider;
  private IotAccessProvider mockProviderOne;
  private IotAccessProvider mockProviderTwo;

  @BeforeEach
  void setUp() {
    UdmiServicePod.resetForTest();
    mockProviderOne = mock(IotAccessProvider.class);
    mockProviderTwo = mock(IotAccessProvider.class);

    when(mockProviderOne.isEnabled()).thenReturn(true);
    when(mockProviderTwo.isEnabled()).thenReturn(true);

    UdmiServicePod.putComponent(PROVIDER_ONE, () -> mockProviderOne);
    UdmiServicePod.putComponent(PROVIDER_TWO, () -> mockProviderTwo);

    IotAccess iotAccess = new IotAccess();
    iotAccess.project_id = PROVIDER_ONE + ", " + PROVIDER_TWO;
    provider = new DynamicIotAccessProvider(iotAccess);
    provider.activate();
    clearInvocations(mockProviderOne, mockProviderTwo);
  }

  @AfterEach
  void tearDown() {
    if (provider != null) {
      provider.shutdown();
    }
    UdmiServicePod.resetForTest();
  }

  @Test
  void explicitAffinityResolution() {
    provider.setProviderAffinity(TEST_REGISTRY, null, PROVIDER_TWO);
    provider.fetchConfig(TEST_REGISTRY, TEST_DEVICE);

    verify(mockProviderTwo).fetchConfig(TEST_REGISTRY, TEST_DEVICE);
    verifyNoInteractions(mockProviderOne);
  }

  @Test
  void invalidProviderAffinityRejection() {
    assertThrows(
        IllegalArgumentException.class,
        () -> provider.setProviderAffinity(TEST_REGISTRY, null, "invalid_provider"));
  }

  @Test
  void reflectorEnvelopeSourceRouting() {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = ContainerBase.REFLECT_BASE;
    envelope.deviceId = TEST_REGISTRY;
    envelope.source = "mqtt" + Common.SOURCE_SEPARATOR + PROVIDER_TWO;

    @SuppressWarnings("unchecked")
    Function<Entry<Long, String>, String> munger = mock(Function.class);
    provider.modifyConfig(envelope, munger);

    verify(mockProviderTwo).modifyConfig(eq(envelope), eq(munger));
    verifyNoInteractions(mockProviderOne);
  }

  @Test
  void reflectorEnvelopeBridgeSourceRouting() {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = ContainerBase.REFLECT_BASE;
    envelope.deviceId = TEST_REGISTRY;
    envelope.source = PROVIDER_TWO + Common.SOURCE_SEPARATOR + "bridge";

    @SuppressWarnings("unchecked")
    Function<Entry<Long, String>, String> munger = mock(Function.class);
    provider.modifyConfig(envelope, munger);

    verify(mockProviderOne).modifyConfig(eq(envelope), eq(munger));
    verifyNoInteractions(mockProviderTwo);
  }

  @Test
  void reflectorBridgeSourceRoutingWithoutImplicit() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.project_id = PROVIDER_TWO;
    DynamicIotAccessProvider noImplicitProvider = new DynamicIotAccessProvider(iotAccess);
    noImplicitProvider.activate();
    try {
      Envelope envelope = new Envelope();
      envelope.deviceRegistryId = ContainerBase.REFLECT_BASE;
      envelope.deviceId = TEST_REGISTRY;
      envelope.source = PROVIDER_TWO + Common.SOURCE_SEPARATOR + "bridge";

      @SuppressWarnings("unchecked")
      Function<Entry<Long, String>, String> munger = mock(Function.class);
      noImplicitProvider.modifyConfig(envelope, munger);

      verify(mockProviderTwo).modifyConfig(eq(envelope), eq(munger));
      verifyNoInteractions(mockProviderOne);
    } finally {
      noImplicitProvider.shutdown();
    }
  }

  @Test
  void reflectorAffinitySeparation() {
    provider.setProviderAffinity(ContainerBase.REFLECT_BASE, TEST_REGISTRY, PROVIDER_TWO);
    provider.fetchConfig(TEST_REGISTRY, TEST_DEVICE);

    verify(mockProviderOne).fetchConfig(TEST_REGISTRY, TEST_DEVICE);
    verify(mockProviderTwo, never()).fetchConfig(anyString(), anyString());
  }

  @Test
  void deviceOperationsIgnoreReflectorPubSubAffinity() {
    PubSubIotAccessProvider mockPubSub = mock(PubSubIotAccessProvider.class);
    when(mockPubSub.isEnabled()).thenReturn(true);
    String pubSubProviderName = "pubsub";
    UdmiServicePod.putComponent(pubSubProviderName, () -> mockPubSub);
    IotAccess iotAccess = new IotAccess();
    iotAccess.project_id = pubSubProviderName + ", " + PROVIDER_ONE;
    DynamicIotAccessProvider testProvider = new DynamicIotAccessProvider(iotAccess);
    testProvider.activate();
    try {
      testProvider.setProviderAffinity(
          ContainerBase.REFLECT_BASE, TEST_REGISTRY, pubSubProviderName);
      testProvider.fetchConfig(TEST_REGISTRY, TEST_DEVICE);
      verify(mockProviderOne).fetchConfig(TEST_REGISTRY, TEST_DEVICE);
      verify(mockPubSub, never()).fetchConfig(anyString(), anyString());
    } finally {
      testProvider.shutdown();
    }
  }

  @Test
  void registryAffinityClearsStaleDeviceCache() {
    // First query caches TEST_REGISTRY/TEST_DEVICE -> implicit
    provider.fetchConfig(TEST_REGISTRY, TEST_DEVICE);
    verify(mockProviderOne).fetchConfig(TEST_REGISTRY, TEST_DEVICE);

    // Now update registry-wide affinity to PROVIDER_TWO (mock)
    provider.setProviderAffinity(TEST_REGISTRY, null, PROVIDER_TWO);

    // Subsequent device query should pick up PROVIDER_TWO because device cache was cleared
    provider.fetchConfig(TEST_REGISTRY, TEST_DEVICE);
    verify(mockProviderTwo).fetchConfig(TEST_REGISTRY, TEST_DEVICE);
  }

  @Test
  void missingImplicitProviderRejection() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.project_id = PROVIDER_TWO; // implicit is NOT enabled
    DynamicIotAccessProvider noImplicitProvider = new DynamicIotAccessProvider(iotAccess);
    noImplicitProvider.activate();
    try {
      assertThrows(
          IllegalArgumentException.class,
          () -> noImplicitProvider.setProviderAffinity(TEST_REGISTRY, null, "implicit"));
    } finally {
      noImplicitProvider.shutdown();
    }
  }
}
