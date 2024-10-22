package daq.pubber;

import udmi.lib.intf.AbstractPoint;
import udmi.schema.PointPointsetModel;
import udmi.schema.RefDiscovery;

/**
 * Represents a random boolean point.
 */
public class RandomBoolean extends BasicPoint implements AbstractPoint {

  public RandomBoolean(String name, PointPointsetModel pointModel) {
    super(name, pointModel);
  }

  @Override
  protected Object getValue() {
    return Math.random() < 0.5;
  }

  @Override
  protected Object setValue(Object setValue) {
    return setValue;
  }

  @Override
  protected boolean validateValue(Object setValue) {
    return setValue instanceof Boolean;
  }

  @Override
  protected void populateEnumeration(RefDiscovery point) {
    point.type = "multistate";
    point.possible_values = null; // Need multi-state values here
  }
}
