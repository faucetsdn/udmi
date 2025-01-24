package daq.pubber.impl.manager;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import daq.pubber.impl.PubberManager;
import daq.pubber.impl.point.PubberRandomBoolean;
import daq.pubber.impl.point.PubberRandomPoint;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import udmi.lib.client.manager.PointsetManager;
import udmi.lib.intf.AbstractPoint;
import udmi.lib.intf.ManagerHost;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointsetState;
import udmi.schema.PubberConfiguration;

/**
 * Helper class to manage the operation of a pointset block.
 */
public class PubberPointsetManager extends PubberManager implements PointsetManager {

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

  @Override
  public int getPointsetUpdateCount() {
    return pointsetUpdateCount;
  }

  @Override
  public void incrementUpdateCount() {
    pointsetUpdateCount++;
  }

  @Override
  public ExtraPointsetEvent getPointsetEvent() {
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

  @Override
  public void setPointsetState(PointsetState pointsetState) {
    this.pointsetState = pointsetState;
  }

  @Override
  public void periodicUpdate() {
    PointsetManager.super.periodicUpdate();
  }

  @Override
  public AbstractPoint makePoint(String name, PointPointsetModel point) {
    if (point.units.equals("No-units")) {
      return new PubberRandomBoolean(name, point);
    } else {
      return new PubberRandomPoint(name, point);
    }
  }

  @Override
  public boolean syncPoints(Map<String, PointPointsetConfig> points) {
    // Calculate the differences between the config points and state points.
    Set<String> configuredPoints = points.keySet();
    Set<String> statePoints = pointsetState.points.keySet();
    Set<String> missingPoints = Sets.difference(configuredPoints, statePoints).immutableCopy();
    final Set<String> extraPoints = Sets.difference(statePoints, configuredPoints).immutableCopy();

    // Restore points in config but not state, implicitly creating pointset.points.X as needed.
    missingPoints.forEach(name -> {
      debug(format("Restoring unknown point %s", name));
      restorePoint(name);
    });

    // Suspend points in state but not config, implicitly removing pointset.points.X as needed.
    extraPoints.forEach(key -> {
      debug(format("Clearing extraneous point %s", key));
      suspendPoint(key);
    });

    // Ensure that the logic was correct, and that state points match config points.
    checkState(pointsetState.points.keySet().equals(points.keySet()),
        "state/config pointset mismatch");

    return !missingPoints.isEmpty() || !extraPoints.isEmpty();
  }

  @Override
  public Map<String, PointPointsetModel> getTestPoints() {
    return ImmutableMap.of(
    "recalcitrant_angle", makePointPointsetModel(50, 50, "Celsius"),
    "faulty_finding", makePointPointsetModel(40, 0, "deg"),
    "superimposition_reading", new PointPointsetModel());
  }

  private static PointPointsetModel makePointPointsetModel(int value, double tolerance,
      String units) {
    PointPointsetModel pointMetadata = new PointPointsetModel();
    pointMetadata.writable = true;
    pointMetadata.baseline_value = value;
    pointMetadata.baseline_tolerance = tolerance;
    pointMetadata.units = units;
    return pointMetadata;
  }
}
