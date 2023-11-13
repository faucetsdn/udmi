package daq.pubber;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static udmi.schema.Category.POINTSET_POINT_INVALID;
import static udmi.schema.Category.POINTSET_POINT_INVALID_VALUE;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.daq.mqtt.util.CatchingScheduledThreadPoolExecutor;
import daq.pubber.Pubber.ExtraPointsetEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import udmi.schema.Entry;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointPointsetState;
import udmi.schema.PointsetConfig;
import udmi.schema.PointsetModel;
import udmi.schema.PointsetState;
import udmi.schema.PubberOptions;

/**
 * Helper class to manage the operation of a pointset block.
 */
public class PointsetManager {

  private static final Set<String> BOOLEAN_UNITS = ImmutableSet.of("No-units");
  private static final double DEFAULT_BASELINE_VALUE = 50;
  private static final int MIN_REPORT_MS = 200;
  private static final int DEFAULT_REPORT_SEC = 10;

  private static final Map<String, PointPointsetModel> DEFAULT_POINTS = ImmutableMap.of(
      "recalcitrant_angle", makePointPointsetModel(true, 50, 50, "Celsius"),
      "faulty_finding", makePointPointsetModel(true, 40, 0, "deg"),
      "superimposition_reading", makePointPointsetModel(false)
  );

  private ScheduledFuture<?> periodicSender;
  private PointsetState pointsetState;
  private final ScheduledExecutorService executor = new CatchingScheduledThreadPoolExecutor(1);
  private final AtomicInteger messageDelayMs = new AtomicInteger(DEFAULT_REPORT_SEC * 1000);
  private final ExtraPointsetEvent pointsetEvent = new ExtraPointsetEvent();
  private final Map<String, AbstractPoint> managedPoints = new HashMap<>();
  private final PubberOptions options;
  private final ManagerHost host;
  private int pointsetUpdateCount = -1;

  /**
   * Create a new instance attached to the given host.
   *
   * @param host host for management functions
   */
  public PointsetManager(ManagerHost host) {
    this.host = host;
    this.options = host.getOptions();

    host.update(pointsetState);
  }

  private static PointPointsetModel makePointPointsetModel(boolean writable, int value,
      double tolerance, String units) {
    PointPointsetModel pointMetadata = new PointPointsetModel();
    pointMetadata.writable = writable;
    pointMetadata.baseline_value = value;
    pointMetadata.baseline_tolerance = tolerance;
    pointMetadata.units = units;
    return pointMetadata;
  }

  private static PointPointsetModel makePointPointsetModel(boolean writable) {
    PointPointsetModel pointMetadata = new PointPointsetModel();
    return pointMetadata;
  }

  private static PointPointsetEvent extraPointsetEvent() {
    PointPointsetEvent pointPointsetEvent = new PointPointsetEvent();
    pointPointsetEvent.present_value = 100;
    return pointPointsetEvent;
  }

  private AbstractPoint makePoint(String name, PointPointsetModel point) {
    boolean writable = point.writable != null && point.writable;
    if (BOOLEAN_UNITS.contains(point.units)) {
      return new RandomBoolean(name, writable);
    } else {
      double baselineValue = convertValue(point.baseline_value, DEFAULT_BASELINE_VALUE);
      double baselineTolerance = convertValue(point.baseline_tolerance, baselineValue);
      double min = baselineValue - baselineTolerance;
      double max = baselineValue + baselineTolerance;
      return new RandomPoint(name, writable, min, max, point.units);
    }
  }

  private double convertValue(Object baselineValue, double defaultBaselineValue) {
    if (baselineValue == null) {
      return defaultBaselineValue;
    }
    if (baselineValue instanceof Double) {
      return (double) baselineValue;
    }
    if (baselineValue instanceof Integer) {
      return (double) (int) baselineValue;
    }
    throw new RuntimeException("Unknown value type " + baselineValue.getClass());
  }

  public void setExtraField(String extraField) {
    ifNotNullThen(extraField, field -> pointsetEvent.extraField = field);
  }

  /**
   * Set the underlying static model for this pointset. This is information that would NOT be
   * normally available for a device, but would, e.g. be programmed directly into a device. It's
   * only available here since this is a reference pseudo-device for testing.
   *
   * @param model pointset model
   */
  public void setPointsetModel(PointsetModel model) {
    Map<String, PointPointsetModel> points = ifNotNullGet(model, x -> x.points, DEFAULT_POINTS);

    ifNotNullThen(options.missingPoint,
        x -> requireNonNull(points.remove(x), "missing point not in pointset metadata"));

    points.forEach((name, point) -> addPoint(makePoint(name, point)));

  }

  private void addPoint(AbstractPoint point) {
    managedPoints.put(point.getName(), point);
  }

  private void restorePoint(String pointName) {
    if (pointsetState == null || pointName.equals(options.missingPoint)) {
      return;
    }

    pointsetState.points.put(pointName, ifNotNullGet(managedPoints.get(pointName),
        AbstractPoint::getState, invalidPoint(pointName)));
    pointsetEvent.points.put(pointName, ifNotNullGet(managedPoints.get(pointName),
        AbstractPoint::getData, new PointPointsetEvent()));
  }

  private void suspendPoint(String pointName) {
    pointsetState.points.remove(pointName);
    pointsetEvent.points.remove(pointName);
  }

  private void updatePoints() {
    managedPoints.values().forEach(point -> {
      point.updateData();
      updateState(point);
    });
  }

  private void updateState(AbstractPoint point) {
    String pointName = point.getName();

    if (!pointsetState.points.containsKey(pointName)) {
      return;
    }

    ifNotTrueThen(options.noPointState,
        () -> pointsetState.points.put(pointName, new PointPointsetState()));

    if (point.isDirty()) {
      pointsetState.points.put(pointName, point.getState());
      host.update(pointsetState);
    }
  }

  private PointPointsetState invalidPoint(String pointName) {
    PointPointsetState pointPointsetState = new PointPointsetState();
    pointPointsetState.status = new Entry();
    pointPointsetState.status.category = POINTSET_POINT_INVALID;
    pointPointsetState.status.level = POINTSET_POINT_INVALID_VALUE;
    pointPointsetState.status.message = "Unknown configured point " + pointName;
    pointPointsetState.status.timestamp = getNow();
    return pointPointsetState;
  }

  private void updatePointConfig(AbstractPoint point, PointPointsetConfig pointConfig) {
    ifNotTrueThen(options.noWriteback, () -> {
      point.setConfig(pointConfig);
      updateState(point);
    });
  }

  private void updatePointsetPointsConfig(PointsetConfig config) {
    if (config == null) {
      pointsetState = null;
      host.update((PointsetState) null);
      return;
    }

    ifNullThen(pointsetState, () -> {
      pointsetState = new PointsetState();
      pointsetState.points = new HashMap<>();
      pointsetEvent.points = new HashMap<>();
    });

    Map<String, PointPointsetConfig> points = ofNullable(config.points).orElseGet(HashMap::new);
    managedPoints.forEach((name, point) -> updatePointConfig(point, points.get(name)));
    pointsetState.state_etag = config.state_etag;

    Set<String> configuredPoints = config.points.keySet();
    Set<String> statePoints = pointsetState.points.keySet();
    Set<String> missingPoints = Sets.difference(configuredPoints, statePoints).immutableCopy();
    final Set<String> clearPoints = Sets.difference(statePoints, configuredPoints).immutableCopy();

    missingPoints.forEach(name -> {
      debug("Restoring unknown point " + name);
      restorePoint(name);
    });

    clearPoints.forEach(key -> {
      debug("Clearing extraneous point " + key);
      suspendPoint(key);
    });

    ifNotNullThen(options.extraPoint,
        extraPoint -> pointsetEvent.points.put(extraPoint, extraPointsetEvent()));

    AtomicReference<Entry> maxStatus = new AtomicReference<>();
    statePoints.forEach(
        name -> ifNotNullThen(pointsetState.points.get(name).status, status -> {
          if (maxStatus.get() == null || status.level > maxStatus.get().level) {
            maxStatus.set(status);
          }
        }));

    host.update(pointsetState);
  }

  private void debug(String message) {
    host.debug(message);
  }

  private void info(String message) {
    host.info(message);
  }

  private void error(String message, Throwable e) {
    host.error(message, e);
  }

  void updatePointsetConfig(PointsetConfig pointsetConfig) {
    updateSamplingInterval(pointsetConfig);
    updatePointsetPointsConfig(pointsetConfig);
  }

  private void updateSamplingInterval(PointsetConfig pointsetConfig) {
    boolean hasSampleRate = pointsetConfig != null && pointsetConfig.sample_rate_sec != null;
    int reportInterval = hasSampleRate ? pointsetConfig.sample_rate_sec : DEFAULT_REPORT_SEC;
    int actualInterval = Integer.max(MIN_REPORT_MS, reportInterval * 1000);
    int intervalMs = ifNotNullGet(options.fixedSampleRate, fixed -> fixed * 1000, actualInterval);

    if (periodicSender == null || intervalMs != messageDelayMs.get()) {
      cancelPeriodicSend();
      messageDelayMs.set(intervalMs);
      startPeriodicSend();
    }
  }

  private synchronized void startPeriodicSend() {
    checkState(periodicSender == null);
    int delay = messageDelayMs.get();
    info("Starting executor with send message delay " + delay);
    periodicSender = executor.scheduleAtFixedRate(this::periodicUpdate, delay, delay,
        TimeUnit.MILLISECONDS);
  }

  private synchronized void cancelPeriodicSend() {
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

  private void periodicUpdate() {
    try {
      if (pointsetState != null) {
        updatePoints();
        sendDevicePoints();
      }
    } catch (Exception e) {
      error("Fatal error during execution", e);
    }
  }

  private void sendDevicePoints() {
    if (pointsetUpdateCount % Pubber.MESSAGE_REPORT_INTERVAL == 0) {
      info(format("%s sending test message #%d with %d points",
          getTimestamp(), pointsetUpdateCount, pointsetEvent.points.size()));
    }
    host.publish(pointsetEvent);
  }

}
