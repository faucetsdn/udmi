package udmi.lib.base;

import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.getNow;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static java.lang.String.format;

import java.util.Objects;
import udmi.lib.intf.AbstractPoint;
import udmi.schema.Category;
import udmi.schema.Entry;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetEvents;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointPointsetState;
import udmi.schema.PointPointsetState.Value_state;
import udmi.schema.RefDiscovery;

/**
 * Abstract representation of a basic data point.
 */
public abstract class BasicPoint implements AbstractPoint {

  protected final PointPointsetEvents data = new PointPointsetEvents();
  private final PointPointsetState state = new PointPointsetState();
  protected final String name;
  private final boolean writable;
  private final String pointRef;

  protected boolean written;
  private boolean dirty;

  /**
   * Construct a maybe writable point.
   */
  public BasicPoint(String name, PointPointsetModel pointModel) {
    this.name = name;
    writable = isTrue(pointModel.writable);
    state.units = pointModel.units;
    dirty = true;
    pointRef = pointModel.ref;
  }

  protected abstract Object getValue();

  protected abstract Object setValue(Object setValue);

  protected abstract boolean validateValue(Object setValue);

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

  public PointPointsetEvents getData() {
    return data;
  }

  /**
   * Set the configuration for this point, nominally to indicate writing a value.
   *
   * @param config Configuration to set
   */
  public void setConfig(PointPointsetConfig config) {
    Value_state previousValueState = state.value_state;
    Entry previousStatus = deepCopy(state.status);
    updateStateConfig(config);
    updateDirtyState(previousValueState, previousStatus);
  }

  private void updateDirtyState(Value_state previousValueState, Entry previousStatus) {
    dirty = dirty
        || state.value_state != previousValueState
        || !Objects.equals(state.status, previousStatus);
  }

  /**
   * Set the Intermediate State of the PointSet and update Dirty State if needed
   */
  public void setIntermediateState() {
    Value_state previousValueState = state.value_state;
    Entry previousStatus = deepCopy(state.status);
    state.value_state  = Value_state.UPDATING;
    updateDirtyState(previousValueState, previousStatus);
  }

  /**
   * Update the state of this point based off of a new config.
   */
  public void updateStateConfig(PointPointsetConfig config) {
    state.status = null;

    if (config != null && !Objects.equals(pointRef, config.ref)) {
      state.status = createEntryFrom(Category.POINTSET_POINT_FAILURE, "Invalid point ref");
      return;
    }

    if (config == null || config.set_value == null) {
      written = false;
      state.value_state = null;
      updateData();
      return;
    }

    try {
      if (!validateValue(config.set_value)) {
        state.status = createEntryFrom(Category.POINTSET_POINT_INVALID,
            "Written value is not valid");
        state.value_state = Value_state.INVALID;
        return;
      }
    } catch (Exception ex) {
      state.status = createEntryFrom(Category.POINTSET_POINT_FAILURE, ex.getMessage());
      state.value_state = Value_state.FAILURE;
      return;
    }

    if (!writable) {
      state.status = createEntryFrom(Category.POINTSET_POINT_FAILURE, "Point is not writable");
      state.value_state = Value_state.FAILURE;
      return;
    }

    try {
      data.present_value = setValue(config.set_value);
      state.value_state = Value_state.APPLIED;
      written = true;
    } catch (Exception ex) {
      state.status = createEntryFrom(Category.POINTSET_POINT_FAILURE, ex.getMessage());
      state.value_state = Value_state.FAILURE;
    }
  }

  @Override
  public RefDiscovery enumerate() {
    RefDiscovery point = new RefDiscovery();
    point.description = format("%s %s", getClass().getSimpleName(), getName());
    point.writable = writable ? true : null;
    populateEnumeration(point);
    return point;
  }

  private Entry createEntryFrom(String category, String message) {
    Entry entry = new Entry();
    entry.detail = format("Point %s (writable %s)", name, writable);
    entry.timestamp = getNow();
    entry.message = message;
    entry.category = category;
    entry.level = Category.LEVEL.get(category).value();
    return entry;
  }

  protected abstract void populateEnumeration(RefDiscovery point);
}
