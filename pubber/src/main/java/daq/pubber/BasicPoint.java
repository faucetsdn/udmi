package daq.pubber;

import com.google.udmi.util.JsonUtil;
import java.util.Date;
import org.checkerframework.checker.units.qual.C;
import udmi.schema.Category;
import udmi.schema.Entry;
import udmi.schema.PointEnumerationEvent;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointPointsetState;
import udmi.schema.PointPointsetState.Value_state;

/**
 * Abstract representation of a basic data point.
 */
public abstract class BasicPoint implements AbstractPoint {

  protected final String name;
  protected final PointPointsetEvent data = new PointPointsetEvent();
  private final PointPointsetState state = new PointPointsetState();
  private final boolean writable;
  protected boolean written;
  private boolean dirty;

  /**
   * Construct a maybe writable point.
   *
   * @param name     Point name
   * @param writable True if writable
   * @param units    Units for the point
   */
  public BasicPoint(String name, boolean writable, String units) {
    this.name = name;
    this.writable = writable;
    state.units = units;
    dirty = true;
  }

  abstract Object getValue();

  @Override
  public void updateData() {
    if (!written) {
      data.present_value = getValue();
    }
  }

  public PointPointsetState getState() {
    dirty = false;
    return state;
  }

  public boolean isDirty() {
    return dirty;
  }

  public String getName() {
    return name;
  }

  public PointPointsetEvent getData() {
    return data;
  }

  /**
   * Set the configuration for this point, nominally to indicate writing a value.
   *
   * @param config Configuration to set
   */
  public void setConfig(PointPointsetConfig config) {

    state.status = null;

    if (config == null || config.set_value == null) {
      written = false;
      state.value_state = null;
      updateData();
      return;
    }

    if (!writable) {
      state.status = notWritableStatus();
      state.value_state = Value_state.FAILURE;
      dirty = true;
    } else if (!validateValue(config.set_value)) {
      state.status = invalidValueStatus();
      state.value_state = Value_state.INVALID;
      dirty = true;
    } else {
      state.value_state = Value_state.APPLIED;
      written = true;
      data.present_value = config.set_value;
    }
  }

  protected abstract boolean validateValue(Object setValue);

  private Entry getEntry() {
    Entry entry = new Entry();
    entry.detail = getPointDetail();
    entry.timestamp = new Date();
    return entry;
  }

  private String getPointDetail() {
    return String.format("Point %s (writable %s)", name, writable);
  }

  private Entry invalidValueStatus() {
    Entry entry = getEntry();
    entry.message = "Written value is not valid";
    entry.category = Category.POINTSET_POINT_INVALID;
    entry.level = Category.POINTSET_POINT_INVALID_VALUE;
    return entry;
  }

  private Entry notWritableStatus() {
    Entry entry = getEntry();
    entry.message = "Point is not writable";
    entry.category = Category.POINTSET_POINT_FAILURE;
    entry.level = Category.POINTSET_POINT_FAILURE_VALUE;
    return entry;
  }

  @Override
  public PointEnumerationEvent enumerate() {
    PointEnumerationEvent point = new PointEnumerationEvent();
    point.description = getClass().getSimpleName() + " " + getName();
    point.writable = writable ? true : null;
    populateEnumeration(point);
    return point;
  }

  protected abstract void populateEnumeration(PointEnumerationEvent point);
}
