package udmi.lib.client;

import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static udmi.schema.Category.POINTSET_POINT_INVALID;
import static udmi.schema.Category.POINTSET_POINT_INVALID_VALUE;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import udmi.lib.intf.AbstractPoint;
import udmi.lib.intf.ManagerHost;
import udmi.lib.intf.ManagerLog;
import udmi.schema.Entry;
import udmi.schema.PointPointsetEvents;
import udmi.schema.PointPointsetState;
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

  default void setPointsetModel(PointsetModel pointset) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

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
    return point.getState();
  }

  default void suspendPoint(String pointName) {
    getPointsetState().points.remove(pointName);
    getPointsetEvent().points.remove(pointName);
  }

  /**
   *  Updates the state of a PointsetState object.
   *
   */
  default void updateState() {
    updateState(ofNullable((Object) getPointsetState()).orElse(PointsetState.class));
  }

  void updateState(Object state);


  /**
   * Updates the state of a specific point.
   */
  default void updateState(AbstractPoint point) {
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
    pointPointsetState.status.message = "Unknown configured point " + pointName;
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
      info(format("%s sending %s message #%d with %d points",
          getTimestamp(), getDeviceId(), getPointsetUpdateCount(),
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

  void updatePoints();

  void updatePointsetPointsConfig(PointsetConfig config);

  String getDeviceId();

  ManagerHost getHost();
}
