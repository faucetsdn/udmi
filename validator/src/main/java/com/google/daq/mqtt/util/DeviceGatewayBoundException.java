package com.google.daq.mqtt.util;

import udmi.schema.CloudModel;

/**
 * Simple marker exception for errors where devices bound to gateways causes exceptions when trying
 * to delete them (they need to be unbound first).
 */
public class DeviceGatewayBoundException extends RuntimeException {

  private final CloudModel cloudModel;

  public DeviceGatewayBoundException(CloudModel cloudModel) {
    this.cloudModel = cloudModel;
  }

  public CloudModel getCloudModel() {
    return cloudModel;
  }
}
