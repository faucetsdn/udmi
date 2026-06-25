package com.google.bos.udmi.service.support;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.udmi.util.JsonUtil;
import com.google.bos.udmi.service.pod.ContainerBase;
import java.io.BufferedReader;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import udmi.schema.EndpointConfiguration;

/**
 * Provider that links directly to a mosquitto broker using the native
 * Mosquitto Dynamic Security plugin JSON API over MQTT.
 */
public class MosquittoBroker extends ContainerBase implements ConnectionBroker {

  private static final String REVOKE_PASSWORD = "--";
  private static final String DEFAULT_MOSQUITTO_LOG_PATH = "/var/log/mosquitto/mosquitto.log";
  private static final Pattern LOG_MATCHER =
      Pattern.compile("([0-9]+): (\\S+) (\\S+) (\\S+) (\\S+) (.*)");
  private static final Pattern PUBLISH_MATCHER =
      Pattern.compile("\\(([d01,qr ]+), m([0-9]+), '(\\S+)', .*\\)");
  private static final Pattern PUBACK_MATCHER = Pattern.compile("\\((m|Mid: )([0-9]+), \\S+\\)");
  private final ContainerBase container;
  private final EndpointConfiguration endpointConfig;
  private final boolean disableLogging;
  private final Object tailLock = new Object();
  private Process tailProcess;

  private MosquittoDynamicSecurityService dynSecService;
  private final ObjectMapper objectMapper = JsonUtil.OBJECT_MAPPER;

  /**
   * Create a new broker connection provider.
   */
  public MosquittoBroker(ContainerBase container, EndpointConfiguration endpointConfig) {
    this(container, endpointConfig, false);
  }

  /**
   * Create a new broker connection provider with logging controls.
   */
  public MosquittoBroker(ContainerBase container, EndpointConfiguration endpointConfig,
      boolean disableLogging) {
    this.container = container;
    this.endpointConfig = endpointConfig;
    this.disableLogging = disableLogging;
    if (!disableLogging) {
      File logFile = new File(getMosquittoLogPath());
      if (!logFile.canRead()) {
        throw new RuntimeException(
            "Mosquitto log file is not readable: " + logFile.getAbsolutePath());
      }
    }
  }

  private String getMosquittoLogPath() {
    return ofNullable(System.getenv("MOSQUITTO_LOG_PATH")).orElse(DEFAULT_MOSQUITTO_LOG_PATH);
  }

  private void consumeLogs(String clientPrefix, Consumer<BrokerEvent> eventConsumer) {
    info("Starting log consumer for " + clientPrefix);
    mosquctlLog(clientPrefix, eventConsumer);
  }

  private void consumeStream(BufferedReader reader, Consumer<String> consumer) {
    Thread thread = new Thread(() -> {
      String line;
      try {
        while ((line = reader.readLine()) != null) {
          consumer.accept(line);
        }
      } catch (Exception e) {
        warn("Exception consuming stream: " + friendlyStackTrace(e));
        consumer.accept(e.getMessage());
      }
    });
    thread.setDaemon(true);
    thread.start();
  }

  private synchronized MosquittoDynamicSecurityService getDynSecService() {
    if (dynSecService == null) {
      dynSecService = new MosquittoDynamicSecurityService(endpointConfig);
    }
    return dynSecService;
  }

  private CompletableFuture<udmi.schema.MosquittoClientResponse> enqueueCommandInternal(
      String commandName, Map<String, Object> cmd) {
    try {
      byte[] bytes = objectMapper.writeValueAsBytes(cmd);
      CompletableFuture<udmi.schema.MosquittoClientResponse> future = new CompletableFuture<>();
      return getDynSecService().enqueueCommand(
          new MosquittoDynamicSecurityService.CommandRequest(commandName, bytes, future));
    } catch (Exception e) {
      CompletableFuture<udmi.schema.MosquittoClientResponse> future = new CompletableFuture<>();
      future.completeExceptionally(e);
      return future;
    }
  }

  private CompletableFuture<Void> enqueueCommand(String commandName, Map<String, Object> cmd) {
    return enqueueCommandWithRetry(commandName, cmd, 0).thenApply(v -> null);
  }

  private CompletableFuture<udmi.schema.MosquittoClientResponse> enqueueCommandWithRetry(
      String commandName, Map<String, Object> cmd, int retryCount) {
    return enqueueCommandInternal(commandName, cmd)
        .exceptionallyCompose(ex -> {
          Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
          String msg = cause.getMessage();
          if (msg != null && msg.contains("Internal error") && retryCount < 20) {
            long delay = 250;
            warn("Command %s failed with Internal error, retrying in %dms (attempt %d/20)...",
                commandName, delay, retryCount + 1);
            return CompletableFuture.runAsync(() -> {},
                CompletableFuture.delayedExecutor(
                    delay, java.util.concurrent.TimeUnit.MILLISECONDS))
                .thenCompose(v -> enqueueCommandWithRetry(commandName, cmd, retryCount + 1));
          }
          return CompletableFuture.failedFuture(ex);
        });
  }

  private boolean isReflectRegistry(String id) {
    return id != null && id.contains("/" + reflectRegistry + "/");
  }

  private CompletableFuture<Void> deprovisionClient(
      String clientId, String clientUser, String roleName) {
    CompletableFuture<Void> f1 = deleteClient(clientUser);
    CompletableFuture<Void> f2 = deleteRole(roleName);
    return CompletableFuture.allOf(f1, f2)
        .thenRun(() -> info("Device %s deleted correctly.", clientId));
  }

  private CompletableFuture<Void> provisionClient(String clientId, String clientPass) {
    String clientUser = clientId;
    String roleName = "role_" + clientId.replace("/", "_");

    if ("--".equals(clientPass)) {
      return deprovisionClient(clientId, clientUser, roleName);
    } else {
      CompletableFuture<Void> clientFuture =
          createClientWithFallback(clientUser, clientPass, clientId);
      CompletableFuture<Void> roleFuture = createRole(roleName);

      return CompletableFuture.allOf(clientFuture, roleFuture)
          .thenCompose(v -> ensureClientHasRole(clientUser, roleName))
          .thenCompose(v -> {
            CompletableFuture<Void> a1 =
                addRoleAcl(roleName, "subscribePattern", clientId + "/config", true);
            CompletableFuture<Void> a2 =
                addRoleAcl(roleName, "subscribePattern", clientId + "/commands/#", true);
            CompletableFuture<Void> a3 =
                addRoleAcl(roleName, "subscribePattern", clientId + "/errors", true);
            CompletableFuture<Void> a4 =
                addRoleAcl(roleName, "publishClientSend", clientId + "/events/#", true);
            CompletableFuture<Void> a5 =
                addRoleAcl(roleName, "publishClientSend", clientId + "/state", true);
            if (isReflectRegistry(clientId)) {
              CompletableFuture<Void> a6 =
                  addRoleAcl(roleName, "publishClientSend", clientId + "/reflect", true);
              return CompletableFuture.allOf(a1, a2, a3, a4, a5, a6);
            }
            return CompletableFuture.allOf(a1, a2, a3, a4, a5);
          }).thenRun(() -> info("Device %s registered correctly.", clientId));
    }
  }

  private CompletableFuture<Void> setClientPassword(String clientUser, String clientPass) {
    Map<String, Object> cmd = new HashMap<>();
    cmd.put("command", "setClientPassword");
    cmd.put("username", clientUser);
    cmd.put("password", clientPass);
    return enqueueCommand("setClientPassword", cmd);
  }

  private CompletableFuture<Void> addRoleAcl(
      String roleName, String type, String pattern, boolean allow) {
    Map<String, Object> cmd = new HashMap<>();
    cmd.put("command", "addRoleACL");
    cmd.put("rolename", roleName);
    cmd.put("acltype", type);
    cmd.put("topic", pattern);
    cmd.put("allow", allow);
    return enqueueCommand("addRoleACL", cmd);
  }

  private CompletableFuture<Void> deleteClient(String clientUser) {
    Map<String, Object> cmd = new HashMap<>();
    cmd.put("command", "deleteClient");
    cmd.put("username", clientUser);
    return enqueueCommand("deleteClient", cmd);
  }

  private CompletableFuture<Void> deleteRole(String roleName) {
    Map<String, Object> cmd = new HashMap<>();
    cmd.put("command", "deleteRole");
    cmd.put("rolename", roleName);
    return enqueueCommand("deleteRole", cmd);
  }

  private CompletableFuture<Void> createClient(
      String clientUser, String clientPass, String clientId) {
    Map<String, Object> cmd = new HashMap<>();
    cmd.put("command", "createClient");
    cmd.put("username", clientUser);
    cmd.put("password", clientPass);
    if (clientId != null) {
      cmd.put("clientid", clientId);
    }
    return enqueueCommand("createClient", cmd);
  }

  private CompletableFuture<Void> modifyClient(
      String clientUser, String clientPass, String clientId) {
    Map<String, Object> cmd = new HashMap<>();
    cmd.put("command", "modifyClient");
    cmd.put("username", clientUser);
    if (clientPass != null) {
      cmd.put("password", clientPass);
    }
    if (clientId != null) {
      cmd.put("clientid", clientId);
    }
    return enqueueCommand("modifyClient", cmd);
  }

  private CompletableFuture<Void> createClientWithFallback(
      String clientUser, String clientPass, String clientId) {
    return createClient(clientUser, clientPass, clientId)
        .exceptionallyCompose(ex -> {
          Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
          String msg = cause.getMessage();
          if (msg != null && msg.contains("exists")) {
            info("Client %s already exists. Falling back to modifyClient...", clientUser);
            return modifyClient(clientUser, clientPass, clientId);
          }
          return CompletableFuture.failedFuture(ex);
        });
  }

  private CompletableFuture<Void> createRole(String roleName) {
    Map<String, Object> cmd = new HashMap<>();
    cmd.put("command", "createRole");
    cmd.put("rolename", roleName);
    return enqueueCommand("createRole", cmd);
  }

  private CompletableFuture<udmi.schema.MosquittoClientResponse> getClient(String clientUser) {
    Map<String, Object> cmd = new HashMap<>();
    cmd.put("command", "getClient");
    cmd.put("username", clientUser);
    return enqueueCommandWithRetry("getClient", cmd, 0);
  }

  private CompletableFuture<Void> ensureClientHasRole(String clientUser, String roleName) {
    return getClient(clientUser).thenCompose(resp -> {
      boolean hasRole = false;
      if (resp != null && resp.data != null && resp.data.client != null
          && resp.data.client.roles != null) {
        hasRole = resp.data.client.roles.stream().anyMatch(r -> roleName.equals(r.rolename));
      }
      if (hasRole) {
        info("Client %s already has role %s, skipping addClientRole.", clientUser, roleName);
        return CompletableFuture.completedFuture(null);
      }
      return addClientRole(clientUser, roleName);
    }).exceptionallyCompose(ex -> {
      // If getClient fails, fallback to simply adding the role.
      return addClientRole(clientUser, roleName);
    });
  }

  private CompletableFuture<Void> addClientRole(String clientUser, String roleName) {
    Map<String, Object> cmd = new HashMap<>();
    cmd.put("command", "addClientRole");
    cmd.put("username", clientUser);
    cmd.put("rolename", roleName);
    return enqueueCommand("addClientRole", cmd);
  }

  private void mosquctlLog(String clientPrefix, Consumer<BrokerEvent> eventConsumer) {
    if (disableLogging) {
      info("Mosquitto logging disabled, skipping log consumer for prefix %s", clientPrefix);
      return;
    }
    synchronized (tailLock) {
      try {
        info("Starting log consumer for prefix %s", clientPrefix);
        ProcessBuilder pb = new ProcessBuilder("tail", "-f", getMosquittoLogPath());
        if (tailProcess != null) {
          tailProcess.destroy();
        }
        tailProcess = pb.start();
        Process exec = tailProcess;
        consumeStream(exec.errorReader(), line -> warn("log error: " + line));
        consumeStream(exec.inputReader(), line -> {
          BrokerEvent event = parseLogLine(line);
          if (event != null && event.clientId != null && event.clientId.startsWith(clientPrefix)) {
            eventConsumer.accept(event);
          }
        });
      } catch (Exception e) {
        throw new RuntimeException("While starting log consumer", e);
      } finally {
        info("Completed log consumer setup");
      }
    }
  }

  private BrokerEvent parseLogLine(String line) {
    BrokerEvent brokerEvent = new BrokerEvent();
    try {
      if (line == null) {
        return null;
      }

      Matcher matcher = LOG_MATCHER.matcher(line);
      if (!matcher.matches()) {
        return null;
      }

      brokerEvent.timestamp = new Date(Long.parseLong(matcher.group(1)) * 1000);
      brokerEvent.operation = catchToElse(() -> Operation.valueOf(matcher.group(3)),
          Operation.UNKNOWN);
      brokerEvent.clientId = matcher.group(5);
      brokerEvent.direction = catchToNull(() -> Direction.valueOf(matcher.group(2)));
      String detail = matcher.group(6);
      return switch (brokerEvent.operation) {
        case PUBLISH -> parsePublishEvent(brokerEvent, detail);
        case PUBACK -> parsePubackEvent(brokerEvent, detail);
        default -> null;
      };
    } catch (Exception e) {
      brokerEvent.detail = friendlyStackTrace(e);
      brokerEvent.operation = Operation.EXCEPTION;
      return brokerEvent;
    }
  }

  private BrokerEvent parsePubackEvent(BrokerEvent brokerEvent, String detail) {
    Matcher matcher = PUBACK_MATCHER.matcher(detail);
    checkState(matcher.matches(), "puback detail does not match regex " + detail);
    brokerEvent.mesageId = Integer.parseInt(matcher.group(2));
    return brokerEvent;
  }

  private BrokerEvent parsePublishEvent(BrokerEvent brokerEvent, String detail) {
    Matcher matcher = PUBLISH_MATCHER.matcher(detail);
    checkState(matcher.matches(), "publish detail does not match regex " + detail);
    brokerEvent.mesageId = Integer.parseInt(matcher.group(2));
    brokerEvent.detail = matcher.group(3);
    return brokerEvent;
  }

  @Override
  public Future<Void> addEventListener(String clientPrefix,
      Consumer<BrokerEvent> eventConsumer) {
    return CompletableFuture.runAsync(() -> consumeLogs(clientPrefix, eventConsumer));
  }

  @Override
  public CompletableFuture<Void> authorize(String clientId, String password) {
    return provisionClient(clientId, ofNullable(password).orElse(REVOKE_PASSWORD));
  }

  @Override
  public CompletableFuture<Void> bindGateway(String gatewayId, String deviceId) {
    String roleName = "role_" + gatewayId.replace("/", "_");

    CompletableFuture<Void> a1 =
        addRoleAcl(roleName, "subscribePattern", deviceId + "/config", true);
    CompletableFuture<Void> a2 =
        addRoleAcl(roleName, "subscribePattern", deviceId + "/commands/#", true);
    CompletableFuture<Void> a3 =
        addRoleAcl(roleName, "subscribePattern", deviceId + "/errors", true);
    CompletableFuture<Void> a4 =
        addRoleAcl(roleName, "publishClientSend", deviceId + "/events/#", true);
    CompletableFuture<Void> a5 =
        addRoleAcl(roleName, "publishClientSend", deviceId + "/state", true);
    CompletableFuture<Void> a6 =
        addRoleAcl(roleName, "publishClientSend", deviceId + "/attach", true);

    if (isReflectRegistry(deviceId)) {
      CompletableFuture<Void> a7 =
          addRoleAcl(roleName, "publishClientSend", deviceId + "/reflect", true);
      return CompletableFuture.allOf(a1, a2, a3, a4, a5, a6, a7);
    }

    return CompletableFuture.allOf(a1, a2, a3, a4, a5, a6);
  }

  @Override
  public CompletableFuture<Void> unbindGateway(String gatewayId, String deviceId) {
    info("Unbind device Id: %s from gateway Id: %s", deviceId, gatewayId);
    String roleName = "role_" + gatewayId.replace("/", "_");

    CompletableFuture<Void> a1 =
        removeRoleAcl(roleName, "subscribePattern", deviceId + "/config");
    CompletableFuture<Void> a2 =
        removeRoleAcl(roleName, "subscribePattern", deviceId + "/commands/#");
    CompletableFuture<Void> a3 =
        removeRoleAcl(roleName, "subscribePattern", deviceId + "/errors");
    CompletableFuture<Void> a4 =
        removeRoleAcl(roleName, "publishClientSend", deviceId + "/events/#");
    CompletableFuture<Void> a5 =
        removeRoleAcl(roleName, "publishClientSend", deviceId + "/state");
    CompletableFuture<Void> a6 =
        removeRoleAcl(roleName, "publishClientSend", deviceId + "/attach");

    if (isReflectRegistry(deviceId)) {
      CompletableFuture<Void> a7 =
          removeRoleAcl(roleName, "publishClientSend", deviceId + "/reflect");
      return CompletableFuture.allOf(a1, a2, a3, a4, a5, a6, a7);
    }

    return CompletableFuture.allOf(a1, a2, a3, a4, a5, a6);
  }

  private CompletableFuture<Void> removeRoleAcl(String roleName, String type, String pattern) {
    Map<String, Object> cmd = new HashMap<>();
    cmd.put("command", "removeRoleACL");
    cmd.put("rolename", roleName);
    cmd.put("acltype", type);
    cmd.put("topic", pattern);
    return enqueueCommand("removeRoleACL", cmd);
  }

  @Override
  public void shutdown() {
    synchronized (tailLock) {
      if (tailProcess != null) {
        tailProcess.destroy();
        tailProcess = null;
      }
    }
    synchronized (this) {
      if (dynSecService != null) {
        dynSecService.shutdown();
      }
    }
    super.shutdown();
  }
}
