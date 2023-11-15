package daq.pubber;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

import com.google.daq.mqtt.util.CatchingScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import udmi.schema.PubberOptions;

/**
 * Base class for Pubber subsystem managers.
 */
public abstract class ManagerBase {

  protected static final int MIN_REPORT_MS = 200;
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

  protected abstract void periodicUpdate();

  protected synchronized void startPeriodicSend() {
    checkState(periodicSender == null);
    int delay = sendRateSec.get();
    if (delay == 0) {
      info(format("Disabling %s sender because delay is 0", this.getClass().getSimpleName()));
    } else {
      info(format("Enabling %s sender because with delay %d", this.getClass().getSimpleName(),
          delay));
      periodicSender = executor.scheduleAtFixedRate(this::periodicUpdate, delay, delay,
          TimeUnit.MILLISECONDS);
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
