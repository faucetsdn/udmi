package com.google.bos.udmi.service.access;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    org.mockito.Mockito.clearInvocations(mockProviderOne, mockProviderTwo);
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
  void reflectorAffinityInheritance() {
    provider.setProviderAffinity(ContainerBase.REFLECT_BASE, TEST_REGISTRY, PROVIDER_TWO);
    provider.fetchConfig(TEST_REGISTRY, TEST_DEVICE);

    verify(mockProviderTwo).fetchConfig(TEST_REGISTRY, TEST_DEVICE);
    verifyNoInteractions(mockProviderOne);
  }
}
