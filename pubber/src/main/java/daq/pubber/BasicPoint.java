package daq.pubber;

import udmi.schema.Entry;
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
   * @param writable True if writeable
   */
  public BasicPoint(String name, boolean writable) {
    this.name = name;
    this.writable = writable;
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
    if (config == null || config.set_value == null) {
      written = false;
      state.status = null;
      state.value_state = null;
      updateData();
    } else {
      if (!validateValue(config.set_value)) {
        state.status = invalidValueStatus();
        state.value_state = Value_state.INVALID;
        dirty = true;
      } else if (!writable) {
        state.status = notWritableStatus();
        state.value_state = Value_state.FAILURE;
        dirty = true;
      } else {
        state.value_state = Value_state.APPLIED;
        written = true;
        data.present_value = config.set_value;
      }
    }
  }

  private Entry invalidValueStatus() {
    Entry entry = new Entry();
    entry.message = "Written value is not valid";
    return entry;
  }

  protected abstract boolean validateValue(Object setValue);

  private Entry notWritableStatus() {
    Entry entry = new Entry();
    entry.message = "Point is not writable";
    return entry;
  }
}
