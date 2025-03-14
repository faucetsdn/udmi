package com.google.daq.mqtt.util;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.daq.mqtt.validator.Validator.TOOLS_FUNCTIONS_VERSION;
import static com.google.udmi.util.Common.CONDENSER_STRING;
import static com.google.udmi.util.Common.DETAIL_KEY;
import static com.google.udmi.util.Common.ERROR_KEY;
import static com.google.udmi.util.Common.EXCEPTION_KEY;
import static com.google.udmi.util.Common.TRANSACTION_KEY;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static udmi.schema.CloudModel.ModelOperation.BIND;
import static udmi.schema.CloudModel.ModelOperation.BLOCK;
import static udmi.schema.CloudModel.ModelOperation.BOUND;
import static udmi.schema.CloudModel.ModelOperation.DELETE;
import static udmi.schema.CloudModel.ModelOperation.READ;
import static udmi.schema.CloudModel.ModelOperation.UNBIND;

import com.google.common.base.Preconditions;
import com.google.daq.mqtt.util.MessagePublisher.QuerySpeed;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.IotProvider;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.ModelOperation;
import udmi.schema.Credential;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.GatewayModel;
import udmi.schema.SetupUdmiConfig;

/**
 * IoT provider client that uses the MQTT reflector messaging interface.
 */
public class IotReflectorClient implements IotProvider {

  public static final String CLOUD_QUERY_TOPIC = "cloud/query";
  public static final String CLOUD_MODEL_TOPIC = "cloud/model";
  // Requires functions that support cloud device manager support.
  private static final String CONFIG_TOPIC_FORMAT = "%s/config";
  private static final File ERROR_DIR = new File("out");
  public static final String UPDATE_PREFIX = "update/";
  private final com.google.bos.iot.core.proxy.IotReflectorClient messageClient;
  private final Map<String, CompletableFuture<Map<String, Object>>> futures =
      new ConcurrentHashMap<>();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final String sessionPrefix;

  /**
   * Create a new client.
   *
   * @param executionConfiguration configuration to use for connection
   * @param toolName               name of tool using this reflector
   */
  public IotReflectorClient(ExecutionConfiguration executionConfiguration, String toolName) {
    SiteModel siteModel = new SiteModel(executionConfiguration.site_model);
    executionConfiguration.key_file = siteModel.validatorKey();
    messageClient = new com.google.bos.iot.core.proxy.IotReflectorClient(executionConfiguration,
        TOOLS_FUNCTIONS_VERSION, toolName);
    messageClient.activate();
    sessionPrefix = messageClient.getSessionPrefix();
    executor.execute(this::processReplies);
  }

  @Override
  public Credential getCredential() {
    return messageClient.getCredential();
  }

  @Override
  public void shutdown() {
    messageClient.close();
    executor.shutdown();
  }

  @Override
  public void updateConfig(String deviceId, SubFolder subFolder, String config) {
    try {
      transaction(deviceId, format(CONFIG_TOPIC_FORMAT, subFolder.value()), config,
          QuerySpeed.LONG);
    } catch (Exception e) {
      System.err.println("Exception handling config update: " + friendlyStackTrace(e));
      throw e;
    }
  }

  @Override
  public void setBlocked(String deviceId, boolean blocked) {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = BLOCK;
    cloudModelTransaction(deviceId, CLOUD_MODEL_TOPIC, cloudModel);
  }

  @Override
  public void updateDevice(String deviceId, CloudModel device) {
    device.operation = ofNullable(device.operation).orElse(ModelOperation.UPDATE);
    cloudModelTransaction(deviceId, CLOUD_MODEL_TOPIC, device);
  }

  @Override
  public void updateRegistry(CloudModel registry) {
    registry.operation = ofNullable(registry.operation).orElse(ModelOperation.UPDATE);
    cloudModelTransaction(null, CLOUD_MODEL_TOPIC, registry);
  }

  @Override
  public void createResource(String deviceId, CloudModel makeDevice) {
    makeDevice.operation = ModelOperation.CREATE;
    CloudModel created = cloudModelTransaction(deviceId, CLOUD_MODEL_TOPIC, makeDevice);
    ifNotNullThen(makeDevice.num_id, () -> ifTrueThen(!makeDevice.num_id.equals(created.num_id),
        () -> System.err.printf("created num_id %s does not match expected %s%n", created.num_id,
            makeDevice.num_id)));
    makeDevice.num_id = created.num_id;
  }

  @Override
  public void deleteDevice(String deviceId, Set<String> unbindIds) {
    CloudModel deleteModel = new CloudModel();
    deleteModel.operation = ModelOperation.DELETE;
    deleteModel.gateway = ifNotNullGet(unbindIds, this::proxyGatewayModel);
    cloudModelTransaction(deviceId, CLOUD_MODEL_TOPIC, deleteModel);
  }

  private GatewayModel proxyGatewayModel(Set<String> unbindIds) {
    GatewayModel gatewayModel = new GatewayModel();
    gatewayModel.proxy_ids = unbindIds.stream().toList();
    return gatewayModel;
  }

  private CloudModel cloudModelTransaction(String deviceId, String topic, CloudModel model) {
    ModelOperation operation = Preconditions.checkNotNull(model.operation, "no operation");
    model.functions_ver = TOOLS_FUNCTIONS_VERSION;
    Map<String, Object> message = transaction(deviceId, topic, stringify(model), QuerySpeed.LONG);
    CloudModel cloudModel = convertTo(CloudModel.class, message);
    String cloudNumId = ifNotNullGet(cloudModel, result -> result.num_id);
    ModelOperation cloudOperation = ifNotNullGet(cloudModel, result -> result.operation);
    // This happens with devices are bound to gateways, so explicitly capture the relevant info.
    if (operation == DELETE && cloudOperation == BOUND) {
      throw new DeviceGatewayBoundException(cloudModel);
    }
    if (cloudNumId == null || cloudOperation != operation) {
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
  public void bindGatewayDevices(String gatewayId, Set<String> deviceIds, boolean shouldBind) {
    CloudModel device = new CloudModel();
    device.operation = shouldBind ? BIND : UNBIND;
    device.gateway = proxyGatewayModel(deviceIds);
    cloudModelTransaction(gatewayId, CLOUD_MODEL_TOPIC, device);
  }

  @Override
  public Map<String, CloudModel> fetchCloudModels(String forGatewayId) {
    return ofNullable(fetchCloudModel(forGatewayId)).map(model -> model.device_ids).orElse(null);
  }

  private CloudModel fetchCloudModel(String deviceId) {
    try {
      Map<String, Object> message = transaction(deviceId, CLOUD_QUERY_TOPIC,
          getQueryMessageString(), QuerySpeed.DYNAMIC);
      // TODO: Remove this legacy workaround once all cloud environments are updated (2024/11/15).
      if ("FETCH".equals(message.get("operation"))) {
        message.put("operation", READ.toString());
      }
      return convertTo(CloudModel.class, message);
    } catch (Exception e) {
      if (e.getMessage().contains("NOT_FOUND")) {
        return null;
      }
      throw e;
    }
  }

  private String getQueryMessageString() {
    CloudModel cloudModel = new CloudModel();
    cloudModel.operation = READ;
    return stringify(cloudModel);
  }

  private Map<String, Object> transaction(String deviceId, String topic,
      String message, QuerySpeed speed) {
    // TODO: Publish should return future to avoid race conditions.
    String transactionId = messageClient.publish(deviceId, topic, message);
    Map<String, Object> objectMap = waitForReply(transactionId, speed);
    String error = (String) ofNullable(objectMap).map(x -> x.get(ERROR_KEY)).orElse(null);
    if (error != null) {
      writeErrorDetail(transactionId, error, (String) objectMap.get(DETAIL_KEY));
      throw new RuntimeException(format("UDMIS error %s: %s", transactionId, error));
    }
    if (isNullOrEmpty((String) objectMap.get("operation")) && !topic.startsWith(UPDATE_PREFIX)) {
      System.err.printf("Warning! Returned transaction operation is null/empty: %s %s %s %s%n",
          deviceId, topic, transactionId, message);
    }
    return objectMap;
  }

  private void writeErrorDetail(String transactionId, String error, String detail) {
    String errorName = String.format("udmis_error_%s.txt", transactionId);
    File errorFile = new File(ERROR_DIR, errorName);
    String detailLines = ifNotNullGet(detail, string -> string.replace(CONDENSER_STRING, "\n"));
    try (PrintWriter output = new PrintWriter(errorFile)) {
      output.println("UDMIS error transaction " + transactionId);
      output.println("Message: " + error);
      output.println("Detail:\n" + detailLines);
      System.err.println("Captured UDMIS error to " + errorFile.getAbsolutePath());
    } catch (Exception e) {
      System.err.println("Error writing exception capture: " + friendlyStackTrace(e));
    }
  }

  private Map<String, Object> waitForReply(String sentId, QuerySpeed speed) {
    try {
      CompletableFuture<Map<String, Object>> replyFuture = new CompletableFuture<>();
      futures.put(sentId, replyFuture);
      return speed == QuerySpeed.DYNAMIC ? dynamicReplyFutureGet(replyFuture) :
          replyFuture.get(speed.seconds(), TimeUnit.SECONDS);
    } catch (Exception e) {
      futures.remove(sentId);
      throw new RuntimeException(
          format("UDMIS reflector timeout %ss for %s", speed.seconds(), sentId));
    }
  }

  private Map<String, Object> dynamicReplyFutureGet(
      CompletableFuture<Map<String, Object>> replyFuture) throws Exception {
    int pollSeconds = QuerySpeed.SHORT.seconds();
    while (messageClient.getLastProgressEvent().plusSeconds(pollSeconds).isAfter(Instant.now())) {
      try {
        return replyFuture.get(pollSeconds, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        // Do nothing, but loop for timeout check only when there's a timeout.
      }
    }
    return replyFuture.get(pollSeconds, TimeUnit.SECONDS);
  }

  private void processReplies() {
    while (messageClient.isActive()) {
      MessageBundle messageBundle = messageClient.takeNextMessage(QuerySpeed.QUICK);
      if (messageBundle == null) {
        continue;
      }

      try {
        String transactionId = messageBundle.attributes.get(TRANSACTION_KEY);
        CompletableFuture<Map<String, Object>> future = ifNotNullGet(transactionId,
            futures::remove);
        ifNotNullThen(future, f -> f.complete(messageBundle.message));

        if (messageBundle.message != null) {
          Exception exception = (Exception) messageBundle.message.get(EXCEPTION_KEY);
          if (exception != null) {
            exception.printStackTrace();
            throw new RuntimeException("UDMIS processing exception", exception);
          }

          String error = (String) messageBundle.message.get(ERROR_KEY);
          if (error != null) {
            if (transactionId == null || !transactionId.startsWith(sessionPrefix)) {
              continue;
            }
            throw new RuntimeException(format("UDMIS pipeline error %s: %s", transactionId, error));
          }
        }

        if (future == null && transactionId != null && transactionId.startsWith(sessionPrefix)) {
          throw new RuntimeException(
              "Received unexpected reply message " + stringifyTerse(messageBundle.attributes));
        }
      } catch (Exception e) {
        System.err.printf("Exception handling message: %s%n", friendlyStackTrace(e));
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
