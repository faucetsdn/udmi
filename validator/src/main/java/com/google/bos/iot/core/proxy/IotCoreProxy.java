package com.google.bos.iot.core.proxy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.cloud.ServiceOptions;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IotCoreProxy {

  private static final String PROJECT_ID = ServiceOptions.getDefaultProjectId();

  private static final Logger LOG = LoggerFactory.getLogger(IotCoreProxy.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  public static final int DEVICE_CHECKOUT_COUNT = 100;
  private static final String SUBSCRIPTION_NAME = "iot-core-proxy";
  private static final String HACK_PROJECT = "bos-uk-lon-6ps";
  private static final String HACK_REGISTRY = "UK-LON-6PS";

  private final Map<String, ProxyTarget> proxyTargets = new ConcurrentHashMap<>();
  private final String configDir;
  private final AtomicInteger messageCount = new AtomicInteger();

  private PubSubClient pubSubClient;
  private int exitCode;

  public static void main(String[] args) {
    long startTime = System.currentTimeMillis();
    int exitCode = 0;
    LOG.warn("Hello. Starting at " + new Date());
    try {
      if (args.length != 1) {
        throw new IllegalArgumentException("Usage: [configPath]");
      }
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

  private IotCoreProxy(String configDir) {
    this.configDir = configDir;
  }

  private void messageLoop() {
    LOG.info("Starting pubsub message loop");
    while (pubSubClient.isActive()) {
      try {
        if (messageCount.incrementAndGet() % DEVICE_CHECKOUT_COUNT == 0) {
          LOG.info("Processing message #" + messageCount.get());
        }
        pubSubClient.processMessage(this::processMessage);
      } catch (Exception e) {
        LOG.error("Error with PubSub message loop", e);
        exitCode = -2;
      }
    }
  }

  private void processMessage(String data, Map<String, String> attributes) {
    String registryId = attributes.get("deviceRegistryId");
    String deviceId = attributes.get("deviceId");
    String subFolder = attributes.get("subFolder");
    try {
      if (HACK_PROJECT.equals(PROJECT_ID)) {
        registryId = HACK_REGISTRY;
      }
      ProxyTarget proxyTarget = getProxyTarget(registryId);
      proxyTarget.publish(deviceId, subFolder, augmentRawMessage(data, deviceId, registryId));
    } catch (Exception e) {
      String path = String.format("%s/%s/%s", registryId, deviceId, subFolder);
      LOG.error("Error processing message for " + path, e);
    }
  }

  private ProxyTarget getProxyTarget(String registryId) {
    return proxyTargets.computeIfAbsent(registryId, id -> new ProxyTarget(configDir, registryId));
  }

  private String augmentRawMessage(String data, String deviceId, String registryId) {
    try {
      if (!HACK_REGISTRY.equals(registryId)) {
        return data;
      }
      ObjectNode jsonObject = (ObjectNode) OBJECT_MAPPER.readTree(data);
      jsonObject.put("deviceId", deviceId);
      jsonObject.put("registryId", registryId);
      return OBJECT_MAPPER.writeValueAsString(jsonObject);
    } catch (IOException e) {
      throw new RuntimeException("Could not augment data message " + data);
    }
  }
  
  private int terminate() {
    info("Terminating");
    if (pubSubClient != null) {
      pubSubClient.stop();
      pubSubClient = null;
    }
    proxyTargets.values().forEach(ProxyTarget::terminate);
    proxyTargets.clear();
    return exitCode;
  }

  private void initialize() {
    LOG.info(String.format("Pulling from pubsub subscription %s/%s",
        PROJECT_ID, SUBSCRIPTION_NAME));
    pubSubClient = new PubSubClient(PROJECT_ID, SUBSCRIPTION_NAME);
  }

  private void info(String msg) {
    LOG.info(msg);
  }
}
