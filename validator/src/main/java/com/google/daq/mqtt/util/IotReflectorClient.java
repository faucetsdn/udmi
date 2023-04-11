package com.google.daq.mqtt.util;

import static com.google.daq.mqtt.validator.Validator.EMPTY_MESSAGE;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.Boolean.TRUE;

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

public class IotReflectorClient implements IotProvider {

  // Requires functions that support cloud device manager support.
  private static final int REQUIRED_FUNCTION_VER = 7;

  public static final String CLOUD_QUERY_TOPIC = "cloud/query";
  public static final String CLOUD_MODEL_TOPIC = "cloud/model";
  private static final String UPDATE_CONFIG_TOPIC = "update/config";
  private final com.google.bos.iot.core.proxy.IotReflectorClient messageClient;

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
    Map<String, Object> message = transaction(deviceId, CLOUD_MODEL_TOPIC, stringify(device));
    CloudModel cloudModel = convertToStrict(CloudModel.class, message);
    if (cloudModel == null || cloudModel.num_id == null) {
      throw new RuntimeException("Invalid return receipt for update device " + deviceId);
    }
  }

  @Override
  public void createDevice(String deviceId, CloudModel makeDevice) {
    makeDevice.operation = Operation.CREATE;
    Map<String, Object> message = transaction(deviceId, CLOUD_MODEL_TOPIC, stringify(makeDevice));
    CloudModel cloudModel = convertToStrict(CloudModel.class, message);
    if (cloudModel == null || cloudModel.num_id == null) {
      throw new RuntimeException("Invalid return receipt for create device " + deviceId);
    }
  }

  @Override
  public CloudModel fetchDevice(String deviceId) {
    return fetchCloudModel(deviceId);
  }

  @Override
  public void bindDeviceToGateway(String proxyId, String gatewayId) {
    CloudModel device = new CloudModel();
    device.operation = Operation.BIND;
    device.device_ids = new HashMap<>();
    device.device_ids.put(proxyId, new CloudModel());
    Map<String, Object> message = transaction(gatewayId, CLOUD_MODEL_TOPIC, stringify(device));
    CloudModel cloudModel = convertToStrict(CloudModel.class, message);
    if (cloudModel == null || !TRUE.equals(cloudModel.is_gateway)) {
      throw new RuntimeException("Invalid return receipt for bind device " + proxyId);
    }
  }

  @Override
  public Set<String> fetchDeviceIds() {
    return fetchCloudModel(null).device_ids.keySet();
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
      MessageBundle messageBundle = messageClient.takeNextMessage();
      if (messageBundle == null) {
        System.err.println("Timeout waiting for reply to " + sentId);
        return null;
      }
      String transactionId = messageBundle.attributes.get("transactionId");
      if (sentId.equals(transactionId)) {
        String error = (String) messageBundle.message.get("error");
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
}
