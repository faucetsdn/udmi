package com.google.bos.iot.core.proxy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.MetadataMapKeys.UDMI_METADATA;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.cloud.ServiceOptions;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.udmi.util.GeneralUtils;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.Metadata;

/**
 * Target proxy entry.
 */
public class ProxyTarget {

  public static final String STATE_TOPIC = "state";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(Include.NON_NULL);
  private static final String PROJECT_ID = ServiceOptions.getDefaultProjectId();
  private static final Logger LOG = LoggerFactory.getLogger(ProxyTarget.class);
  private static final String EVENTS_TOPIC_FORMAT = "events/%s";
  private static final String CONFIG_TOPIC = "config";
  private static final String DEVICE_TOPIC_PREFIX = "/devices/";
  private static final String STATE_SUBFOLDER = "state";
  private static final long DEVICE_REFRESH_SEC = 10 * 60;
  private static final long CONFIG_UPDATE_LIMIT_MS = 2000; // 1sec limit, but allow for jitter.

  private final Map<String, MessagePublisher> messagePublishers = new ConcurrentHashMap<>();
  private final Map<String, String> configMap;
  private final String srcRegistryId;
  private final ProxyConfig proxyConfig;
  private final Consumer<MessageBundle> bundleOut;
  private final Map<String, LocalDateTime> initializedTimes = new ConcurrentHashMap<>();
  private final Map<String, LocalDateTime> configTimes = new ConcurrentHashMap<>();
  private final Map<String, Metadata> udmiMetadata = new ConcurrentHashMap<>();
  private ExecutionConfiguration cloudConfig;
  private CloudIotManager cloudIotManager;

  ProxyTarget(Map<String, String> configMap, String registryId,
      Consumer<MessageBundle> bundleOut) {
    info("Creating new proxy target for " + registryId);
    this.srcRegistryId = registryId;
    this.configMap = configMap;
    this.bundleOut = bundleOut;
    proxyConfig = loadProxyConfig(registryId);
    if (proxyConfig == null) {
      info("Ignoring unknown proxy target " + registryId);
      return;
    }
    cloudConfig = loadCloudConfig(registryId);
    initialize();
    info("Created proxy target instance for registry " + registryId);
  }

  private void initialize() {
    checkNotNull(cloudConfig.cloud_region, "cloud config cloud_region not defined");
    checkNotNull(cloudConfig.registry_id, "cloud config registry_id not defined");
    checkNotNull(proxyConfig.dstProjectId, "proxy config dstProjectId not defined");
    checkNotNull(proxyConfig.dstCloudRegion, "proxy config dstCloudRegion not defined");

    LOG.info(String.format("Pushing to Cloud IoT registry %s/%s/%s",
        proxyConfig.dstProjectId, proxyConfig.dstCloudRegion, proxyConfig.dstRegistryId));

    cloudIotManager = new CloudIotManager(PROJECT_ID, cloudConfig);
  }

  private ProxyConfig loadProxyConfig(String srcRegistryId) {
    final String keyPrefix = getKeyPrefix(srcRegistryId);
    final String regionKey = keyPrefix + "region";
    final String targetKey = keyPrefix + "target";
    final String registryKey = keyPrefix + "registry";

    if (!configMap.containsKey(targetKey)) {
      LOG.warn("Proxy target key not found: " + targetKey);
      return null;
    }
    if (!configMap.containsKey(regionKey)) {
      LOG.warn("Proxy region key not found: " + regionKey);
      return null;
    }
    ProxyConfig proxyConfig = new ProxyConfig();
    proxyConfig.dstProjectId = configMap.get(targetKey);
    proxyConfig.dstRegistryId = configMap.getOrDefault(registryKey, srcRegistryId);
    proxyConfig.dstCloudRegion = configMap.get(regionKey);
    return proxyConfig;
  }

  private String getKeyPrefix(String srcRegistryId) {
    return String.format("proxy_%s_", srcRegistryId);
  }

  private ExecutionConfiguration loadCloudConfig(String srcRegistryId) {
    ExecutionConfiguration executionConfiguration = new ExecutionConfiguration();
    executionConfiguration.registry_id = srcRegistryId;
    executionConfiguration.cloud_region = proxyConfig.dstCloudRegion;
    return executionConfiguration;
  }

  MessagePublisher getMqttPublisher(String deviceId) {
    Metadata udmi = getUdmiMetadata(deviceId);
    String gatewayId = extractGateway(udmi);
    if (gatewayId != null) {
      if (shouldIgnoreTarget(gatewayId)) {
        info("Ignoring proxy message for gateway " + gatewayId);
        return null;
      }
      return getMqttPublisher(gatewayId);
    }
    return messagePublishers.computeIfAbsent(deviceId, deviceKey -> newMqttPublisher(deviceId));
  }

  boolean hasMqttPublisher(String deviceId) {
    return messagePublishers.containsKey(deviceId);
  }

  void clearMqttPublisher(String deviceId) {
    info("Publishers remove " + deviceId);
    MessagePublisher publisher = messagePublishers.remove(deviceId);
    ifNotNullThen(publisher, publisher::close);
  }

  private MessagePublisher newMqttPublisher(String deviceId) {
    initializedTimes.put(deviceId, LocalDateTime.now());
    Device device = cloudIotManager.fetchDevice(deviceId);
    Map<String, String> metadata = device.getMetadata();
    if (metadata == null || !metadata.containsKey("key_algorithm")) {
      info("Missing metadata.key_algorithm for " + deviceId);
      return null;
    }
    info("Publishers create " + deviceId);
    String keyAlgorithm = metadata.get("key_algorithm");
    String keyBytesRaw = metadata.get("key_bytes");
    byte[] keyBytes = Base64.getDecoder().decode(keyBytesRaw);

    return new MqttPublisher(makeExecutionConfiguration(deviceId), keyBytes,
        keyAlgorithm, this::messageHandler, e -> errorHandler(deviceId, e));
  }

  private ExecutionConfiguration makeExecutionConfiguration(String deviceId) {
    ExecutionConfiguration executionConfiguration = new ExecutionConfiguration();
    executionConfiguration.project_id = proxyConfig.dstProjectId;
    executionConfiguration.cloud_region = proxyConfig.dstCloudRegion;
    executionConfiguration.registry_id = proxyConfig.dstRegistryId;
    executionConfiguration.device_id = deviceId;
    return executionConfiguration;
  }

  private String extractGateway(Metadata udmi) {
    return udmi == null ? null : udmi.gateway == null ? null : udmi.gateway.gateway_id;
  }

  private Metadata getUdmiMetadata(String deviceId) {
    return udmiMetadata.computeIfAbsent(deviceId, deviceKey -> parseUdmiMetadta(deviceId));
  }

  private Metadata parseUdmiMetadta(String deviceId) {
    try {
      Device device = cloudIotManager.fetchDevice(deviceId);
      Map<String, String> metadata = device.getMetadata();
      if (metadata == null) {
        return new Metadata();
      }
      String udmiMetadata = metadata.get(UDMI_METADATA);
      if (udmiMetadata == null) {
        return new Metadata();
      }
      return OBJECT_MAPPER.readValue(udmiMetadata, Metadata.class);
    } catch (Exception e) {
      throw new RuntimeException("While loading device udmi metadata for " + deviceId, e);
    }
  }

  boolean publish(String deviceId, String subFolder, String data) {
    if (proxyConfig == null) {
      return false;
    }
    if (subFolder == null || "".equals(subFolder)) {
      info("Ignoring message with no subFolder for " + deviceId);
      return false;
    }
    if (shouldIgnoreTarget(deviceId)) {
      info("Ignoring " + subFolder + " message for " + deviceId);
      return false;
    }
    try {
      MessagePublisher messagePublisher = getMqttPublisher(deviceId);
      if (messagePublisher == null) {
        return false;
      }
      info("Sending " + subFolder + " message for " + deviceId);
      String mqttTopic = STATE_SUBFOLDER.equals(subFolder) ? STATE_TOPIC :
          String.format(EVENTS_TOPIC_FORMAT, subFolder);
      messagePublisher.publish(deviceId, mqttTopic, data);
      mirrorMessage(deviceId, data, subFolder);
    } catch (Exception e) {
      LOG.error("Problem publishing " + subFolder + " for " + deviceId, e);
      clearMqttPublisher(deviceId);
      return false;
    }
    return true;
  }

  private boolean shouldIgnoreTarget(String deviceId) {
    if (hasMqttPublisher(deviceId)) {
      return false;
    }
    if (!initializedTimes.containsKey(deviceId)) {
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime initializedTime = initializedTimes.get(deviceId);
    return now.isBefore(initializedTime.plusSeconds(DEVICE_REFRESH_SEC));
  }

  void terminate() {
    messagePublishers.values().forEach(MessagePublisher::close);
    messagePublishers.clear();
  }

  private void errorHandler(String deviceId, Throwable error) {
    LOG.error("Error publishing " + deviceId, error);
    clearMqttPublisher(deviceId);
  }

  private void messageHandler(String topic, String message) {
    info("Received on " + topic);
    if (topic.endsWith(CONFIG_TOPIC)) {
      try {
        String deviceId = topic
            .substring(DEVICE_TOPIC_PREFIX.length(), topic.length() - CONFIG_TOPIC.length() - 1);
        LocalDateTime configTime = configTimes
            .computeIfAbsent(deviceId, id -> LocalDateTime.now().minusMinutes(1));
        long deltaMs = Duration.between(configTime, LocalDateTime.now()).toMillis();
        info(String
            .format("Updating device config for %s/%s after %dms", srcRegistryId, deviceId,
                deltaMs));
        if (deltaMs < CONFIG_UPDATE_LIMIT_MS) {
          Thread.sleep(CONFIG_UPDATE_LIMIT_MS - deltaMs);
        }
        cloudIotManager.setDeviceConfig(deviceId, message);
        configTimes.put(deviceId, LocalDateTime.now());
        mirrorMessage(deviceId, message, CONFIG_TOPIC);
      } catch (Exception e) {
        throw new RuntimeException("While updating config for " + topic, e);
      }
    }
  }

  private void mirrorMessage(String deviceId, String message, String subFolder) {
    MessageBundle bundle = new MessageBundle(message);
    bundle.attributes.put("deviceRegistryId", srcRegistryId);
    bundle.attributes.put("deviceId", deviceId);
    bundle.attributes.put("subFolder", subFolder);
    bundleOut.accept(bundle);
  }

  private void info(String msg) {
    LOG.info(msg);
  }
}
