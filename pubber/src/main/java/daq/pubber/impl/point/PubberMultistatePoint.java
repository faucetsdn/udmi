package daq.pubber.impl.point;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import udmi.lib.base.BasicPoint;
import udmi.lib.intf.AbstractPoint;
import udmi.schema.PointPointsetModel;
import udmi.schema.RefDiscovery;

/**
 * Represents a multi-state point whose wire value is a string key from metadata values.
 */
public class PubberMultistatePoint extends BasicPoint implements AbstractPoint {

  private final Map<String, String> values;
  private final List<String> keys;
  private final String baselineValue;

  /**
   * Creates a multi-state point generator for data simulation.
   */
  public PubberMultistatePoint(String name, PointPointsetModel pointModel) {
    super(name, pointModel);
    this.values = pointModel.values;
    this.keys = new ArrayList<>(pointModel.values.keySet());
    this.baselineValue = pointModel.baseline_value instanceof String s
        && pointModel.values.containsKey(s) ? s : null;
  }

  @Override
  protected Object getValue() {
    if (baselineValue != null) {
      return baselineValue;
    }
    int index = (int) Math.floor(Math.random() * keys.size());
    return keys.get(index);
  }

  @Override
  protected Object setValue(Object setValue) {
    return setValue;
  }

  @Override
  protected boolean validateValue(Object setValue) {
    return setValue instanceof String s && values != null && values.containsKey(s);
  }

  @Override
  protected void populateEnumeration(RefDiscovery point) {
    point.type = "multistate";
    point.possible_values = new ArrayList<>(keys);
  }
}
