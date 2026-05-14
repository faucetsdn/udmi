package com.google.bos.udmi.service.support;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.pod.ContainerBase;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import udmi.schema.EndpointConfiguration;

/**
 * NOTE: The topic structure used in ACLs in this file is hardcoded to use the deviceId
 * directly (e.g., deviceId/config, deviceId/commands, etc.) instead of using properties
 * from the endpoint configuration.
 */

/**
 * Provider that links directly to a mosquitto broker.
 */
public class MosquittoBroker extends ContainerBase implements ConnectionBroker {


  private static final long EXEC_TIMEOUT_SEC = 10;
  private static final String REVOKE_PASSWORD = "--";
  private static final Pattern LOG_MATCHER =
      Pattern.compile("([0-9]+): (\\S+) (\\S+) (\\S+) (\\S+) (.*)");
  private static final Pattern PUBLISH_MATCHER =
      Pattern.compile("\\(([d01,qr ]+), m([0-9]+), '(\\S+)', .*\\)");
  private static final Pattern PUBACK_MATCHER = Pattern.compile("\\((m|Mid: )([0-9]+), \\S+\\)");
  private final ContainerBase container;
  private final EndpointConfiguration endpointConfig;

  public MosquittoBroker(ContainerBase container, EndpointConfiguration endpointConfig) {
    this.container = container;
    this.endpointConfig = endpointConfig;
  }

  private List<String> buildCommandPrefix() {
    List<String> cmd = new ArrayList<>();
    cmd.add("mosquitto_ctrl");
    
    if (endpointConfig.hostname != null) {
      cmd.add("-h");
      cmd.add(endpointConfig.hostname);
    }
    if (endpointConfig.port != null) {
      cmd.add("-p");
      cmd.add(endpointConfig.port.toString());
    }
    if (endpointConfig.auth_provider != null && endpointConfig.auth_provider.basic != null) {
      if (endpointConfig.auth_provider.basic.username != null) {
        cmd.add("-u");
        cmd.add(endpointConfig.auth_provider.basic.username);
      }
      if (endpointConfig.auth_provider.basic.password != null) {
        cmd.add("-P");
        cmd.add(endpointConfig.auth_provider.basic.password);
      }
    }
    
    if (endpointConfig.ca_file != null) {
      cmd.add("--cafile");
      cmd.add(endpointConfig.ca_file);
    }
    if (endpointConfig.cert_file != null) {
      cmd.add("--cert");
      cmd.add(endpointConfig.cert_file);
    }
    if (endpointConfig.key_file != null) {
      cmd.add("--key");
      cmd.add(endpointConfig.key_file);
    }
    
    cmd.add("dynsec");
    return cmd;
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

  private void executeCommand(List<String> cmd) {
    synchronized (MosquittoBroker.class) {
      try {
        info("Executing command %s", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process exec = pb.start();
        exec.waitFor(EXEC_TIMEOUT_SEC, TimeUnit.SECONDS);
        exec.errorReader().lines().forEach(container::info);
        exec.inputReader().lines().forEach(container::info);
        int exitValue = exec.exitValue();
        checkState(exitValue == 0, "exit return code " + exitValue);
      } catch (Exception e) {
        throw new RuntimeException("While executing " + String.join(" ", cmd), e);
      }
    }
  }

  private void mosquctlClient(String clientId, String clientPass) {
    String clientUser = clientId;
    String roleName = "role_" + clientId.replace("/", "_");

    deleteClient(clientUser);
    deleteRole(roleName);

    if (!"--".equals(clientPass)) {
      createClient(clientUser, clientPass, clientId);
      createRole(roleName);
      addClientRole(clientUser, roleName);

      addRoleAcl(roleName, "subscribePattern", clientId + "/config", "allow");
      addRoleAcl(roleName, "subscribePattern", clientId + "/commands", "allow");
      addRoleAcl(roleName, "subscribePattern", clientId + "/errors", "allow");
      addRoleAcl(roleName, "publishClientSend", clientId + "/events/#", "allow");
      addRoleAcl(roleName, "publishClientSend", clientId + "/state", "allow");
      
      info("Device %s registered correctly.", clientId);
    } else {
      info("Device %s deleted correctly.", clientId);
    }
  }

  private void addRoleAcl(String roleName, String type, String pattern, String allow) {
    List<String> cmd = new ArrayList<>(buildCommandPrefix());
    cmd.add("addRoleACL");
    cmd.add(roleName);
    cmd.add(type);
    cmd.add(pattern);
    cmd.add(allow);
    executeCommand(cmd);
  }

  private void deleteClient(String clientUser) {
    List<String> cmd = new ArrayList<>(buildCommandPrefix());
    cmd.add("deleteClient");
    cmd.add(clientUser);
    try {
      executeCommand(cmd);
    } catch (Exception e) {
      warn("Ignore error deleting client: " + e.getMessage());
    }
  }

  private void deleteRole(String roleName) {
    List<String> cmd = new ArrayList<>(buildCommandPrefix());
    cmd.add("deleteRole");
    cmd.add(roleName);
    try {
      executeCommand(cmd);
    } catch (Exception e) {
      warn("Ignore error deleting role: " + e.getMessage());
    }
  }

  private void createClient(String clientUser, String clientPass, String clientId) {
    List<String> cmd = new ArrayList<>(buildCommandPrefix());
    cmd.add("createClient");
    cmd.add(clientUser);
    cmd.add("-p");
    cmd.add(clientPass);
    cmd.add("-c");
    cmd.add(clientId);
    executeCommand(cmd);
  }

  private void createRole(String roleName) {
    List<String> cmd = new ArrayList<>(buildCommandPrefix());
    cmd.add("createRole");
    cmd.add(roleName);
    try {
      executeCommand(cmd);
    } catch (Exception e) {
      warn("Ignore error creating role: " + e.getMessage());
    }
  }

  private void addClientRole(String clientUser, String roleName) {
    List<String> cmd = new ArrayList<>(buildCommandPrefix());
    cmd.add("addClientRole");
    cmd.add(clientUser);
    cmd.add(roleName);
    try {
      executeCommand(cmd);
    } catch (Exception e) {
      warn("Ignore error adding client role: " + e.getMessage());
    }
  }

  private void mosquctlLog(String clientPrefix, Consumer<BrokerEvent> eventConsumer) {
    synchronized (MosquittoBroker.class) {
      try {
        info("Starting log consumer for prefix %s", clientPrefix);
        ProcessBuilder pb = new ProcessBuilder("tail", "-f", "/var/log/mosquitto/mosquitto.log");
        Process exec = pb.start();
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
  public void authorize(String clientId, String password) {
    mosquctlClient(clientId, ofNullable(password).orElse(REVOKE_PASSWORD));
  }

  @Override
  public void bindGateway(String gatewayId, String deviceId) {
    String roleName = "role_" + gatewayId.replace("/", "_");

    createRole(roleName);
    addClientRole(gatewayId, roleName);

    // add ACLs
    addRoleAcl(roleName, "subscribePattern", deviceId + "/config", "allow");
    addRoleAcl(roleName, "subscribePattern", deviceId + "/commands", "allow");
    addRoleAcl(roleName, "subscribePattern", deviceId + "/errors", "allow");
    addRoleAcl(roleName, "publishClientSend", deviceId + "/events/#", "allow");
    addRoleAcl(roleName, "publishClientSend", deviceId + "/state", "allow");
    addRoleAcl(roleName, "publishClientSend", deviceId + "/attach", "allow");
  }

  @Override
  public void unbindGateway(String gatewayId, String deviceId) {
    info("Unbind device Id: %s from gateway Id: %s", deviceId, gatewayId);
    String roleName = "role_" + gatewayId.replace("/", "_");

    removeRoleAcl(roleName, "subscribePattern", deviceId + "/config");
    removeRoleAcl(roleName, "subscribePattern", deviceId + "/commands");
    removeRoleAcl(roleName, "subscribePattern", deviceId + "/errors");
    removeRoleAcl(roleName, "publishClientSend", deviceId + "/events/#");
    removeRoleAcl(roleName, "publishClientSend", deviceId + "/state");
    removeRoleAcl(roleName, "publishClientSend", deviceId + "/attach");
  }

  private void removeRoleAcl(String roleName, String type, String pattern) {
    List<String> cmd = new ArrayList<>(buildCommandPrefix());
    cmd.add("removeRoleACL");
    cmd.add(roleName);
    cmd.add(type);
    cmd.add(pattern);
    try {
      executeCommand(cmd);
    } catch (Exception e) {
      warn("Ignore error removing role ACL: " + e.getMessage());
    }
  }
}
