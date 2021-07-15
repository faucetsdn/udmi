package com.google.bos.iot.core.proxy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.daq.mqtt.util.CloudIotManager.UDMI_METADATA;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.bos.iot.core.proxy.MessageValidator.RelativeClient;
import com.google.cloud.ServiceOptions;
import com.google.daq.mqtt.util.CloudIotConfig;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import udmi.schema.Metadata;
import udmi.schema.PointsetEvent;

public class ProxyTarget {

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

  static final String STATE_TOPIC = "state";
  private static final long DEVICE_REFRESH_SEC = 10 * 60;
  private static final long CONFIG_UPDATE_LIMIT_MS = 2000; // 1sec limit, but allow for jitter.

  private final Map<String, MessagePublisher> messagePublishers = new ConcurrentHashMap<>();
  private final Map<String, String> configMap;
  private final String registryId;
  private final ProxyConfig proxyConfig;
  private final Consumer<MessageBundle> bundleOut;
  private CloudIotConfig cloudConfig;
  private CloudIotManager cloudIotManager;
  private Map<String, LocalDateTime> initializedTimes = new ConcurrentHashMap<>();
  private Map<String, LocalDateTime> configTimes = new ConcurrentHashMap<>();
  private Map<String, Metadata> udmiMetadata = new ConcurrentHashMap<>();

  public ProxyTarget(Map<String, String> configMap, String registryId,
      Consumer<MessageBundle> bundleOut) {
    info("Creating new proxy target for " + registryId);
    this.configMap = configMap;
    this.registryId = registryId;
    this.bundleOut = bundleOut;
    proxyConfig = loadProxyConfig();
    if (proxyConfig == null) {
      info("Ignoring unknown proxy target " + registryId);
      return;
    }
    cloudConfig = loadCloudConfig();
    initialize();
    info("Created proxy target instance for registry " + registryId);
  }

  private void initialize() {
    checkNotNull(cloudConfig.cloud_region,"cloud config cloud_region not defined");
    checkNotNull(cloudConfig.registry_id,"cloud config registry_id not defined");
    checkNotNull(proxyConfig.dstProjectId,"proxy config dstProjectId not defined");
    checkNotNull(proxyConfig.dstCloudRegion,"proxy config dstCloudRegion not defined");

    LOG.info(String.format("Pushing to Cloud IoT registry %s/%s/%s",
        proxyConfig.dstProjectId,proxyConfig.dstCloudRegion, registryId));

    cloudIotManager = new CloudIotManager(PROJECT_ID, cloudConfig);
  }

  private ProxyConfig loadProxyConfig() {
    String keyPrefix = getKeyPrefix();
    String regionKey = keyPrefix + "region";
    String targetKey = keyPrefix + "target";
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
    proxyConfig.dstCloudRegion = configMap.get(regionKey);
    return proxyConfig;
  }

  private String getKeyPrefix() {
    return String.format("proxy_%s_", registryId);
  }

  private CloudIotConfig loadCloudConfig() {
    CloudIotConfig cloudIotConfig = new CloudIotConfig();
    cloudIotConfig.registry_id = registryId;
    cloudIotConfig.cloud_region = proxyConfig.dstCloudRegion;
    return cloudIotConfig;
  }

  public MessagePublisher getMqttPublisher(String deviceId) {
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

  public boolean hasMqttPublisher(String deviceId) {
    return messagePublishers.containsKey(deviceId);
  }

  public void clearMqttPublisher(String deviceId) {
    info("Publishers remove " + deviceId);
    MessagePublisher publisher = messagePublishers.remove(deviceId);
    if (publisher != null) {
      publisher.close();
    }
  }

  private MessagePublisher newMqttPublisher(String deviceId) {
    initializedTimes.put(deviceId, LocalDateTime.now());
    info("Publishers create " + deviceId);
    Device device = cloudIotManager.fetchDevice(deviceId);
    Map<String, String> metadata = device.getMetadata();
    String keyAlgorithm = metadata.get("key_algorithm");
    String key_bytes = metadata.get("key_bytes");
    byte[] keyBytes = Base64.getDecoder().decode(key_bytes);
    return new MqttPublisher(proxyConfig.dstProjectId, proxyConfig.dstCloudRegion,
        registryId, deviceId, keyBytes, keyAlgorithm,
        this::messageHandler, this::errorHandler);
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
      String udmi_metadata = metadata.get(UDMI_METADATA);
      if (udmi_metadata == null) {
        return new Metadata();
      }
      return OBJECT_MAPPER.readValue(udmi_metadata, Metadata.class);
    } catch (Exception e) {
      throw new RuntimeException("While loading device udmi metadata" + deviceId, e);
    }
  }

  public void publish(String deviceId, String subFolder, String data) {
    if (proxyConfig == null) {
      return;
    }
    if (subFolder == null) {
      info("Ignoring message with no subFolder for " + deviceId);
      return;
    }
    if (shouldIgnoreTarget(deviceId)) {
        info("Ignoring " + subFolder + " message for " + deviceId);
        return;
    }
    info("Sending " + subFolder + " message for " + deviceId);
    try {
      MessagePublisher messagePublisher = getMqttPublisher(deviceId);
      if (messagePublisher == null) {
        return;
      }
      String mqttTopic = STATE_SUBFOLDER.equals(subFolder) ? STATE_TOPIC :
          String.format(EVENTS_TOPIC_FORMAT, subFolder);
      messagePublisher.publish(deviceId, mqttTopic, data);
      mirrorMessage(deviceId, data, subFolder);
    } catch (Exception e) {
      LOG.error("Problem publishing device " + deviceId, e);
      clearMqttPublisher(deviceId);
    }
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

  public void terminate() {
    messagePublishers.values().forEach(MessagePublisher::close);
    messagePublishers.clear();
  }

  private void errorHandler(MqttPublisher publisher, Throwable error) {
    String deviceId = publisher.getDeviceId();
    LOG.error("Error publishing " + deviceId, error);
    clearMqttPublisher(deviceId);
  }

  private void messageHandler(String topic, String message) {
    info("Received message for " + topic);
    if (topic.endsWith(CONFIG_TOPIC)) {
      try {
        String deviceId = topic
            .substring(DEVICE_TOPIC_PREFIX.length(), topic.length() - CONFIG_TOPIC.length() - 1);
        LocalDateTime configTime = configTimes
            .computeIfAbsent(deviceId, id -> LocalDateTime.now().minusMinutes(1));
        long deltaMs = Duration.between(configTime, LocalDateTime.now()).toMillis();
        info(String
            .format("Updating device config for %s/%s after %dms", registryId, deviceId, deltaMs));
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
    bundle.attributes.put("deviceRegistryId", registryId);
    bundle.attributes.put("deviceId", deviceId);
    bundle.attributes.put("subFolder", subFolder);
    bundleOut.accept(bundle);
  }

  private void info(String msg) {
    LOG.info(msg);
  }
}
