package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.StateProcessor.IOT_ACCESS_COMPONENT;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope.SubFolder;

public class IotAccessDistributor extends DistributorBase {

  IotAccessBase iotAccessBase;

  public IotAccessDistributor(EndpointConfiguration config) {
  }

  @Override
  public void activate() {
    iotAccessBase = UdmiServicePod.getComponent(IOT_ACCESS_COMPONENT);
  }

  @Override
  public void shutdown() {
    iotAccessBase = null;
  }

  @Override
  void registryBackoffClear(String deviceRegistryId, String deviceId) {
    iotAccessBase.registryBackoffClear(deviceRegistryId, deviceId);
  }

  @Override
  void sendCommand(String registry, String device, SubFolder udmi, String stringify) {
    iotAccessBase.sendCommand(registry, device, udmi, stringify);
  }

  @Override
  void setProviderAffinity(String deviceRegistryId, String deviceId, String source) {
    iotAccessBase.setProviderAffinity(deviceRegistryId, deviceId, source);
  }
}
