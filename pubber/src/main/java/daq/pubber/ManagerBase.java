package daq.pubber;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.getNow;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.daq.mqtt.util.CatchingScheduledThreadPoolExecutor;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import udmi.schema.PubberOptions;

/**
 * Base class for Pubber subsystem managers.
 */
public abstract class ManagerBase {

  public static final int DISABLED_INTERVAL = 0;
  protected static final int DEFAULT_REPORT_SEC = 10;
  protected static final int WAIT_TIME_SEC = 10;
  protected final AtomicInteger sendRateSec = new AtomicInteger(DEFAULT_REPORT_SEC);
  protected final PubberOptions options;
  protected final ManagerHost host;
  private final ScheduledExecutorService executor = new CatchingScheduledThreadPoolExecutor(1);
  protected ScheduledFuture<?> periodicSender;

  public ManagerBase(ManagerHost host, PubberOptions pubberOptions) {
    this.options = pubberOptions;
    this.host = host;
  }

  protected void updateState(Object state) {
    host.update(state);
  }

  protected ScheduledFuture<?> scheduleFuture(Date futureTime, Runnable futureTask) {
    if (executor.isShutdown() || executor.isTerminated()) {
      throw new RuntimeException("Executor shutdown/terminated, not scheduling");
    }
    long delay = futureTime.getTime() - getNow().getTime();
    debug(format("Scheduling future in %dms", delay));
    return executor.schedule(futureTask, delay, TimeUnit.MILLISECONDS);
  }

  protected void debug(String message) {
    host.debug(message);
  }

  protected void info(String message) {
    host.info(message);
  }

  protected void warn(String message) {
    host.warn(message);
  }

  protected void error(String message, Throwable e) {
    host.error(message, e);
  }

  protected void error(String message) {
    host.error(message, null);
  }

  protected void updateInterval(Integer sampleRateSec) {
    int reportInterval = ofNullable(sampleRateSec).orElse(DEFAULT_REPORT_SEC);
    int intervalSec = ofNullable(options.fixedSampleRate).orElse(reportInterval);
    if (periodicSender == null || intervalSec != sendRateSec.get()) {
      cancelPeriodicSend();
      sendRateSec.set(intervalSec);
      startPeriodicSend();
    }
  }

  protected abstract void periodicUpdate();

  protected synchronized void startPeriodicSend() {
    checkState(periodicSender == null);
    int sec = sendRateSec.get();
    warn(format("Starting %s sender with delay %ds", this.getClass().getSimpleName(), sec));
    if (sec != 0) {
      periodicSender = executor.scheduleAtFixedRate(this::periodicUpdate, sec, sec, SECONDS);
    }
  }

  protected synchronized void cancelPeriodicSend() {
    if (periodicSender != null) {
      try {
        warn(format("Terminating %s sender", this.getClass().getSimpleName()));
        periodicSender.cancel(false);
      } catch (Exception e) {
        throw new RuntimeException("While cancelling executor", e);
      } finally {
        periodicSender = null;
      }
    }
  }

  private void stopExecutor() {
    try {
      executor.shutdown();
      if (!executor.awaitTermination(WAIT_TIME_SEC, TimeUnit.SECONDS)) {
        throw new RuntimeException("Failed to shutdown scheduled tasks");
      }
    } catch (Exception e) {
      throw new RuntimeException("While stopping executor", e);
    }
  }


  protected void shutdown() {
    cancelPeriodicSend();
    stopExecutor();
  }
}
