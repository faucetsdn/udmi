package com.google.daq.mqtt.util;

import static com.google.daq.mqtt.sequencer.SequenceBase.EMPTY_MESSAGE;
import static com.google.udmi.util.Common.ERROR_KEY;
import static com.google.udmi.util.Common.EXCEPTION_KEY;
import static com.google.udmi.util.Common.TRANSACTION_KEY;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static udmi.schema.CloudModel.Operation.BIND;

import com.google.common.base.Preconditions;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.SiteModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Operation;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.SetupUdmiConfig;

/**
 * IoT provider client that uses the MQTT reflector messaging interface.
 */
public class IotReflectorClient implements IotProvider {

  public static final String CLOUD_QUERY_TOPIC = "cloud/query";
  public static final String CLOUD_MODEL_TOPIC = "cloud/model";
  // Requires functions that support cloud device manager support.
  private static final int REQUIRED_FUNCTION_VER = 9;
  private static final String UPDATE_CONFIG_TOPIC = "update/config";
  private final com.google.bos.iot.core.proxy.IotReflectorClient messageClient;

  /**
   * Create a new client.
   *
   * @param executionConfiguration configuration to use for connection
   */
  public IotReflectorClient(ExecutionConfiguration executionConfiguration) {
    SiteModel siteModel = new SiteModel(executionConfiguration.site_model);
    executionConfiguration.key_file = siteModel.validatorKey();
    messageClient = new com.google.bos.iot.core.proxy.IotReflectorClient(executionConfiguration,
        REQUIRED_FUNCTION_VER);
  }

  @Override
  public void shutdown() {
    messageClient.close();
  }

  @Override
  public void updateConfig(String deviceId, String config) {
    transaction(deviceId, UPDATE_CONFIG_TOPIC, config);
  }

  @Override
  public void setBlocked(String deviceId, boolean blocked) {
    throw new IllegalStateException("Not yet implemented");
  }

  @Override
  public void updateDevice(String deviceId, CloudModel device) {
    device.operation = Operation.UPDATE;
    cloudModelTransaction(deviceId, CLOUD_MODEL_TOPIC, device);
  }

  @Override
  public void createDevice(String deviceId, CloudModel makeDevice) {
    makeDevice.operation = Operation.CREATE;
    CloudModel created = cloudModelTransaction(deviceId, CLOUD_MODEL_TOPIC, makeDevice);
    makeDevice.num_id = created.num_id;
  }

  @Override
  public void deleteDevice(String deviceId) {
    CloudModel deleteModel = new CloudModel();
    deleteModel.operation = Operation.DELETE;
    cloudModelTransaction(deviceId, CLOUD_MODEL_TOPIC, deleteModel);
  }

  private CloudModel cloudModelTransaction(String deviceId, String topic, CloudModel model) {
    Operation operation = Preconditions.checkNotNull(model.operation, "no operation");
    Map<String, Object> message = transaction(deviceId, topic, stringify(model));
    CloudModel cloudModel = convertToStrict(CloudModel.class, message);
    String cloudNumId = ifNotNullGet(cloudModel, result -> result.num_id);
    Operation cloudOperation = ifNotNullGet(cloudModel, result -> result.operation);
    if (cloudModel == null || cloudNumId == null || cloudOperation != operation) {
      throw new RuntimeException(
          format("Invalid return receipt: %s / %s", cloudOperation, cloudNumId));
    }
    return cloudModel;
  }

  @Override
  public CloudModel fetchDevice(String deviceId) {
    return fetchCloudModel(deviceId);
  }

  @Override
  public void bindDeviceToGateway(String proxyId, String gatewayId) {
    CloudModel device = new CloudModel();
    device.operation = BIND;
    device.device_ids = new HashMap<>();
    device.device_ids.put(proxyId, new CloudModel());
    cloudModelTransaction(gatewayId, CLOUD_MODEL_TOPIC, device);
  }

  @Override
  public Set<String> fetchDeviceIds(String forGatewayId) {
    return ofNullable(fetchCloudModel(forGatewayId))
        .map(model -> model.device_ids.keySet()).orElse(null);
  }

  @Nullable
  private CloudModel fetchCloudModel(String deviceId) {
    try {
      Map<String, Object> message = transaction(deviceId, CLOUD_QUERY_TOPIC, EMPTY_MESSAGE);
      return convertToStrict(CloudModel.class, message);
    } catch (Exception e) {
      if (e.getMessage().contains("NOT_FOUND")) {
        return null;
      }
      throw e;
    }
  }

  private synchronized Map<String, Object> transaction(String deviceId, String topic,
      String message) {
    return waitForReply(messageClient.publish(deviceId, topic, message));
  }

  private Map<String, Object> waitForReply(String sentId) {
    while (messageClient.isActive()) {
      MessageBundle messageBundle = messageClient.takeNextMessage(true);
      if (messageBundle == null) {
        throw new RuntimeException("UDMIS reflector transaction timeout " + sentId);
      }
      Exception exception = (Exception) messageBundle.message.get(EXCEPTION_KEY);
      if (exception != null) {
        exception.printStackTrace();
        throw new RuntimeException("UDMIS processing exception", exception);
      }
      String transactionId = messageBundle.attributes.get(TRANSACTION_KEY);
      if (sentId.equals(transactionId)) {
        String error = (String) messageBundle.message.get(ERROR_KEY);
        if (error != null) {
          throw new RuntimeException("UDMIS error: " + error);
        }
        return messageBundle.message;
      }
      System.err.println("Received unexpected reply message " + transactionId);
    }
    throw new RuntimeException("Unexpected termination of message loop");
  }

  @Override
  public String getDeviceConfig(String deviceId) {
    throw new IllegalStateException("Not yet implemented");
  }

  @Override
  public List<Object> getMockActions() {
    throw new IllegalStateException("Not yet implemented");
  }

  @Override
  public SetupUdmiConfig getVersionInformation() {
    return messageClient.getVersionInformation();
  }
}
