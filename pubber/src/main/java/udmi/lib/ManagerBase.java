package udmi.lib;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.getNow;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.daq.mqtt.util.CatchingScheduledThreadPoolExecutor;
import com.google.udmi.util.SchemaVersion;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import udmi.lib.client.ManagerProvider;
import udmi.schema.Config;
import udmi.schema.DiscoveryState;
import udmi.schema.GatewayState;
import udmi.schema.LocalnetState;
import udmi.schema.PointsetState;
import udmi.schema.PubberConfiguration;
import udmi.schema.PubberOptions;
import udmi.schema.State;
import udmi.schema.SystemState;

/**
 * Base class for Pubber subsystem managers.
 */
public abstract class ManagerBase implements ManagerProvider {

  public static final int DISABLED_INTERVAL = 0;
  protected static final int DEFAULT_REPORT_SEC = 10;
  public static final int WAIT_TIME_SEC = 10;
  protected final AtomicInteger sendRateSec = new AtomicInteger(DEFAULT_REPORT_SEC);
  protected final PubberOptions options;
  protected final ManagerHost host;
  protected final Config deviceConfig = new Config();
  protected final State deviceState = new State();
  protected final ScheduledExecutorService executor = new CatchingScheduledThreadPoolExecutor(1);
  protected final AtomicBoolean stateDirty = new AtomicBoolean();
  protected final String deviceId;
  protected final PubberConfiguration config;
  protected ScheduledFuture<?> periodicSender;

  /**
   * New instance.
   */
  protected ManagerBase(ManagerHost host, PubberConfiguration configuration) {
    config = configuration;
    options = configuration.options;
    deviceId = requireNonNull(configuration.deviceId, "device id not defined");
    this.host = host;
  }

  /**
   * Updates state holder.
   */
  public static void updateStateHolder(State state, Object update) {
    requireNonNull(update, "null update message");
    state.timestamp = getNow();
    state.version = SchemaVersion.CURRENT.key();
    boolean markerClass = update instanceof Class<?>;
    final Object checkValue = markerClass ? null : update;
    final Object checkTarget;
    try {
      checkTarget = markerClass ? ((Class<?>) update).getConstructor().newInstance() : update;
    } catch (Exception e) {
      throw new RuntimeException("Could not create marker instance of class " + update.getClass());
    }
    if (checkTarget instanceof SystemState) {
      state.system = (SystemState) checkValue;
    } else if (checkTarget instanceof PointsetState) {
      state.pointset = (PointsetState) checkValue;
    } else if (checkTarget instanceof LocalnetState) {
      state.localnet = (LocalnetState) checkValue;
    } else if (checkTarget instanceof GatewayState) {
      state.gateway = (GatewayState) checkValue;
    } else if (checkTarget instanceof DiscoveryState) {
      state.discovery = (DiscoveryState) checkValue;
    } else {
      throw new RuntimeException(
          "Unrecognized update type " + checkTarget.getClass().getSimpleName());
    }
  }

  @Override
  public void updateState(Object state) {
    host.update(state);
  }

  /**
   * Schedule a future for the futureTask parameter.
   */
  public ScheduledFuture<?> scheduleFuture(Date futureTime, Runnable futureTask) {
    if (executor.isShutdown() || executor.isTerminated()) {
      throw new RuntimeException("Executor shutdown/terminated, not scheduling");
    }
    long delay = Math.max(futureTime.getTime() - getNow().getTime(), 0);
    debug(format("Scheduling future in %dms", delay));
    return executor.schedule(() -> wrappedRunnable(futureTask), delay, TimeUnit.MILLISECONDS);
  }

  private void wrappedRunnable(Runnable futureTask) {
    try {
      futureTask.run();
    } catch (Exception e) {
      error("Error while executing scheduled future", e);
    }
  }

  public ScheduledFuture<?> schedulePeriodic(int sec, Runnable periodicUpdate) {
    return executor.scheduleAtFixedRate(periodicUpdate, sec, sec, SECONDS);
  }

  public void debug(String message) {
    host.debug(message);
  }

  public void info(String message) {
    host.info(message);
  }

  public void warn(String message) {
    host.warn(message);
  }

  public void error(String message, Throwable e) {
    host.error(message, e);
  }

  public void error(String message) {
    host.error(message, null);
  }

  /**
   * Updates the interval for periodic updates based on the provided sample rate.
   */
  @Override
  public void updateInterval(Integer sampleRateSec) {
    int reportInterval = ofNullable(sampleRateSec).orElse(DEFAULT_REPORT_SEC);
    int intervalSec = ofNullable(options.fixedSampleRate).orElse(reportInterval);
    if (intervalSec < DISABLED_INTERVAL) {
      error(format("Dropping update interval, sample rate %ds is less then DISABLED_INTERVAL",
          intervalSec));
      return;
    }
    if (periodicSender == null || intervalSec != sendRateSec.get()) {
      cancelPeriodicSend();
      sendRateSec.set(intervalSec);
      if (intervalSec > DISABLED_INTERVAL) {
        startPeriodicSend();
      }
    }
  }

  @Override
  public void periodicUpdate() {
    throw new IllegalStateException("No periodic update handler defined");
  }

  protected synchronized void startPeriodicSend() {
    checkState(periodicSender == null);
    int sec = sendRateSec.get();
    warn(format("Starting %s %s sender with delay %ds",
        deviceId, this.getClass().getSimpleName(), sec));
    if (sec != 0) {
      periodicUpdate(); // Do this now to synchronously raise any obvious exceptions.
      periodicSender = schedulePeriodic(sec, this::periodicUpdate);
    }
  }

  protected synchronized void cancelPeriodicSend() {
    if (periodicSender != null) {
      try {
        warn(format("Terminating %s %s sender", deviceId, this.getClass().getSimpleName()));
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

  public void stop() {
    cancelPeriodicSend();
  }

  public void shutdown() {
    cancelPeriodicSend();
    stopExecutor();
  }

  public String getDeviceId() {
    return deviceId;
  }

  public PubberOptions getOptions() {
    return options;
  }

  public PubberConfiguration getConfig() {
    return config;
  }

  public ManagerHost getHost() {
    return host;
  }

  public State getDeviceState() {
    return deviceState;
  }

  public AtomicBoolean getStateDirty() {
    return stateDirty;
  }
}
