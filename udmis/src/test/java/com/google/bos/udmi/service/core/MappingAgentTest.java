package com.google.bos.udmi.service.core;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.DiscoveryEvent;

/**
 * Simple tests for the auto-mapping provisioning agent.
 */
public class MappingAgentTest extends ProcessorTestBase {

  private static final String SCAN_ADDR = "19273821";
  private static final ProtocolFamily SCAN_FAMILY = ProtocolFamily.VENDOR;
  private static final String TARGET_DEVICE = format("%s-%s", SCAN_FAMILY, SCAN_ADDR);

  protected void initializeTestInstance() {
    initializeTestInstance(MappingAgent.class);
    CloudModel registryModel = new CloudModel();
    registryModel.device_ids = new HashMap<>();
    CloudModel deviceModel = new CloudModel();
    registryModel.device_ids.put(TEST_DEVICE, deviceModel);
    when(provider.listDevices(eq(TEST_REGISTRY))).thenReturn(registryModel);
    when(provider.fetchDevice(eq(TEST_REGISTRY), eq(TEST_DEVICE))).thenReturn(deviceModel);
    when(provider.fetchDevice(eq(TEST_REGISTRY), not((eq(TEST_DEVICE))))).thenAnswer(query -> {
      String deviceId = query.getArgument(1);
      throw new RuntimeException("No such device " + deviceId);
    });
  }

  private DiscoveryEvent getDiscoveryScanEvent(String targetDeviceId) {
    String[] split = targetDeviceId.split("-");
    DiscoveryEvent discoveryEvent = new DiscoveryEvent();
    discoveryEvent.scan_family = ProtocolFamily.fromValue(split[0]);
    discoveryEvent.scan_addr = split[1];
    return discoveryEvent;
  }

  @Test
  public void discoveryEventCreate() {
    initializeTestInstance();
    getReverseDispatcher().publish(getDiscoveryScanEvent(TARGET_DEVICE));
    getReverseDispatcher().waitForMessageProcessed(DiscoveryEvent.class);
    terminateAndWait();
    ArgumentCaptor<String> deviceCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<CloudModel> modelCaptor = ArgumentCaptor.forClass(CloudModel.class);
    verify(provider, times(1)).modelResource(eq(TEST_REGISTRY), deviceCaptor.capture(),
        modelCaptor.capture());
    assertEquals(TARGET_DEVICE, deviceCaptor.getValue(), "created device id");
    CloudModel model = modelCaptor.getValue();
    assertEquals(Operation.CREATE, model.operation, "operation mismatch");
  }

  @Test
  public void discoveryEventExisting() {
    initializeTestInstance();
    getReverseDispatcher().publish(getDiscoveryScanEvent(TEST_DEVICE));
    getReverseDispatcher().waitForMessageProcessed(DiscoveryEvent.class);
    terminateAndWait();
    verify(provider, never()).modelResource(eq(TEST_REGISTRY), any(), any());
  }
}