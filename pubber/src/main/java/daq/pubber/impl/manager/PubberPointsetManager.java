package daq.pubber.impl.manager;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import daq.pubber.impl.PubberManager;
import daq.pubber.impl.point.PubberRandomBoolean;
import daq.pubber.impl.point.PubberRandomPoint;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import udmi.lib.client.manager.PointsetManager;
import udmi.lib.intf.AbstractPoint;
import udmi.lib.intf.ManagerHost;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointPointsetState.Value_state;
import udmi.schema.PointsetState;
import udmi.schema.PubberConfiguration;

/**
 * Helper class to manage the operation of a pointset block.
 */
public class PubberPointsetManager extends PubberManager implements PointsetManager {

  private final ExtraPointsetEvent pointsetEvent = new ExtraPointsetEvent();
  private final Map<String, AbstractPoint> managedPoints = new HashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private final Map<String, String> setValueCache = new ConcurrentHashMap<>();
  private static final int WRITE_DELAY_SEC = 10;

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

  @Override
  public void updatePointConfig(AbstractPoint point, PointPointsetConfig pointConfig) {
    boolean isFastWrite = isFastWrite();
    boolean isDelayWrite = isDelayWrite();
    boolean isNoWriteback = isNoWriteback();

    debug(format("value of noWriteback: %s", isNoWriteback));
    String newPointValue = stringify(catchToNull(() -> pointConfig.set_value));
    String prevPointValue = setValueCache.put(point.getName(), newPointValue);
    boolean isUnmodified = Objects.equals(newPointValue, prevPointValue);

    if (isFastWrite || isUnmodified) {
      PointsetManager.super.updatePointConfig(point, pointConfig);
    } else if (isDelayWrite) {
      debug(format("Applying delayed writeback for point %s with %ds delay", point.getName(),
          WRITE_DELAY_SEC));
      handleDelayWriteback(point, pointConfig, WRITE_DELAY_SEC);
    } else {
      debug(format("Applying slow writeback for point %s with %ds delay", point.getName(),
          WRITE_DELAY_SEC));
      getPointsetState().points.get(point.getName()).value_state = Value_state.UPDATING;
      updateState();
      handleDelayWriteback(point, pointConfig, WRITE_DELAY_SEC);
    }
  }

  private void handleDelayWriteback(AbstractPoint point,
      PointPointsetConfig pointConfig, int delaySec) {
    scheduler.schedule(() -> {
      try {
        info(format("Completing delayed writeback for %s", point.getName()));
        // Use the default interface method to apply the final state.
        debug(format("setting value state as %s", pointConfig.set_value));
        PointsetManager.super.updatePointConfig(point, pointConfig);
      } catch (Exception e) {
        error("Error during scheduled writeback for " + point.getName(), e);
      }
    }, delaySec, TimeUnit.SECONDS);
  }
}
