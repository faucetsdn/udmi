package udmi.lib.client.manager;

import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.Optional.ofNullable;
import static udmi.schema.Category.POINTSET_POINT_INVALID;
import static udmi.schema.Category.POINTSET_POINT_INVALID_VALUE;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import udmi.lib.intf.AbstractPoint;
import udmi.lib.intf.ManagerHost;
import udmi.lib.intf.ManagerLog;
import udmi.schema.Entry;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetEvents;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointPointsetState;
import udmi.schema.PointPointsetState.Value_state;
import udmi.schema.PointsetConfig;
import udmi.schema.PointsetEvents;
import udmi.schema.PointsetModel;
import udmi.schema.PointsetState;

/**
 * Pointset client.
 */
public interface PointsetManager extends ManagerLog {

  int MESSAGE_REPORT_INTERVAL = 10;

  /**
   * Generates a {@code PointPointsetEvents} object with a present value set to 100.
   *
   * @return A new {@code PointPointsetEvents} instance with the present value initialized to 100.
   */
  static PointPointsetEvents extraPointsetEvent() {
    PointPointsetEvents pointPointsetEvent = new PointPointsetEvents();
    pointPointsetEvent.present_value = 100;
    return pointPointsetEvent;
  }

  ExtraPointsetEvent getPointsetEvent();

  Map<String, AbstractPoint> getManagedPoints();

  int getPointsetUpdateCount();

  PointsetState getPointsetState();

  void setPointsetState(PointsetState ppointsetState);

  default void setExtraField(String extraField) {
    ifNotNullThen(extraField, field -> getPointsetEvent().extraField = field);
  }

  default void addPoint(AbstractPoint point) {
    getManagedPoints().put(point.getName(), point);
  }

  /**
   * Creates a restore point for a given point name.
   *
   * @param pointName the point name.
   */
  default void restorePoint(String pointName) {
    if (getPointsetState() == null) {
      return;
    }

    getPointsetState().points.put(pointName, ifNotNullGet(getManagedPoints().get(pointName),
        this::getPointState, invalidPoint(pointName)));
    getPointsetEvent().points.put(pointName, ifNotNullGet(getManagedPoints().get(pointName),
        AbstractPoint::getData, new PointPointsetEvents()));
  }

  /**
   * Get a tweaked point state.
   *
   * @param point the point.
   * @return tweaked point state.
   */
  default PointPointsetState getPointState(AbstractPoint point) {
    PointPointsetState pointState = point.getState();
    // Tweak for testing: erroneously apply an applied state here.
    ifTrueThen(point.getName().equals(getExtraPoint()), () -> pointState.value_state =
        ofNullable(pointState.value_state).orElse(PointPointsetState.Value_state.APPLIED));
    return ifTrueGet(isNoPointState(), PointPointsetState::new, pointState);
  }

  default void suspendPoint(String pointName) {
    getPointsetState().points.remove(pointName);
    getPointsetEvent().points.remove(pointName);
  }

  /**
   *  Updates the state of a PointsetState object.
   */
  default void updateState() {
    updateState(ofNullable((Object) getPointsetState()).orElse(PointsetState.class));
  }

  void updateState(Object state);

  void incrementUpdateCount();

  /**
   * Periodic update.
   */
  default void periodicUpdate() {
    try {
      if (getPointsetState() != null) {
        incrementUpdateCount();
        updatePoints();
        sendDevicePoints();
      }
    } catch (Exception e) {
      error("Fatal error during execution", e);
    }
  }

  AbstractPoint makePoint(String name, PointPointsetModel point);

  default Map<String, PointPointsetModel> getTestPoints() {
    return new HashMap<>();
  }

  /**
   * Set the underlying static model for this pointset. This is information that would NOT be
   * normally available for a device, but would, e.g. be programmed directly into a device. It's
   * only available here since this is a reference pseudo-device for testing.
   *
   * @param model pointset model
   */
  default void setPointsetModel(PointsetModel model) {
    Map<String, PointPointsetModel> points = ifNotNullGet(model,
        m -> requireNonNullElseGet(m.points, HashMap::new), getTestPoints());
    ifNotNullThen(getMissingPoint(),
        x -> requireNonNull(points.remove(x), "Missing point not in pointset metadata"));
    points.forEach((name, point) -> addPoint(makePoint(name, point)));
  }

  /**
   * Updates the state of a specific point.
   */
  default void updatePoint(AbstractPoint point) {
    String pointName = point.getName();

    if (!getPointsetState().points.containsKey(pointName)) {
      return;
    }

    if (point.isDirty()) {
      // Always call to clear the dirty bit
      getPointsetState().points.put(pointName, getPointState(point));
      updateState();
    }
  }

  /**
   * Marks point as invalid.
   *
   * @param pointName point name.
   * @return PointPointsetState.
   */
  default PointPointsetState invalidPoint(String pointName) {
    PointPointsetState pointPointsetState = new PointPointsetState();
    pointPointsetState.status = new Entry();
    pointPointsetState.status.category = POINTSET_POINT_INVALID;
    pointPointsetState.status.level = POINTSET_POINT_INVALID_VALUE;
    pointPointsetState.status.message = format("Unknown configured point %s", pointName);
    pointPointsetState.status.timestamp = getNow();
    return pointPointsetState;
  }

  /**
   * Updates config.
   */
  default void updateConfig(PointsetConfig config) {
    Integer rate = ifNotNullGet(config, c -> c.sample_rate_sec);
    Integer limit = ifNotNullGet(config, c -> c.sample_limit_sec);
    Integer max = Stream.of(rate, limit).filter(Objects::nonNull).reduce(Math::max).orElse(null);
    updateInterval(max);
    updatePointsetPointsConfig(config);
  }

  void updateInterval(Integer sampleRateSec);

  /**
   * Sends device points to the host.
   */
  default void sendDevicePoints() {
    if (getPointsetUpdateCount() % MESSAGE_REPORT_INTERVAL == 0) {
      info(format("sending %s message #%d with %d points", getDeviceId(), getPointsetUpdateCount(),
          getPointsetEvent().points.size()));
    }
    getHost().publish(getPointsetEvent());
  }

  void stop();

  void shutdown();

  /**
   * PointsetEvents with extraField.
   */
  class ExtraPointsetEvent extends PointsetEvents {

    // This extraField exists only to trigger schema parsing errors.
    public Object extraField;
  }


  String getDeviceId();

  ManagerHost getHost();

  /**
   * Update points.
   */
  default void updatePoints() {
    getManagedPoints().values().forEach(point -> {
      try {
        point.updateData();
      } catch (Exception ex) {
        error("Unable to update point data", ex);
      }
      updatePoint(point);
    });
  }

  boolean syncPoints(Map<String, PointPointsetConfig> points);

  /**
   * Updates the configuration of pointset points.
   */
  default void updatePointsetPointsConfig(PointsetConfig config) {
    // If there is no pointset config, then ensure that there's no pointset state.
    if (config == null) {
      setPointsetState(null);
      updateState();
      return;
    }

    // Known that pointset config exists, so ensure that a pointset state also exists.
    ifNullThen(getPointsetState(), () -> {
      setPointsetState(new PointsetState());
      getPointsetState().points = new HashMap<>();
      getPointsetEvent().points = new HashMap<>();
    });

    // Update each internally managed point with its specific pointset config (if any).
    Map<String, PointPointsetConfig> points = ofNullable(config.points).orElseGet(HashMap::new);
    ifNotNullThen(getMissingPoint(), points::remove);
    syncPoints(points);
    getManagedPoints().forEach((name, point) -> updatePointConfig(point, points.get(name)));
    getPointsetState().state_etag = config.state_etag;

    // Special testing provisions for forcing an extra point (designed to cause a violation).
    ifNotNullThen(getExtraPoint(), extraPoint ->
        getPointsetEvent().points.put(extraPoint, PointsetManager.extraPointsetEvent()));

    // Mark device state as dirty, so the system will send a consolidated state update.
    updateState();
  }

  /**
   * Update point config.
   */
  default void updatePointConfig(AbstractPoint point, PointPointsetConfig pointConfig) {
    ifNotTrueThen(isNoWriteback(), () -> {
      try {
        point.setConfig(pointConfig);
      } catch (Exception ex) {
        error("Unable to set point config", ex);
      }
      updatePoint(point);
    });
  }

  /**
   * Update Point Intermediate State
   * @param point
   * @param pointConfig
   */
  default void updatePointIntermediateState(AbstractPoint point, PointPointsetConfig pointConfig) {
    ifNotTrueThen(isNoWriteback(), () -> {
      try {
        point.setIntermediateState();
      } catch (Exception e) {
        error("Unable to set intermediate state", e);
      }
      updatePoint(point);
    });
  }

  default boolean isNoWriteback() {
    return false;
  }

  default boolean isNoPointState() {
    return false;
  }

  default String getExtraPoint() {
    return null;
  }

  default String getMissingPoint() {
    return null;
  }
}
