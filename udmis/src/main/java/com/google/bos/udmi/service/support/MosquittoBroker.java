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
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provider that links directly to a mosquitto broker.
 */
public class MosquittoBroker extends ContainerBase implements ConnectionBroker {

  private static final String UDMI_ROOT = System.getenv("UDMI_ROOT");
  private static final String MOSQUCTL_CLIENT_FMT = UDMI_ROOT + "/bin/mosquctl_client %s %s";
  private static final String MOSQUCTL_LOG_FMT = UDMI_ROOT + "/bin/mosquctl_log %s";
  private static final long EXEC_TIMEOUT_SEC = 10;
  private static final String REVOKE_PASSWORD = "--";
  private static final Pattern LOG_MATCHER =
      Pattern.compile("([0-9]+): (\\S+) (\\S+) (\\S+) (\\S+) (.*)");
  private static final Pattern PUBLISH_MATCHER =
      Pattern.compile("\\(([d01,qr ]+), m([0-9]+), '(\\S+)', .*\\)");
  private static final Pattern PUBACK_MATCHER = Pattern.compile("\\((m|Mid: )([0-9]+), \\S+\\)");
  private final ContainerBase container;

  public MosquittoBroker(ContainerBase container) {
    this.container = container;
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

  private void mosquctlClient(String clientId, String clientPass) {
    String cmd = format(MOSQUCTL_CLIENT_FMT, clientId, clientPass);
    synchronized (MosquittoBroker.class) {
      try {
        info("Executing command %s", cmd);
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

  private void mosquctlLog(String clientPrefix, Consumer<BrokerEvent> eventConsumer) {
    String cmd = format(MOSQUCTL_LOG_FMT, clientPrefix);
    synchronized (MosquittoBroker.class) {
      try {
        info("Starting log consumer %s", cmd);
        Process exec = Runtime.getRuntime().exec(cmd);
        consumeStream(exec.errorReader(), line -> warn("log error: " + line));
        consumeStream(exec.inputReader(), line -> ifNotNullThen(parseLogLine(line), eventConsumer));
      } catch (Exception e) {
        throw new RuntimeException("While executing " + cmd, e);
      } finally {
        info("Completed log consumer");
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
}
