package com.google.bos.udmi.service.access;

import com.clearblade.cloud.iot.v1.binddevicetogateway.BindDeviceToGatewayRequest;
import com.clearblade.cloud.iot.v1.binddevicetogateway.BindDeviceToGatewayResponse;
import com.clearblade.cloud.iot.v1.createdevice.CreateDeviceRequest;
import com.clearblade.cloud.iot.v1.createdeviceregistry.CreateDeviceRegistryRequest;
import com.clearblade.cloud.iot.v1.deletedevice.DeleteDeviceRequest;
import com.clearblade.cloud.iot.v1.deletedeviceregistry.DeleteDeviceRegistryRequest;
import com.clearblade.cloud.iot.v1.deviceslist.DevicesListRequest;
import com.clearblade.cloud.iot.v1.deviceslist.DevicesListResponse;
import com.clearblade.cloud.iot.v1.devicestateslist.ListDeviceStatesRequest;
import com.clearblade.cloud.iot.v1.devicestateslist.ListDeviceStatesResponse;
import com.clearblade.cloud.iot.v1.devicetypes.Device;
import com.clearblade.cloud.iot.v1.devicetypes.DeviceConfig;
import com.clearblade.cloud.iot.v1.getdevice.GetDeviceRequest;
import com.clearblade.cloud.iot.v1.getdeviceregistry.GetDeviceRegistryRequest;
import com.clearblade.cloud.iot.v1.listdeviceconfigversions.ListDeviceConfigVersionsRequest;
import com.clearblade.cloud.iot.v1.listdeviceconfigversions.ListDeviceConfigVersionsResponse;
import com.clearblade.cloud.iot.v1.listdeviceregistries.ListDeviceRegistriesRequest;
import com.clearblade.cloud.iot.v1.listdeviceregistries.ListDeviceRegistriesResponse;
import com.clearblade.cloud.iot.v1.modifycloudtodeviceconfig.ModifyCloudToDeviceConfigRequest;
import com.clearblade.cloud.iot.v1.registrytypes.DeviceRegistry;
import com.clearblade.cloud.iot.v1.sendcommandtodevice.SendCommandToDeviceRequest;
import com.clearblade.cloud.iot.v1.sendcommandtodevice.SendCommandToDeviceResponse;
import com.clearblade.cloud.iot.v1.unbinddevicefromgateway.UnbindDeviceFromGatewayRequest;
import com.clearblade.cloud.iot.v1.unbinddevicefromgateway.UnbindDeviceFromGatewayResponse;
import com.clearblade.cloud.iot.v1.updatedevice.UpdateDeviceRequest;
import com.clearblade.cloud.iot.v1.updatedeviceregistry.UpdateDeviceRegistryRequest;

/**
 * Manager interface for device management actions.
 */
public interface DeviceManagerInterface {

  Device getDevice(GetDeviceRequest request);

  Device createDevice(CreateDeviceRequest request);

  BindDeviceToGatewayResponse bindDeviceToGateway(BindDeviceToGatewayRequest request);

  UnbindDeviceFromGatewayResponse unbindDeviceFromGateway(UnbindDeviceFromGatewayRequest request);

  void deleteDevice(DeleteDeviceRequest request);

  Device updateDevice(UpdateDeviceRequest request);

  SendCommandToDeviceResponse sendCommandToDevice(SendCommandToDeviceRequest request);

  DevicesListResponse listDevices(DevicesListRequest request);

  DeviceConfig modifyCloudToDeviceConfig(ModifyCloudToDeviceConfigRequest request);

  ListDeviceStatesResponse listDeviceStates(ListDeviceStatesRequest request);

  ListDeviceConfigVersionsResponse listDeviceConfigVersions(
      ListDeviceConfigVersionsRequest request);

  DeviceRegistry getDeviceRegistry(GetDeviceRegistryRequest request);

  DeviceRegistry createDeviceRegistry(CreateDeviceRegistryRequest request);

  DeviceRegistry updateDeviceRegistry(UpdateDeviceRegistryRequest request);

  void deleteDeviceRegistry(DeleteDeviceRegistryRequest request);

  ListDeviceRegistriesResponse listDeviceRegistries(ListDeviceRegistriesRequest request);
}
