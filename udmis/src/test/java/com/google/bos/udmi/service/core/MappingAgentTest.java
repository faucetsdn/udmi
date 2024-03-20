package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.MetadataMapKeys.UDMI_ONBOARD_UNTIL;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.JsonUtil;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryEvent;
import udmi.schema.Envelope;

/**
 * Simple tests for the auto-mapping provisioning agent.
 */
public class MappingAgentTest extends ProcessorTestBase {

  private static final String SCAN_ADDR = "19273821";
  private static final ProtocolFamily SCAN_FAMILY = ProtocolFamily.VENDOR;
  private static final String TARGET_DEVICE = format("%s-%s", SCAN_FAMILY, SCAN_ADDR);
  private static final Date SCAN_GENERATION = new Date();
  private static final Duration ONBOARDING_WINDOW = Duration.ofMinutes(5);

  protected void initializeTestInstance() {
    initializeTestInstance(MappingAgent.class);

    CloudModel registryModel = new CloudModel();
    registryModel.device_ids = new HashMap<>();

    CloudModel deviceModel = new CloudModel();
    registryModel.device_ids.put(TARGET_DEVICE, deviceModel);
    registryModel.device_ids.put(TEST_DEVICE, deviceModel);
    deviceModel.resource_type = Resource_type.DEVICE;

    CloudModel gatewayModel = new CloudModel();
    registryModel.device_ids.put(TEST_GATEWAY, gatewayModel);
    gatewayModel.resource_type = Resource_type.GATEWAY;
    gatewayModel.device_ids = new HashMap<>();
    gatewayModel.device_ids.put(TEST_DEVICE, new CloudModel());
    gatewayModel.metadata = getGatewayMetadata();

    when(provider.listDevices(eq(TEST_REGISTRY))).thenReturn(registryModel);

    when(provider.fetchDevice(eq(TEST_REGISTRY), any())).thenAnswer(query -> {
      String deviceId = query.getArgument(1);
      throw new RuntimeException("No such device " + deviceId);
    });
    when(provider.fetchDevice(eq(TEST_REGISTRY), eq(TEST_DEVICE))).thenReturn(deviceModel);
    when(provider.fetchDevice(eq(TEST_REGISTRY), eq(TEST_GATEWAY))).thenReturn(gatewayModel);
  }

  private Map<String, String> getGatewayMetadata() {
    return ImmutableMap.of(UDMI_ONBOARD_UNTIL, isoConvert(Instant.now().plus(ONBOARDING_WINDOW)));
  }

  private DiscoveryEvent getDiscoveryScanEvent(String targetDeviceId) {
    String[] split = targetDeviceId.split("-");
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = ProtocolFamily.fromValue(split[0]);
    discoveryEvent.scan_addr = split[1];
    discoveryEvent.generation = SCAN_GENERATION;
    return discoveryEvent;
  }

  @Test
  public void discoveryEventCreate() {
    initializeTestInstance();
    getReverseDispatcher()
        .withEnvelope(getScanEnvelope())
        .publish(getDiscoveryScanEvent(TARGET_DEVICE));
    terminateAndWait();

    verify(provider, times(1)).fetchDevice(eq(TEST_REGISTRY), eq(TEST_GATEWAY));

    ArgumentCaptor<String> deviceCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CloudModel> modelCaptor = ArgumentCaptor.forClass(CloudModel.class);
    verify(provider, times(2)).modelResource(eq(TEST_REGISTRY), deviceCaptor.capture(),
        modelCaptor.capture());
    List<String> devices = deviceCaptor.getAllValues();
    List<CloudModel> models = modelCaptor.getAllValues();

    assertEquals(TARGET_DEVICE, devices.get(0), "created device id");
    assertEquals(Operation.CREATE, models.get(0).operation, "operation mismatch");
    assertTrue(models.get(0).blocked, "device blocked");

    assertEquals(TEST_GATEWAY, devices.get(1), "scanning gateway id");
    assertEquals(Operation.BIND, models.get(1).operation, "operation mismatch");
    assertNotNull(models.get(1).device_ids.get(TARGET_DEVICE), "binding device entry");
  }

  @NotNull
  private static Envelope getScanEnvelope() {
    Envelope envelope = new Envelope();
    envelope.deviceId = TEST_GATEWAY;
    envelope.deviceRegistryId = TEST_REGISTRY;
    return envelope;
  }

  @Test
  public void discoveryEventExisting() {
    initializeTestInstance();
    getReverseDispatcher()
        .withEnvelope(getScanEnvelope())
        .publish(getDiscoveryScanEvent(TEST_DEVICE));
    terminateAndWait();
    verify(provider, times(1)).fetchDevice(eq(TEST_REGISTRY), eq(TEST_GATEWAY));
    verify(provider, never()).modelResource(eq(TEST_REGISTRY), any(), any());
  }
}