package com.google.bos.udmi.service.support;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.isNotEmpty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.udmi.util.CertManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import udmi.schema.Basic;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Transport;

/**
 * Thread-safe, non-blocking service to interact with Mosquitto Dynamic Security plugin
 * over its MQTT v5 JSON API. Employs enqueuing, throttling, batching, and automatic fallbacks.
 */
public class MosquittoDynamicSecurityService implements MqttCallback {

  private static final Logger log = LoggerFactory.getLogger(MosquittoDynamicSecurityService.class);

  private static final int MAX_QUEUE_SIZE = 10000;
  private static final int BATCH_SIZE_LIMIT = 100;
  private static final long BATCH_BYTES_LIMIT = 10 * 1024 * 1024; // 10 MB
  private static final long MIN_PUBLISH_INTERVAL_MS = 500; // 0.5 seconds
  private static final String CONTROL_TOPIC = "$CONTROL/dynamic-security/v1";
  private static final String RESPONSE_TOPIC = "$CONTROL/dynamic-security/v1/response";

  private final EndpointConfiguration endpoint;
  private final BlockingQueue<CommandRequest> commandQueue =
      new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
  private final MqttClient mqttClient;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private long lastPublishTime = 0;
  private boolean batchInFlight = false;
  private List<CommandRequest> inFlightBatch = null;
  private ScheduledFuture<?> scheduledTask = null;

  /**
   * Constructs and connects the Dynamic Security Service.
   */
  public MosquittoDynamicSecurityService(EndpointConfiguration endpoint) {
    this.endpoint = endpoint;
    this.mqttClient = createMqttClient();
    connect();
  }

  private MqttClient createMqttClient() {
    String clientId = "dynsec-service-" + format("%08x", (long) (Math.random() * 0x100000000L));
    String brokerUrl = makeBrokerUrl(endpoint);
    log.info("Creating Dynamic Security MQTT client {} to {}", clientId, brokerUrl);
    try {
      MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
      client.setCallback(this);
      return client;
    } catch (MqttException e) {
      throw new RuntimeException("Failed to create Paho MQTT v5 client", e);
    }
  }

  private String makeBrokerUrl(EndpointConfiguration ep) {
    Transport transport = ep.transport != null ? ep.transport : Transport.SSL;
    int port = ep.port != null ? ep.port : 8883;
    String transportStr = Transport.TCP.equals(transport) ? "tcp" : "ssl";
    return transportStr + "://" + ep.hostname + ":" + port;
  }

  private void connect() {
    try {
      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setAutomaticReconnect(true);
      options.setConnectionTimeout(5);

      boolean useSsl = Transport.SSL.equals(endpoint.transport)
          || (endpoint.port != null && endpoint.port == 8883)
          || endpoint.ca_file != null;

      if (useSsl) {
        checkState(isNotEmpty(endpoint.ca_file), "ca_file is empty");
        checkState(isNotEmpty(endpoint.cert_file), "cert_file is empty");
        checkState(isNotEmpty(endpoint.key_file), "key_file is empty");
        String pass = ifNotNullGet(endpoint.auth_provider,
            p -> ifNotNullGet(p.basic, b -> b.password));
        CertManager certManager = new CertManager(
            new File(endpoint.ca_file),
            new File(endpoint.cert_file),
            new File(endpoint.key_file),
            endpoint.transport,
            pass,
            log::info
        );
        options.setSocketFactory(certManager.getSocketFactory());
      }

      if (endpoint.auth_provider != null && endpoint.auth_provider.basic != null) {
        Basic basic = endpoint.auth_provider.basic;
        if (basic.username != null) {
          options.setUserName(basic.username);
        }
        if (basic.password != null) {
          options.setPassword(basic.password.getBytes(StandardCharsets.UTF_8));
        }
      }

      log.info("Connecting Dynamic Security Service to MQTT broker...");
      mqttClient.connect(options);
      log.info("Connected! Subscribing to response topic: {}", RESPONSE_TOPIC);
      mqttClient.subscribe(RESPONSE_TOPIC, 1);
    } catch (Exception e) {
      throw new RuntimeException("Failed to connect Dynamic Security Service to MQTT broker", e);
    }
  }

  /**
   * Enqueues a command request to the service non-blockingly.
   */
  public CompletableFuture<Void> enqueueCommand(CommandRequest req) {
    if (commandQueue.size() >= MAX_QUEUE_SIZE) {
      // TODO: Throwing QueueFullException synchronously here can abort the batch response
      // processing loop (e.g., during fallback enqueues). Complete exceptionally instead.
      // This can cause corrupted state - e.g. a device will have 5 of 6 ACL's to permit
      // communication added but will not get marked as "not bound".
      // Probably need some retry logic, maybe with recall logic too.
      throw new QueueFullException("Dynamic security queue is full. Size: " + commandQueue.size());
    }
    commandQueue.offer(req);
    triggerWorker();
    return req.future;
  }

  private synchronized void triggerWorker() {
    if (batchInFlight) {
      return;
    }
    if (commandQueue.isEmpty()) {
      return;
    }
    if (scheduledTask != null && !scheduledTask.isDone()) {
      return;
    }
    long now = System.currentTimeMillis();
    long timeSinceLastPublish = now - lastPublishTime;
    long delay = MIN_PUBLISH_INTERVAL_MS - timeSinceLastPublish;

    if (delay <= 0) {
      executor.submit(this::drainAndPublishBatch);
    } else {
      scheduledTask = scheduler.schedule(
          () -> executor.submit(this::drainAndPublishBatch),
          delay,
          TimeUnit.MILLISECONDS
      );
    }
  }

  private synchronized void drainAndPublishBatch() {
    if (batchInFlight || commandQueue.isEmpty()) {
      return;
    }
    batchInFlight = true;
    if (scheduledTask != null) {
      scheduledTask.cancel(false);
      scheduledTask = null;
    }

    List<CommandRequest> batch = new ArrayList<>();
    long totalBytes = 0;

    while (batch.size() < BATCH_SIZE_LIMIT
        && totalBytes < BATCH_BYTES_LIMIT
        && !commandQueue.isEmpty()) {
      CommandRequest req = commandQueue.peek();
      if (req == null) {
        break;
      }
      long nextSize = req.serializedPayload.length + 1;
      if (batch.size() > 0 && totalBytes + nextSize > BATCH_BYTES_LIMIT) {
        break;
      }
      commandQueue.poll();
      batch.add(req);
      totalBytes += nextSize;
    }

    if (batch.isEmpty()) {
      batchInFlight = false;
      return;
    }

    this.inFlightBatch = batch;
    publishBatchToBroker(batch);
  }

  private void publishBatchToBroker(List<CommandRequest> batch) {
    try {
      byte[] payload = serializeBatch(batch);
      MqttMessage message = new MqttMessage();
      message.setPayload(payload);
      message.setQos(1);

      MqttProperties properties = new MqttProperties();
      properties.setResponseTopic(RESPONSE_TOPIC);
      message.setProperties(properties);

      log.debug("Publishing batch containing {} commands ({} bytes) to {}: {}",
          batch.size(), payload.length, CONTROL_TOPIC, new String(payload, StandardCharsets.UTF_8));
      mqttClient.publish(CONTROL_TOPIC, message);
    } catch (Exception e) {
      log.error("Failed to publish batch to Mosquitto broker", e);
      // Fail all futures in the batch exceptionally
      this.inFlightBatch = null;
      this.batchInFlight = false;
      for (CommandRequest req : batch) {
        req.future.completeExceptionally(e);
      }
      triggerWorker();
    }
  }

  private byte[] serializeBatch(List<CommandRequest> batch) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      out.write("{\"commands\":[".getBytes(StandardCharsets.UTF_8));
      for (int i = 0; i < batch.size(); i++) {
        if (i > 0) {
          out.write((int) ',');
        }
        out.write(batch.get(i).serializedPayload);
      }
      out.write("]}".getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      // Bypassed exception as ByteArrayOutputStream does not throw IOException
    }
    return out.toByteArray();
  }

  @Override
  public void messageArrived(String topic, MqttMessage message) {
    if (!RESPONSE_TOPIC.equals(topic)) {
      return;
    }
    List<CommandRequest> batchCopy = this.inFlightBatch;
    if (batchCopy == null) {
      log.warn("Received dynamic security response but no batch was in-flight");
      return;
    }
    this.inFlightBatch = null;
    this.lastPublishTime = System.currentTimeMillis();
    this.batchInFlight = false;

    executor.submit(() -> processResponses(batchCopy, message.getPayload()));
  }

  @SuppressWarnings("unchecked")
  private void processResponses(List<CommandRequest> batch, byte[] responsePayload) {
    try {
      String responseString = new String(responsePayload, StandardCharsets.UTF_8);
      log.debug("Received dynamic security broker response: {}", responseString);
      Map<String, Object> responseMap = objectMapper.readValue(responsePayload, Map.class);
      List<Map<String, Object>> responsesList =
          (List<Map<String, Object>>) responseMap.get("responses");

      if (responsesList == null || responsesList.size() != batch.size()) {
        throw new RuntimeException("Batch response mismatch or broker error: "
            + new String(responsePayload, StandardCharsets.UTF_8));
      }

      for (int i = 0; i < batch.size(); i++) {
        CommandRequest req = batch.get(i);
        Map<String, Object> resp = responsesList.get(i);

        Integer status = (Integer) resp.get("status");
        String error = (String) resp.get("error");

        if (error == null && (status == null || status == 0)) {
          req.future.complete(null);
        } else if (isBenignError(req.commandName, error)) {
          log.debug("Handling benign broker error for command {}: {}", req.commandName, error);
          req.future.complete(null);
        } else if ("createClient".equals(req.commandName)
            && error != null
            && error.contains("exists")
            && req.isFallbackSupported) {
          log.info("Client already exists. Triggering modifyClient fallback for {}", req.username);
          triggerFallback(req, "modifyClient");
        } else if ("modifyClient".equals(req.commandName)
            && error != null
            && error.contains("not found")
            && req.isFallbackSupported) {
          log.info("Client not found. Triggering createClient fallback for {}", req.username);
          triggerFallback(req, "createClient");
        } else {
          req.future.completeExceptionally(new RuntimeException("Broker error: " + error));
        }
      }
    } catch (Exception e) {
      log.error("Error processing batch responses", e);
      for (CommandRequest req : batch) {
        req.future.completeExceptionally(e);
      }
    } finally {
      triggerWorker();
    }
  }

  private void triggerFallback(CommandRequest originalReq, String fallbackCommand) {
    Map<String, Object> cmd = new HashMap<>();
    cmd.put("command", fallbackCommand);
    cmd.put("username", originalReq.username);
    if (originalReq.password != null) {
      cmd.put("password", originalReq.password);
    }
    if (originalReq.clientId != null) {
      cmd.put("clientid", originalReq.clientId);
    }

    try {
      byte[] bytes = objectMapper.writeValueAsBytes(cmd);
      CompletableFuture<Void> fallbackFuture = new CompletableFuture<>();
      CommandRequest fallbackReq = new CommandRequest(
          fallbackCommand,
          bytes,
          fallbackFuture,
          originalReq.username,
          originalReq.password,
          originalReq.clientId,
          false
      );

      enqueueCommand(fallbackReq);

      fallbackFuture.whenComplete((res, ex) -> {
        if (ex != null) {
          originalReq.future.completeExceptionally(ex);
        } else {
          originalReq.future.complete(null);
        }
      });
    } catch (Exception e) {
      originalReq.future.completeExceptionally(e);
    }
  }

  private boolean isBenignError(String command, String error) {
    if (error == null) {
      return false;
    }
    String cleanError = error.trim();
    return cleanError.equalsIgnoreCase("Role already exists")
        || cleanError.equalsIgnoreCase("Group already exists")
        || cleanError.equalsIgnoreCase("Client already has role")
        || cleanError.equalsIgnoreCase("Client does not have role")
        || cleanError.equalsIgnoreCase("Role already has ACL")
        || cleanError.equalsIgnoreCase("ACL not found")
        || cleanError.equalsIgnoreCase("Client not found")
        || cleanError.equalsIgnoreCase("Role not found")
        || cleanError.equalsIgnoreCase("Group not found")
        || cleanError.equalsIgnoreCase("ACL with this topic already exists");
  }

  /**
   * Shuts down executors and disconnects the MQTT client.
   */
  public void shutdown() {
    try {
      executor.shutdown();
      scheduler.shutdown();
      if (mqttClient.isConnected()) {
        mqttClient.disconnect();
      }
      mqttClient.close();
    } catch (Exception e) {
      log.error("Error during Dynamic Security Service shutdown", e);
    }
  }

  @Override
  public void disconnected(MqttDisconnectResponse disconnectResponse) {
    log.warn("MQTT connection lost: {}", disconnectResponse);
  }

  @Override
  public void mqttErrorOccurred(MqttException exception) {
    log.error("MQTT error occurred", exception);
  }

  @Override
  public void deliveryComplete(IMqttToken token) {
  }

  @Override
  public void connectComplete(boolean reconnect, String serverUri) {
    log.info("MQTT connect completed. Reconnect: {}, URI: {}", reconnect, serverUri);
    if (reconnect) {
      try {
        mqttClient.subscribe(RESPONSE_TOPIC, 1);
      } catch (MqttException e) {
        log.error("Failed to re-subscribe to response topic on reconnect", e);
      }
    }
  }

  @Override
  public void authPacketArrived(int reasonCode, MqttProperties properties) {
  }

  private static String format(String format, Object... args) {
    return String.format(format, args);
  }

  /**
   * Models a command request in the dynamic security system queue.
   */
  public static class CommandRequest {
    public final String commandName;
    public final byte[] serializedPayload;
    public final CompletableFuture<Void> future;
    public final String username;
    public final String password;
    public final String clientId;
    public final boolean isFallbackSupported;

    /**
     * Constructor.
     */
    public CommandRequest(String commandName, byte[] serializedPayload,
        CompletableFuture<Void> future, String username, String password,
        String clientId, boolean isFallbackSupported) {
      this.commandName = commandName;
      this.serializedPayload = serializedPayload;
      this.future = future;
      this.username = username;
      this.password = password;
      this.clientId = clientId;
      this.isFallbackSupported = isFallbackSupported;
    }
  }
}
