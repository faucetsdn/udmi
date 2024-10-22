package daq.pubber;

import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.Optional.ofNullable;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import udmi.lib.base.ManagerBase;
import udmi.lib.client.PointsetManager;
import udmi.lib.intf.AbstractPoint;
import udmi.lib.intf.ManagerHost;
import udmi.schema.Entry;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointsetConfig;
import udmi.schema.PointsetModel;
import udmi.schema.PointsetState;
import udmi.schema.PubberConfiguration;

/**
 * Helper class to manage the operation of a pointset block.
 */
public class PubberPointsetManager extends ManagerBase implements PointsetManager {

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
  @Override
  public void setPointsetModel(PointsetModel model) {
    Map<String, PointPointsetModel> points =
        ifNotNullGet(model, m -> requireNonNullElseGet(model.points, HashMap::new), DEFAULT_POINTS);

    ifNotNullThen(options.missingPoint,
        x -> requireNonNull(points.remove(x), "missing point not in pointset metadata"));

    points.forEach((name, point) -> addPoint(makePoint(name, point)));
  }

  @Override
  public void addPoint(AbstractPoint point) {
    managedPoints.put(point.getName(), point);
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
   *
   */
  @Override
  public void updatePointsetPointsConfig(PointsetConfig config) {
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

    Set<String> configuredPoints = points.keySet();
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
        extraPoint -> pointsetEvent.points.put(extraPoint,
            udmi.lib.client.PointsetManager.extraPointsetEvent()));

    AtomicReference<Entry> maxStatus = new AtomicReference<>();
    statePoints.forEach(
        name -> ifNotNullThen(pointsetState.points.get(name).status, status -> {
          if (maxStatus.get() == null || status.level > maxStatus.get().level) {
            maxStatus.set(status);
          }
        }));

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
