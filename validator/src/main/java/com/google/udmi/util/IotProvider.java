package com.google.udmi.util;

import java.util.List;
import java.util.Map;
import udmi.schema.CloudModel;
import udmi.schema.Credential;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.SetupUdmiConfig;

/**
 * Abstraction for a cloud-based IoT provider. Provides methods for all the different operations
 * that UDMI tools need to do on the target registry. Nominally for GCP IoT Core, but can also be
 * mocked or backed by different providers.
 */
public interface IotProvider {

  /**
   * Update the device config with the supplied block (usually JSON).
   */
  void updateConfig(String deviceId, SubFolder subFolder, String config);

  /**
   * Set the blocked (not receiving) status for a given device. This is a separate method without a
   * device parameter (like updateDevice) so it's more convenient to do on an "unknown" device where
   * nothing else about the device matters.
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
  void updateDevice(String deviceId, CloudModel device);

  /**
   * Updates current registry's metadata.
   *
   * @param registry registry to update
   */
  void updateRegistry(CloudModel registry);

  /**
   * Create a new device entry.
   *
   * @param deviceId   device id to create
   * @param makeDevice device specification to createCloudModel
   */
  void createResource(String deviceId, CloudModel makeDevice);

  /**
   * Delete the specified device from the iot registry.
   *
   * @param deviceId device id to delete
   */
  void deleteDevice(String deviceId);

  /**
   * Fetch a device for the given id.
   *
   * @param deviceId device id to fetch
   * @return fetched device
   */
  CloudModel fetchDevice(String deviceId);

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
  Map<String, CloudModel> fetchCloudModels(String forGatewayId);

  /**
   * Get the device config blob for the indicated device.
   *
   * @param deviceId device to query
   * @return config blob
   */
  String getDeviceConfig(String deviceId);

  /**
   * Shutdown the provider for a clean exit.
   */
  void shutdown();

  /**
   * Get a list of mocked device objects. Used for unit testing only with mocked implementation.
   *
   * @return list of mocked objects
   */
  List<Object> getMockActions();

  /**
   * Get the version information about this provider.
   */
  default SetupUdmiConfig getVersionInformation() {
    throw new IllegalStateException("getVersionInformation not implemented");
  }

  default Credential getCredential() {
    throw new IllegalStateException("getCredential not implemented");
  }
}
