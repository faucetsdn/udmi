package com.google.daq.mqtt.util;

import udmi.schema.CloudModel;

public class DeviceGatewayBoundException extends RuntimeException {

  private final CloudModel cloudModel;

  public DeviceGatewayBoundException(CloudModel cloudModel) {
    this.cloudModel = cloudModel;
  }

  public CloudModel getCloudModel() {
    return cloudModel;
  }
}
