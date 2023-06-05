package com.google.bos.udmi.service.access;

import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_DEVICE;
import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_NUMID;
import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_PROJECT;
import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_REGISTRY;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.cloudiot.v1.CloudIot.Projects;
import com.google.api.services.cloudiot.v1.CloudIot.Projects.Locations;
import com.google.api.services.cloudiot.v1.CloudIot.Projects.Locations.Registries;
import com.google.api.services.cloudiot.v1.CloudIot.Projects.Locations.Registries.Devices;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.DeviceRegistry;
import com.google.api.services.cloudiot.v1.model.ListDeviceRegistriesResponse;
import com.google.api.services.cloudiot.v1.model.ListDevicesResponse;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import udmi.schema.CloudModel;
import udmi.schema.IotAccess;

class GcpIotAccessProviderTest {

  private static final String US_CENTRAL_1 = "us-central1";
  private final CloudIot cloudIot = mock(CloudIot.class);
  private final Map<String, Registries.List> registryLists = new HashMap<>();

  private static IotAccess getConfig() {
    IotAccess iotAccess = new IotAccess();
    iotAccess.project_id = TEST_PROJECT;
    return iotAccess;
  }

  private static String pathForRegion(String region) {
    return format("projects/%s/locations/%s", TEST_PROJECT, region);
  }

  private ListDeviceRegistriesResponse responseFor(String key) {
    ListDeviceRegistriesResponse listDeviceRegistriesResponse = new ListDeviceRegistriesResponse();
    DeviceRegistry deviceRegistry = new DeviceRegistry();
    // Find one registry for each reason, and just happen to use TEST_REGISTRY for US_CENTRAL_1.
    String registryId = TEST_REGISTRY + (key.equals(US_CENTRAL_1) ? "" : key);
    deviceRegistry.setId(registryId);
    List<DeviceRegistry> deviceRegistries = ImmutableList.of(deviceRegistry);
    listDeviceRegistriesResponse.setDeviceRegistries(deviceRegistries);
    return listDeviceRegistriesResponse;
  }

  class MockedGcpIotAccessProvider extends GcpIotAccessProvider {

    public MockedGcpIotAccessProvider() {
      super(getConfig());
    }

    @Override
    protected @NotNull CloudIot createCloudIotService() {
      try {
        Devices devices = mock(Devices.class);
        Registries registries =
            when(mock(Registries.class).devices()).thenReturn(devices).getMock();

        when(registries.list(anyString())).thenAnswer(
            i -> registryLists.get((String) i.getArgument(0)));
        CLOUD_REGIONS
            .forEach(region -> registryLists.computeIfAbsent(pathForRegion(region), key -> {
              try {
                return when(mock(Registries.List.class).execute()).thenAnswer(
                        i -> responseFor(region))
                    .getMock();
              } catch (Exception e) {
                throw new RuntimeException("While processing mock " + region, e);
              }
            }));

        Locations locations =
            when(mock(Locations.class).registries()).thenReturn(registries).getMock();
        Projects projects = when(mock(Projects.class).locations()).thenReturn(locations).getMock();
        when(cloudIot.projects()).thenReturn(projects);

        DeviceRegistry deviceRegistry = new DeviceRegistry().setId(TEST_REGISTRY);
        ListDeviceRegistriesResponse registryListResponse = new ListDeviceRegistriesResponse();
        registryListResponse.setDeviceRegistries(ImmutableList.of(deviceRegistry));
        when(registryLists.get(pathForRegion(US_CENTRAL_1)).execute()).thenAnswer(
            i -> registryListResponse);
        return cloudIot;
      } catch (Exception e) {
        throw new RuntimeException("While creating mock iot service provider", e);
      }
    }
  }

  @Test
  void listDevices() throws IOException {
    IotAccessProvider provider = new MockedGcpIotAccessProvider();
    provider.activate();

    Devices devices = cloudIot.projects().locations().registries().devices();
    Devices.List mockList = mock(Devices.List.class);
    when(devices.list(anyString())).thenReturn(mockList);
    ListDevicesResponse response = new ListDevicesResponse();
    response.setDevices(
        ImmutableList.of(new Device().setId(TEST_DEVICE).setNumId(new BigInteger(TEST_NUMID))));
    when(mockList.execute()).thenReturn(response);

    CloudModel cloudModel = provider.listDevices(TEST_REGISTRY);
    Set<String> strings = cloudModel.device_ids.keySet();
    assertEquals(1, strings.size(), "expected number of devices");
    assertTrue(strings.contains(TEST_DEVICE), "result contains expected device");
  }
}
