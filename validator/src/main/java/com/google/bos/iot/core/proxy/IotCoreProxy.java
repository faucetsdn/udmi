package com.google.bos.iot.core.proxy;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.cloud.ServiceOptions;
import com.google.daq.mqtt.util.PubSubClient;
import com.google.daq.mqtt.util.PubSubPusher;
import com.google.udmi.util.MessageValidator;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import udmi.schema.Envelope.SubType;

/**
 * Class for proxying messages from one instance to another.
 */
public class IotCoreProxy {

  private static final String PROJECT_ID = ServiceOptions.getDefaultProjectId();

  private static final Logger LOG = LoggerFactory.getLogger(IotCoreProxy.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private static final long POLL_DELAY_MS = 1000;
  private static final String PROXY_SUBSCRIPTION_FMT = "%s-proxy";
  private static final String STATE_SUBSCRIPTION_FMT = "%s-state";
  private static final String CONFIG_TOPIC_FMT = "%s-config";
  private static final String VALIDATION_TOPIC_FMT = "%s-validation";
  private static final String STATE_SUBFOLDER = "state";
  private static final String CONFIG_SUBFOLDER = "config";
  private static final String SCHEMA_ROOT_PATH = "schema";

  private final Map<String, ProxyTarget> proxyTargets = new ConcurrentHashMap<>();
  private final ProjectMetadata configMap;

  private PubSubClient proxySubscription;
  private PubSubClient stateSubscription;
  private PubSubPusher configPublisher;
  private PubSubPusher validationPublisher;
  private MessageValidator messageValidator;
  private int exitCode;

  private IotCoreProxy(String configFile) {
    try {
      this.configMap = OBJECT_MAPPER.readValue(new File(configFile), ProjectMetadata.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + configFile, e);
    }
  }

  /**
   * Create an instance with args.
   *
   * @param args command-line args
   */
  public static void main(String[] args) {
    long startTime = System.currentTimeMillis();
    int exitCode = 0;
    LOG.warn("Hello. Starting at " + new Date());
    try {
      if (args.length != 1) {
        throw new IllegalArgumentException("Usage: [config_file]");
      }
      File configFile = new File(args[0]);
      LOG.info("Loading config from " + configFile.getAbsolutePath());
      IotCoreProxy iotCoreProxy = new IotCoreProxy(args[0]);
      try {
        iotCoreProxy.initialize();
        iotCoreProxy.messageLoop();
      } finally {
        exitCode = iotCoreProxy.terminate();
      }
    } catch (Exception e) {
      exitCode = -1;
      LOG.error("Top-level exception", e);
    } finally {
      long minutedElapsed = (System.currentTimeMillis() - startTime) / 1000 / 60;
      LOG.warn("Terminating at " + new Date());
      LOG.info(String.format("This process has gone %d minutes without a workplace accident.",
          minutedElapsed));
      LOG.error("Exiting with code " + exitCode);
      System.exit(exitCode);
    }
  }

  private void messageLoop() {
    LOG.info("Starting pubsub message loop");
    while (proxySubscription.isActive() && stateSubscription.isActive()) {
      try {
        proxySubscription.processMessage(this::processProxyMessage, POLL_DELAY_MS);
        stateSubscription.processMessage(this::processStateMessage, 0);
      } catch (Exception e) {
        LOG.error("Error with PubSub message loop", e);
        exitCode = -2;
      }
    }
  }

  private void processStateMessage(Map<String, String> attributes, String data) {
    String registryId = attributes.get("deviceRegistryId");
    String deviceId = attributes.get("deviceId");
    String subFolder = attributes.get("subFolder");

    if (subFolder != null) {
      LOG.warn(String.format("Ignoring state message from %s:%s with subFolder %s",
          registryId, deviceId, subFolder));
      return;
    }

    Map<String, String> modAttributes = new HashMap<>(attributes);
    modAttributes.put("subFolder", STATE_SUBFOLDER);
    processProxyMessage(modAttributes, data);
  }

  private void processProxyMessage(Map<String, String> attributes, String data) {
    String registryId = attributes.get("deviceRegistryId");
    String deviceId = attributes.get("deviceId");
    String subFolder = attributes.get("subFolder");
    try {
      ProxyTarget proxyTarget = getProxyTarget(registryId);
      proxyTarget.publish(deviceId, subFolder, data);
      validateMessage(attributes, data);
    } catch (Exception e) {
      String path = String.format("%s/%s/%s", registryId, deviceId, subFolder);
      LOG.error("Error processing message for " + path, e);
    }
  }

  private void validateMessage(Map<String, String> attributes, String data) {
    String subType = attributes.get("subType");
    String subFolder = attributes.get("subFolder");
    if (subType == null) {
      return;
    }
    try {
      if (!subFolder.equals("state") && !subFolder.equals("config")) {
        subFolder = SubType.EVENTS + "_" + subFolder;
      }
      List<String> validationErrors = messageValidator.validateMessage(subFolder, data);
      if (!validationErrors.isEmpty()) {
        LOG.warn(String.format("Found %d errors while validating %s:%s",
            validationErrors.size(), attributes.get("deviceRegistryId"),
            attributes.get("deviceId")));
      }
      ValidationBundle validationBundle = new ValidationBundle();
      validationBundle.errors = validationErrors;
      sendValidationResult(attributes, validationBundle);
    } catch (Exception e) {
      throw new RuntimeException(String.format("Against type/folder %s/%S", subType, subFolder), e);
    }
  }

  private void sendValidationResult(Map<String, String> attributes, ValidationBundle bundle) {
    try {
      String data = OBJECT_MAPPER.writeValueAsString(bundle);
      validationPublisher.sendMessage(attributes, data);
    } catch (Exception e) {
      throw new RuntimeException("While sending validation bundle", e);
    }
  }

  private void mirrorMessage(MessageBundle messageBundle) {
    if (CONFIG_SUBFOLDER.equals(messageBundle.attributes.get("subFolder"))) {
      configPublisher.sendMessage(messageBundle.attributes, messageBundle.data);
      validateMessage(messageBundle.attributes, messageBundle.data);
    }
  }

  private ProxyTarget getProxyTarget(String registryId) {
    return proxyTargets.computeIfAbsent(registryId,
        id -> new ProxyTarget(configMap, registryId, this::mirrorMessage));
  }

  private int terminate() {
    info("Terminating");
    if (proxySubscription != null) {
      proxySubscription.close();
      proxySubscription = null;
    }
    if (stateSubscription != null) {
      stateSubscription.close();
      stateSubscription = null;
    }
    proxyTargets.values().forEach(ProxyTarget::terminate);
    proxyTargets.clear();
    return exitCode;
  }

  private void initialize() {
    String proxySubBase = checkNotNull(configMap.get("proxy_sub_base"),
        "proxy_sub_base not defined");
    String proxySubscriptionName = String.format(PROXY_SUBSCRIPTION_FMT, proxySubBase);
    String stateSubscriptionName = String.format(STATE_SUBSCRIPTION_FMT, proxySubBase);
    proxySubscription = new PubSubClient(PROJECT_ID, proxySubscriptionName);
    stateSubscription = new PubSubClient(PROJECT_ID, stateSubscriptionName);

    String proxyTopicBase = checkNotNull(configMap.get("proxy_topic_base"),
        "proxy_topic_base not defined");
    String configTopicName = String.format(CONFIG_TOPIC_FMT, proxyTopicBase);
    String validationTopicName = String.format(VALIDATION_TOPIC_FMT, proxyTopicBase);
    configPublisher = new PubSubPusher(PROJECT_ID, configTopicName);
    validationPublisher = new PubSubPusher(PROJECT_ID, validationTopicName);

    messageValidator = new MessageValidator(SCHEMA_ROOT_PATH);
  }

  private void info(String msg) {
    LOG.info(msg);
  }

  private static class ValidationBundle {

    public List<String> errors;
  }
}
