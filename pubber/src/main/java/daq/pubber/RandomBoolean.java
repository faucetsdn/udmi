package daq.pubber;

import udmi.schema.PointEnumerationEvent;

/**
 * Represents a random boolean point.
 */
public class RandomBoolean extends BasicPoint implements AbstractPoint {

  public RandomBoolean(String name, boolean writable) {
    super(name, writable);
  }

  Object getValue() {
    return Math.random() < 0.5;
  }

  @Override
  protected boolean validateValue(Object setValue) {
    return setValue instanceof Boolean;
  }

  @Override
  protected void populateEnumeration(PointEnumerationEvent point) {
    point.type = "multistate";
    point.possible_values = null; // Need multi-state values here
  }
}
