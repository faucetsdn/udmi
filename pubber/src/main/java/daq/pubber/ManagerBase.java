package daq.pubber;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.daq.mqtt.util.CatchingScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import udmi.schema.PubberOptions;

/**
 * Base class for Pubber subsystem managers.
 */
public abstract class ManagerBase {

  public static final int DISABLED_INTERVAL = 0;
  protected static final int DEFAULT_REPORT_SEC = 10;
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
    String simpleName = this.getClass().getSimpleName();
    info(format("Setting %s sender with delay %ds", simpleName, sec));
    if (sec != 0) {
      periodicSender = executor.scheduleAtFixedRate(this::periodicUpdate, sec, sec, SECONDS);
    }
  }

  protected synchronized void cancelPeriodicSend() {
    if (periodicSender != null) {
      try {
        periodicSender.cancel(false);
      } catch (Exception e) {
        throw new RuntimeException("While cancelling executor", e);
      } finally {
        periodicSender = null;
      }
    }
  }
}
