package daq.pubber;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.Optional.ofNullable;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import udmi.lib.client.PointsetManager;
import udmi.lib.intf.AbstractPoint;
import udmi.lib.intf.ManagerHost;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointPointsetState;
import udmi.schema.PointPointsetState.Value_state;
import udmi.schema.PointsetConfig;
import udmi.schema.PointsetModel;
import udmi.schema.PointsetState;
import udmi.schema.PubberConfiguration;

/**
 * Helper class to manage the operation of a pointset block.
 */
public class PubberPointsetManager extends PubberManager implements PointsetManager {

  private static final Set<String> BOOLEAN_UNITS = ImmutableSet.of("No-units");

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
  public PubberPointsetManager(ManagerHost host, PubberConfiguration configuration) {
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

  private AbstractPoint makePoint(String name, PointPointsetModel point) {
    if (BOOLEAN_UNITS.contains(point.units)) {
      return new RandomBoolean(name, point);
    } else {
      return new RandomPoint(name, point);
    }
  }

  @Override
  public PointPointsetState getPointState(AbstractPoint point) {
    PointPointsetState pointState = PointsetManager.super.getPointState(point);
    // Tweak for testing: erroneously apply an applied state here.
    ifTrueThen(point.getName().equals(options.extraPoint),
        () -> pointState.value_state = ofNullable(pointState.value_state).orElse(
            Value_state.APPLIED));
    return ifTrueGet(options.noPointState, PointPointsetState::new, pointState);
  }

  @Override
  public void restorePoint(String pointName) {
    if (pointName.equals(options.missingPoint)) {
      return;
    }
    PointsetManager.super.restorePoint(pointName);
  }

  /**
   * Set the underlying static model for this pointset. This is information that would NOT be
   * normally available for a device, but would, e.g. be programmed directly into a device. It's
   * only available here since this is a reference pseudo-device for testing.
   *
   * @param model pointset model
   */
  @Override
  public void setPointsetModel(PointsetModel model) {
    Map<String, PointPointsetModel> points =
        ifNotNullGet(model, m -> requireNonNullElseGet(model.points, HashMap::new), DEFAULT_POINTS);

    ifNotNullThen(options.missingPoint,
        x -> requireNonNull(points.remove(x), "missing point not in pointset metadata"));

    points.forEach((name, point) -> addPoint(makePoint(name, point)));
  }

  /**
   * updates the data of all points in the managedPoints map.
   */
  @Override
  public void updatePoints() {
    managedPoints.values().forEach(point -> {
      try {
        point.updateData();
      } catch (Exception ex) {
        error("Unable to update point data", ex);
      }
      updateState(point);
    });
  }

  private void updatePointConfig(AbstractPoint point, PointPointsetConfig pointConfig) {
    ifNotTrueThen(options.noWriteback, () -> {
      point.setConfig(pointConfig);
      updateState(point);
    });
  }

  /**
   * Updates the configuration of pointset points.
   */
  @Override
  public void updatePointsetPointsConfig(PointsetConfig config) {
    // If there is no pointset config, then ensure that there's no pointset state.
    if (config == null) {
      pointsetState = null;
      updateState();
      return;
    }

    // Known that pointset config exists, so ensure that a pointset state also exists.
    ifNullThen(pointsetState, () -> {
      pointsetState = new PointsetState();
      pointsetState.points = new HashMap<>();
      pointsetEvent.points = new HashMap<>();
    });

    // Update each internally managed point with its specific pointset config (if any).
    Map<String, PointPointsetConfig> points = ofNullable(config.points).orElseGet(HashMap::new);
    managedPoints.forEach((name, point) -> updatePointConfig(point, points.get(name)));
    pointsetState.state_etag = config.state_etag;

    // Calculate the differences between the config points and state points.
    Set<String> configuredPoints = points.keySet();
    Set<String> statePoints = pointsetState.points.keySet();
    Set<String> missingPoints = Sets.difference(configuredPoints, statePoints).immutableCopy();
    final Set<String> extraPoints = Sets.difference(statePoints, configuredPoints).immutableCopy();

    // Restore points in config but not state, implicitly creating pointset.points.X as needed.
    missingPoints.forEach(name -> {
      debug("Restoring unknown point " + name);
      restorePoint(name);
    });

    // Suspend points in state but not config, implicitly removing pointset.points.X as needed.
    extraPoints.forEach(key -> {
      debug("Clearing extraneous point " + key);
      suspendPoint(key);
    });

    // Ensure that the logic was correct, and that state points match config points.
    checkState(pointsetState.points.keySet().equals(points.keySet()),
        "state/config pointset mismatch");

    // Special testing provisions for forcing an extra point (designed to cause a violation).
    ifNotNullThen(options.extraPoint,
        extraPoint -> pointsetEvent.points.put(extraPoint,
            udmi.lib.client.PointsetManager.extraPointsetEvent()));

    // Mark device state as dirty, so the system will send a consolidated state update.
    updateState();
  }

  @Override
  public void periodicUpdate() {
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

  @Override
  public int getPointsetUpdateCount() {
    return pointsetUpdateCount;
  }

  @Override
  public udmi.lib.client.PointsetManager.ExtraPointsetEvent getPointsetEvent() {
    return pointsetEvent;
  }

  @Override
  public Map<String, AbstractPoint> getManagedPoints() {
    return managedPoints;
  }

  @Override
  public PointsetState getPointsetState() {
    return pointsetState;
  }

}
