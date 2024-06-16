package com.google.bos.udmi.service.support;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.pod.ContainerBase;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Provider that links directly to a mosquitto broker.
 */
public class MosquittoBroker extends ContainerBase implements ConnectionBroker {

  private static final String UDMI_ROOT = System.getenv("UDMI_ROOT");
  private static final String MOSQUCTL_CLIENT_FMT = UDMI_ROOT + "/bin/mosquctl_client %s %s";
  private static final String MOSQUCTL_LOG_FMT = UDMI_ROOT + "/bin/mosquctl_log %s";
  private static final long EXEC_TIMEOUT_SEC = 10;
  private static final String REVOKE_PASSWORD = "--";
  private final ContainerBase container;

  public MosquittoBroker(ContainerBase container) {
    this.container = container;
  }

  @Override
  public void authorize(String clientId, String password) {
    mosquctlClient(clientId, ofNullable(password).orElse(REVOKE_PASSWORD));
  }

  @Override
  public Future<Void> addEventListener(String clientPrefix,
      Consumer<ConnectionEvent> eventConsumer) {
    return CompletableFuture.runAsync(() -> consumeLogs(clientPrefix, eventConsumer));
  }

  private void consumeLogs(String clientPrefix, Consumer<ConnectionEvent> eventConsumer) {
    info("Starting log consumer for " + clientPrefix);
  }

  private ConnectionEvent parseLogLine(String clientPrefix, String line) {
    if (line == null) {
      return null;
    }
    ConnectionEvent connectionEvent = new ConnectionEvent();
    connectionEvent.clientId = clientPrefix;
    connectionEvent.detail = line;
    return connectionEvent;
  }

  private void mosquctlClient(String clientId, String clientPass) {
    String cmd = format(MOSQUCTL_CLIENT_FMT, clientId, clientPass);
    synchronized (MosquittoBroker.class) {
      try {
        container.info("Executing command %s", cmd);
        Process exec = Runtime.getRuntime().exec(cmd);
        exec.waitFor(EXEC_TIMEOUT_SEC, TimeUnit.SECONDS);
        exec.errorReader().lines().forEach(container::info);
        exec.inputReader().lines().forEach(container::info);
        int exitValue = exec.exitValue();
        checkState(exitValue == 0, "exit return code " + exitValue);
      } catch (Exception e) {
        throw new RuntimeException("While executing " + cmd, e);
      }
    }
  }
}
