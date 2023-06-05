package com.google.bos.udmi.service.access;

import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_DEVICE;
import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_PROJECT;
import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_REGION;
import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_REGISTRY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clearblade.cloud.iot.v1.DeviceManagerClient;
import com.clearblade.cloud.iot.v1.deviceslist.DevicesListRequest;
import com.clearblade.cloud.iot.v1.deviceslist.DevicesListResponse;
import com.clearblade.cloud.iot.v1.devicetypes.Device;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import udmi.schema.CloudModel;
import udmi.schema.IotAccess;

class ClearBladeIotAccessProviderTest {

  private final DeviceManagerClient mockClient = mock(DeviceManagerClient.class);

  @NotNull
  private ClearBladeIotAccessProvider getProvider() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.project_id = TEST_PROJECT;
    return new ClearBladeIotAccessProviderMock(iotAccess);
  }

  private class ClearBladeIotAccessProviderMock extends ClearBladeIotAccessProvider {

    public ClearBladeIotAccessProviderMock(IotAccess iotAccess) {
      super(iotAccess);
    }

    @Override
    protected Map<String, String> fetchRegistryCloudRegions() {
      return ImmutableMap.of(TEST_REGISTRY, TEST_REGION);
    }

    @Override
    protected DeviceManagerClient getDeviceManagerClient() {
      return mockClient;
    }
  }

  @Test
  void fetchDevice() {
    ClearBladeIotAccessProvider provider = getProvider();
    when(mockClient.listDevices(Mockito.any(DevicesListRequest.class))).thenAnswer(
        this::makeDevicesListResponse);
    CloudModel cloudModel = provider.listDevices(TEST_REGISTRY);
    assertEquals(1, cloudModel.device_ids.size(), "number of listed devices");
    assertTrue(cloudModel.device_ids.containsKey(TEST_DEVICE), "listed device name");
  }

  private DevicesListResponse makeDevicesListResponse(InvocationOnMock invocation) {
    String request = invocation.getArgument(0).toString();
    assertTrue(request.endsWith(TEST_REGISTRY));
    DevicesListResponse devicesListResponse = DevicesListResponse.Builder.newBuilder().build();
    Device device = Device.newBuilder()
        .setName(request + "/" + TEST_DEVICE)
        .setId(TEST_DEVICE)
        .build();
    devicesListResponse.setDevicesList(ImmutableList.of(device));
    return devicesListResponse;
  }
}