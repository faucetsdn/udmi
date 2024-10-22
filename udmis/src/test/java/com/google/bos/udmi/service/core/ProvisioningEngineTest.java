package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.MetadataMapKeys.UDMI_PROVISION_ENABLE;
import static com.google.udmi.util.MetadataMapKeys.UDMI_PROVISION_GENERATION;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.JsonUtil;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.lib.ProtocolFamily;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.CloudModel.Resource_type;
import udmi.schema.DiscoveryEvents;
import udmi.schema.Envelope;

/**
 * Simple tests for the auto-mapping provisioning agent.
 */
public class ProvisioningEngineTest extends ProcessorTestBase {

  private static final String SCAN_ADDR = "19273821";
  private static final String SCAN_FAMILY = ProtocolFamily.VENDOR;
  private static final String TARGET_DEVICE = format("%s-%s", SCAN_FAMILY, SCAN_ADDR);
  private static final String DISCOVERED_DEVICE = "discovered_" + TARGET_DEVICE;
  private static final Date SCAN_GENERATION = new Date();

  private static Map<String, String> getGatewayMetadata() {
    return ImmutableMap.of(
        UDMI_PROVISION_ENABLE, "true",
        UDMI_PROVISION_GENERATION, JsonUtil.isoConvert());
  }

  @NotNull
  private static Envelope getScanEnvelope() {
    Envelope envelope = new Envelope();
    envelope.deviceId = TEST_GATEWAY;
    envelope.deviceRegistryId = TEST_REGISTRY;
    return envelope;
  }

  static void initializeProvider(IotAccessBase provider, boolean alreadyProvisioned) {
    CloudModel registryModel = new CloudModel();
    registryModel.device_ids = new HashMap<>();

    CloudModel deviceModel = new CloudModel();
    deviceModel.resource_type = Resource_type.DEVICE;
    registryModel.device_ids.put(TEST_DEVICE, deviceModel);

    if (alreadyProvisioned) {
      CloudModel provisionedModel = new CloudModel();
      provisionedModel.resource_type = Resource_type.DEVICE;
      registryModel.device_ids.put(DISCOVERED_DEVICE, provisionedModel);
    }

    CloudModel gatewayModel = new CloudModel();
    registryModel.device_ids.put(TEST_GATEWAY, gatewayModel);
    gatewayModel.resource_type = Resource_type.GATEWAY;
    gatewayModel.device_ids = new HashMap<>();
    gatewayModel.device_ids.put(TEST_DEVICE, new CloudModel());
    ifTrueThen(alreadyProvisioned, () ->
        gatewayModel.device_ids.put(DISCOVERED_DEVICE, new CloudModel()));
    gatewayModel.metadata = getGatewayMetadata();

    when(provider.getRegistries()).thenReturn(ImmutableSet.of(TEST_REGISTRY));

    when(provider.listDevices(eq(TEST_REGISTRY), isNull())).thenReturn(registryModel);

    when(provider.fetchDevice(eq(TEST_REGISTRY), any())).thenAnswer(query -> {
      String deviceId = query.getArgument(1);
      throw new RuntimeException("No such device " + deviceId);
    });
    when(provider.fetchDevice(eq(TEST_REGISTRY), eq(TEST_DEVICE))).thenReturn(
        deviceModel);
    when(provider.fetchDevice(eq(TEST_REGISTRY), eq(TEST_GATEWAY))).thenReturn(
        gatewayModel);
  }

  protected void initializeTestInstance(boolean alreadyProvisioned) {
    initializeTestInstance(ProvisioningEngine.class);

    initializeProvider(provider, alreadyProvisioned);
  }

  private DiscoveryEvents getDiscoveryScanEvent(String targetDeviceId) {
    String[] split = targetDeviceId.split("-");
    DiscoveryEvents discoveryEvent = new DiscoveryEvents();
    discoveryEvent.scan_family = split[0];
    discoveryEvent.scan_addr = split[1];
    discoveryEvent.generation = SCAN_GENERATION;
    return discoveryEvent;
  }

  @Test
  public void discoveryEventCreate() {
    initializeTestInstance(false);
    getReverseDispatcher()
        .withEnvelope(getScanEnvelope())
        .publish(getDiscoveryScanEvent(TARGET_DEVICE));
    terminateAndWait();

    verify(provider, times(1)).fetchDevice(eq(TEST_REGISTRY), eq(TEST_GATEWAY));

    ArgumentCaptor<String> deviceCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CloudModel> modelCaptor = ArgumentCaptor.forClass(CloudModel.class);
    verify(provider, times(2)).modelDevice(eq(TEST_REGISTRY), deviceCaptor.capture(),
        modelCaptor.capture());
    List<String> devices = deviceCaptor.getAllValues();
    List<CloudModel> models = modelCaptor.getAllValues();

    assertEquals(DISCOVERED_DEVICE, devices.get(0), "created device id");
    assertEquals(Operation.CREATE, models.get(0).operation, "operation mismatch");
    assertTrue(models.get(0).blocked, "device blocked");

    assertEquals(TEST_GATEWAY, devices.get(1), "scanning gateway id");
    assertEquals(Operation.BIND, models.get(1).operation, "operation mismatch");
    assertNotNull(models.get(1).device_ids.get(DISCOVERED_DEVICE), "binding device entry");
  }

  @Test
  public void discoveryEventExisting() {
    initializeTestInstance(true);
    getReverseDispatcher()
        .withEnvelope(getScanEnvelope())
        .publish(getDiscoveryScanEvent(TARGET_DEVICE));

    terminateAndWait();
    verify(provider, times(1)).fetchDevice(eq(TEST_REGISTRY), eq(TEST_GATEWAY));
    verify(provider, never()).modelDevice(eq(TEST_REGISTRY), any(), any());
  }
}