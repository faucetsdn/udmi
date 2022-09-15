package com.google.daq.mqtt.util;

import com.google.api.services.cloudiot.v1.model.Device;
import java.util.List;
import java.util.Set;

/**
 * Abstraction for a cloud-based IoT provider. Provides methods for all the different operations
 * that UDMI tools need to do on the target registry. Nominally for GCP IoT Core, but can also be
 * mocked or backed by different providers.
 */
public interface IotProvider {

  /**
   * Update the device config with the supplied block (usually JSON).
   *
   * @param deviceId device to update
   * @param config   config data block
   */
  void updateConfig(String deviceId, String config);

  /**
   * Set the blocked (not receiving) status for a given device.
   *
   * @param deviceId device to (un)block
   * @param blocked  block or not
   */
  void setBlocked(String deviceId, boolean blocked);

  /**
   * Update a device entry with { blocked, credentials, metadata } fields from the provided Device.
   *
   * @param deviceId device to update
   * @param device   data to update with
   */
  void updateDevice(String deviceId, Device device);

  /**
   * Create a new device entry.
   *
   * @param makeDevice device specification to create
   */
  void createDevice(Device makeDevice);

  /**
   * Fetch a Device object for the given device.
   *
   * @param deviceId device id to fetch
   * @return Device object
   */
  Device fetchDevice(String deviceId);

  /**
   * Make the given proxy device bound to the given gateway.
   *
   * @param proxyDeviceId   device to bind
   * @param gatewayDeviceId thing to bind to
   */
  void bindDeviceToGateway(String proxyDeviceId, String gatewayDeviceId);

  /**
   * Return all the device ids currently registered.
   *
   * @return set of registered device ids
   */
  Set<String> fetchDeviceIds();

  /**
   * Get the device config blob for the indicated device.
   *
   * @param deviceId device to query
   * @return config blob
   */
  String getDeviceConfig(String deviceId);

  /**
   * Get a list of mocked device objects. Used for unit testing only with mocked implementation.
   *
   * @return list of mocked objects
   */
  List<Object> getMockActions();
}
