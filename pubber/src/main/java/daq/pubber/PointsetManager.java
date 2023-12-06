package daq.pubber;

import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static udmi.schema.Category.POINTSET_POINT_INVALID;
import static udmi.schema.Category.POINTSET_POINT_INVALID_VALUE;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import udmi.schema.Entry;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointPointsetState;
import udmi.schema.PointsetConfig;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetModel;
import udmi.schema.PointsetState;
import udmi.schema.PubberConfiguration;

/**
 * Helper class to manage the operation of a pointset block.
 */
public class PointsetManager extends ManagerBase {

  private static final Set<String> BOOLEAN_UNITS = ImmutableSet.of("No-units");
  private static final double DEFAULT_BASELINE_VALUE = 50;

  private static final Map<String, PointPointsetModel> DEFAULT_POINTS = ImmutableMap.of(
      "recalcitrant_angle", makePointPointsetModel(true, 50, 50, "Celsius"),
      "faulty_finding", makePointPointsetModel(true, 40, 0, "deg"),
      "superimposition_reading", makePointPointsetModel(false)
  );
  private final ExtraPointsetEvent pointsetEvent = new ExtraPointsetEvent();
  private final Map<String, AbstractPoint> managedPoints = new HashMap<>();
  private int pointsetUpdateCount = -1;
  private PointsetState pointsetState;

  /**
   * Create a new instance attached to the given host.
   */
  public PointsetManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
    setExtraField(options.extraField);
    updateState();
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

  private void updateState() {
    updateState(ofNullable((Object) pointsetState).orElse(PointsetState.class));
  }

  private void updateState(AbstractPoint point) {
    String pointName = point.getName();

    if (!pointsetState.points.containsKey(pointName)) {
      return;
    }

    if (point.isDirty()) {
      PointPointsetState state = point.getState(); // Always call to clear the dirty bit
      PointPointsetState useState =
          ifTrueGet(options.noPointState, PointPointsetState::new, () -> state);
      pointsetState.points.put(pointName, useState);
      updateState();
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
      updateState();
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

    updateState();
  }

  void updateConfig(PointsetConfig config) {
    updateInterval(ifNotNullGet(config, c -> c.sample_rate_sec, DISABLED_INTERVAL));
    updatePointsetPointsConfig(config);
  }

  @Override
  protected void periodicUpdate() {
    try {
      if (pointsetState != null) {
        pointsetUpdateCount++;
        updatePoints();
        sendDevicePoints();
      }
    } catch (Exception e) {
      error("Fatal error during execution", e);
    }
  }

  private void sendDevicePoints() {
    if (pointsetUpdateCount % Pubber.MESSAGE_REPORT_INTERVAL == 0) {
      info(format("%s sending %s message #%d with %d points",
          getTimestamp(), deviceId, pointsetUpdateCount, pointsetEvent.points.size()));
    }
    host.publish(pointsetEvent);
  }

  static class ExtraPointsetEvent extends PointsetEvent {

    // This extraField exists only to trigger schema parsing errors.
    public Object extraField;
  }
}
