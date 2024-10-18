package udmi.lib.client;

import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static udmi.schema.Category.POINTSET_POINT_INVALID;
import static udmi.schema.Category.POINTSET_POINT_INVALID_VALUE;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import udmi.lib.AbstractPoint;
import udmi.lib.ManagerHost;
import udmi.lib.ManagerLog;
import udmi.schema.Entry;
import udmi.schema.PointPointsetEvents;
import udmi.schema.PointPointsetState;
import udmi.schema.PointPointsetState.Value_state;
import udmi.schema.PointsetConfig;
import udmi.schema.PointsetEvents;
import udmi.schema.PointsetModel;
import udmi.schema.PointsetState;
import udmi.schema.PubberOptions;

/**
 * Pointset client.
 */
public interface PointsetManagerClient extends ManagerLog {

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
    if (getPointsetState() == null || pointName.equals(getOptions().missingPoint)) {
      return;
    }

    getPointsetState().points.put(pointName, ifNotNullGet(getManagedPoints().get(pointName),
        this::getTweakedPointState, invalidPoint(pointName)));
    getPointsetEvent().points.put(pointName, ifNotNullGet(getManagedPoints().get(pointName),
        AbstractPoint::getData, new PointPointsetEvents()));
  }

  /**
   * Get a tweaked point state.
   *
   * @param point the point.
   * @return tweaked point state.
   */
  default PointPointsetState getTweakedPointState(AbstractPoint point) {
    PointPointsetState state = point.getState();
    // Tweak for testing: erroneously apply an applied state here.
    ifTrueThen(point.getName().equals(getOptions().extraPoint),
        () -> state.value_state = ofNullable(state.value_state).orElse(Value_state.APPLIED));
    return state;
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
      PointPointsetState state = getTweakedPointState(point); // Always call to clear the dirty bit
      PointPointsetState useState = ifTrueGet(getOptions().noPointState,
          PointPointsetState::new, state);
      getPointsetState().points.put(pointName, useState);
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
    if (getPointsetUpdateCount() % UdmiPublisherClient.MESSAGE_REPORT_INTERVAL == 0) {
      info(format("%s sending %s message #%d with %d points",
          getTimestamp(), getDeviceId(), getPointsetUpdateCount(),
          getPointsetEvent().points.size()));
    }
    getHost().publish(getPointsetEvent());
  }

  void stop();

  void shutdown();

  PubberOptions getOptions();

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
