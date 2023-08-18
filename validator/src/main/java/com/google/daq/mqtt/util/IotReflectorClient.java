package com.google.daq.mqtt.util;

import static com.google.daq.mqtt.sequencer.SequenceBase.EMPTY_MESSAGE;
import static com.google.udmi.util.Common.ERROR_KEY;
import static com.google.udmi.util.Common.EXCEPTION_KEY;
import static com.google.udmi.util.Common.TRANSACTION_KEY;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static udmi.schema.CloudModel.Operation.BIND;

import com.google.common.base.Preconditions;
import com.google.daq.mqtt.util.MessagePublisher.QuerySpeed;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.SiteModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
  private static final String REFLECTOR_PREFIX = "RC:";
  private final com.google.bos.iot.core.proxy.IotReflectorClient messageClient;
  private final Map<String, CompletableFuture<Map<String, Object>>> futures = new ConcurrentHashMap<>();
  private final Executor executor = Executors.newSingleThreadExecutor();

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
    executor.execute(this::processReplies);
  }

  @Override
  public void shutdown() {
    messageClient.close();
  }

  @Override
  public void updateConfig(String deviceId, String config) {
    transaction(deviceId, UPDATE_CONFIG_TOPIC, config, MessagePublisher.QuerySpeed.SHORT);
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
    Map<String, Object> message = transaction(deviceId, topic, stringify(model),
        MessagePublisher.QuerySpeed.SHORT);
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
      Map<String, Object> message = transaction(deviceId, CLOUD_QUERY_TOPIC, EMPTY_MESSAGE,
          MessagePublisher.QuerySpeed.LONG);
      return convertToStrict(CloudModel.class, message);
    } catch (Exception e) {
      if (e.getMessage().contains("NOT_FOUND")) {
        return null;
      }
      throw e;
    }
  }

  private Map<String, Object> transaction(String deviceId, String topic,
      String message, QuerySpeed speed) {
    return waitForReply(messageClient.publish(deviceId, topic, message), speed);
  }

  private Map<String, Object> waitForReply(String sentId, QuerySpeed speed) {
    try {
      CompletableFuture<Map<String, Object>> replyFuture = new CompletableFuture<>();
      futures.put(sentId, replyFuture);
      return replyFuture.get(speed.seconds(), TimeUnit.SECONDS);
    } catch (Exception e) {
      futures.remove(sentId);
      throw new RuntimeException(
          format("UDMIS reflector timeout %ss for %s", speed.seconds(), sentId));
    }
  }

  private void processReplies() {
    while (messageClient.isActive()) {
      try {
        MessageBundle messageBundle = messageClient.takeNextMessage(QuerySpeed.BLOCK);
        Exception exception = (Exception) messageBundle.message.get(EXCEPTION_KEY);
        if (exception != null) {
          exception.printStackTrace();
          throw new RuntimeException("UDMIS processing exception", exception);
        }

        String error = (String) messageBundle.message.get(ERROR_KEY);
        if (error != null) {
          throw new RuntimeException("UDMIS error: " + error);
        }

        String transactionId = messageBundle.attributes.get(TRANSACTION_KEY);
        CompletableFuture<Map<String, Object>> future = ifNotNullGet(transactionId,
            futures::remove);
        ifNotNullThen(future, f -> f.complete(messageBundle.message));
        if (future == null && transactionId.startsWith(REFLECTOR_PREFIX)) {
          throw new RuntimeException("Received unexpected reply message " + transactionId);
        }
      } catch (Exception e) {
        System.err.printf("Exception handling message: %s", friendlyStackTrace(e));
      }
    }
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
