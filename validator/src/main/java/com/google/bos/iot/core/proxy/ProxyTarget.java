package com.google.bos.iot.core.proxy;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.cloud.ServiceOptions;
import com.google.common.base.Joiner;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyTarget {

  private static final String PROJECT_ID = ServiceOptions.getDefaultProjectId();
  private static final Logger LOG = LoggerFactory.getLogger(ProxyTarget.class);

  private static final String PROXY_CONFIG_FORMAT = "%s_proxy.json";
  private static final String CLOUD_CONFIG_FORMAT = "%s_cloud.json";
  public static final String DEVICE_KEY_FORMAT = "%s_%s_rsa.pkcs8";

  public static final String HELLO_TOPIC = "events/hello";
  public static final String HELLO_DATA = "hello";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  public static final String EVENTS_TOPIC_FORMAT = "events/%s";
  public static final String CONFIG_TOPIC = "config";
  public static final String DEVICE_TOPIC_PREFIX = "/devices/";

  private final Map<String, MessagePublisher> messagePublishers = new ConcurrentHashMap<>();
  private final String configDir;
  private final String registryId;
  private final ProxyConfig proxyConfig;
  private final CloudIotConfig cloudConfig;
  private CloudIotManager cloudIotManager;
  private final Set<String> ignoredDevices = new ConcurrentSkipListSet<>();
  private Set<String> targetDevices;

  public ProxyTarget(String configDir, String registryId) {
    this.configDir = configDir;
    this.registryId = registryId;
    if (!configExists(registryId)) {
      info("Ignoring unknown proxy target " + registryId);
      this.proxyConfig = null;
      this.cloudConfig = null;
      return;
    }
    this.proxyConfig = loadProxyConfig();
    this.cloudConfig = loadCloudConfig();
    initialize();
    info("Created proxy target instance for registry " + registryId);
  }

  private void initialize() {
    checkNotNull(cloudConfig.cloud_region,"cloud config cloud_region not defined");
    checkNotNull(cloudConfig.registry_id,"cloud config registry_id not defined");
    checkNotNull(proxyConfig.algorithm, "proxy config algorithm not defined");
    checkNotNull(proxyConfig.dstProjectId,"proxy config dstProjectId not defined");
    checkNotNull(proxyConfig.dstCloudRegion,"proxy config dstCloudRegion not defined");
    checkNotNull(proxyConfig.registryId,"proxy config registryId not defined");
    checkArgument(proxyConfig.registryId.equals(cloudConfig.registry_id),
        "config registry id mismatch");

    LOG.info(String.format("Pushing to Cloud IoT registry %s/%s/%s",
        proxyConfig.dstProjectId,proxyConfig.dstCloudRegion,proxyConfig.registryId));

    cloudIotManager = new CloudIotManager(PROJECT_ID, cloudConfig);

    targetDevices = cloudIotManager.listDevices();
    LOG.info("Proxying for devices "+Joiner.on(", ").join(targetDevices));
  }

  private void publishHello(String deviceId) {
    getMqttPublisher(deviceId).publish(deviceId, HELLO_TOPIC, HELLO_DATA);
  }

  private boolean configExists(String registryId) {
    return new File(configDir, String.format(PROXY_CONFIG_FORMAT, registryId)).exists();
  }

  private ProxyConfig loadProxyConfig() {
    File proxyConfig = new File(configDir, String.format(PROXY_CONFIG_FORMAT, registryId));
    info("Reading proxy configuration from " + proxyConfig.getAbsolutePath());
    try {
      return OBJECT_MAPPER.readValue(proxyConfig, ProxyConfig.class);
    } catch (Exception e) {
      throw new RuntimeException(
          "While reading configuration file " + proxyConfig.getAbsolutePath(), e);
    }
  }

  private CloudIotConfig loadCloudConfig() {
    File cloudConfig = new File(configDir, String.format(CLOUD_CONFIG_FORMAT, registryId));
    info("Reading cloud configuration from " + cloudConfig.getAbsolutePath());
    try {
      return OBJECT_MAPPER.readValue(cloudConfig, CloudIotConfig.class);
    } catch (Exception e) {
      throw new RuntimeException(
          "While reading configuration file " + cloudConfig.getAbsolutePath(), e);
    }
  }

  private String getPublisherKey(String deviceId) {
    return Joiner.on(":").join(proxyConfig.dstProjectId, proxyConfig.registryId, deviceId);
  }

  public MessagePublisher getMqttPublisher(String deviceId) {
    String publisherKey = getPublisherKey(deviceId);
    return messagePublishers
        .computeIfAbsent(publisherKey, deviceKey -> newMqttPublisher(proxyConfig, deviceId));
  }

  private MessagePublisher newMqttPublisher(ProxyConfig proxyConfig, String deviceId) {
    File deviceKeyFile = getDeviceKeyFile(proxyConfig.registryId, deviceId);
    if (!deviceKeyFile.exists()) {
      LOG.warn(String.format("Disabling %s with missing key file %s", deviceId, deviceKeyFile));
      return new NullPublisher(deviceId);
    }
    LOG.info(String.format("Publishing %s from key file %s", deviceId, deviceKeyFile));
    return new MqttPublisher(proxyConfig.dstProjectId, proxyConfig.dstCloudRegion,
        proxyConfig.registryId, deviceId, deviceKeyFile, proxyConfig.algorithm,
        this::messageHandler, this::errorHandler);
  }

  private File getDeviceKeyFile(String siteName, String deviceId) {
    String deviceKeyFileName = String.format(DEVICE_KEY_FORMAT, siteName, deviceId);
    return new File(configDir, deviceKeyFileName);
  }

  public void publish(String deviceId, String subFolder, String data) {
    if (proxyConfig == null) {
      return;
    }
    if (!targetDevices.contains(deviceId)) {
      if (ignoredDevices.add(deviceId)) {
        info("Ignoring " + subFolder + " message for " + registryId + ":" + deviceId);
      }
      return;
    }
    info("Sending " + subFolder + " message for " + registryId + ":" + deviceId);
    MessagePublisher messagePublisher = getMqttPublisher(deviceId);
    String mqttTopic = String.format(EVENTS_TOPIC_FORMAT, subFolder);
    messagePublisher.publish(deviceId, mqttTopic, data);
  }

  public void terminate() {
    messagePublishers.values().forEach(MessagePublisher::close);
    messagePublishers.clear();
  }

  private void errorHandler(Throwable error) {
    LOG.error("Publisher error", error);
    terminate();
  }

  private void messageHandler(String topic, String message) {
    info("Received message for " + topic);
    if (topic.endsWith(CONFIG_TOPIC)) {
      String deviceId = topic.substring(DEVICE_TOPIC_PREFIX.length(), topic.length() - CONFIG_TOPIC.length() - 1);
      info(String.format("Updating device config for %s/%s", registryId, deviceId));
      cloudIotManager.setDeviceConfig(deviceId, message);
    }
  }

  private void info(String msg) {
    LOG.info(msg);
  }
}
