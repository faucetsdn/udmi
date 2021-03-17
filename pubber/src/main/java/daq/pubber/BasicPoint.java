package daq.pubber;

import daq.udmi.Entry;
import daq.udmi.Message.PointConfig;
import daq.udmi.Message.PointData;
import daq.udmi.Message.PointState;

public abstract class BasicPoint implements AbstractPoint {

  private static final String INVALID_STATE = "invalid";
  private static final String APPLIED_STATE = "applied";
  private static final String FAILURE_STATE = "failure";

  protected final String name;
  protected final PointData data = new PointData();
  private final PointState state = new PointState();
  private final boolean writable;
  protected boolean written;
  private boolean dirty;

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

  public PointState getState() {
    dirty = false;
    return state;
  }

  public boolean isDirty() { return dirty; }

  public String getName() {
    return name;
  }

  public PointData getData() {
    return data;
  }

  public void setConfig(PointConfig config) {
    if (config == null || config.set_value == null) {
      written = false;
      state.status = null;
      state.value_state = null;
      updateData();
    } else {
      if (!validateValue(config.set_value)) {
        state.status = invalidValueStatus();
        state.value_state = INVALID_STATE;
        dirty = true;
      } else if (!writable) {
        state.status = notWritableStatus();
        state.value_state = FAILURE_STATE;
        dirty = true;
      } else {
        state.value_state = APPLIED_STATE;
        written = true;
        data.present_value = config.set_value;
      }
    }
  }

  private Entry invalidValueStatus() {
    return new Entry("Written value is not valid");
  }

  protected abstract boolean validateValue(Object set_value);

  private Entry notWritableStatus() {
    return new Entry("Point is not writable");
  }
}
