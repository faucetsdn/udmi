package daq.pubber;

import udmi.lib.intf.AbstractPoint;
import udmi.lib.base.BasicPoint;
import udmi.schema.PointPointsetModel;
import udmi.schema.RefDiscovery;

/**
 * Represents a randomly generated numerical point.
 */
public class RandomPoint extends BasicPoint implements AbstractPoint {

  private static final double DEFAULT_BASELINE_VALUE = 50;
  private final double min;
  private final double max;
  private final String units;

  /**
   * Creates a random point generator for data simulation.
   */
  public RandomPoint(String name, PointPointsetModel pointModel) {
    super(name, pointModel);
    double baselineValue = convertValue(pointModel.baseline_value, DEFAULT_BASELINE_VALUE);
    double baselineTolerance = convertValue(pointModel.baseline_tolerance, baselineValue);
    this.min = baselineValue - baselineTolerance;
    this.max = baselineValue + baselineTolerance;
    this.units = pointModel.units;
  }

  private double convertValue(Object baselineValue, double defaultBaselineValue) {
    if (baselineValue == null) {
      return defaultBaselineValue;
    }
    if (baselineValue instanceof Double) {
      return (double) baselineValue;
    }
    if (baselineValue instanceof Integer) {
      return (double) (int) baselineValue;
    }
    throw new RuntimeException("Unknown value type " + baselineValue.getClass());
  }

  @Override
  protected Object setValue(Object setValue) {
    return setValue;
  }

  @Override
  protected Object getValue() {
    return Math.round(Math.random() * (max - min) + min);
  }

  @Override
  protected boolean validateValue(Object setValue) {
    if (setValue instanceof Integer) {
      int value = (int) setValue;
      return value >= min && value <= max;
    }
    if (setValue instanceof Double) {
      double value = (double) setValue;
      return value >= min && value <= max;
    }
    return false;
  }

  @Override
  protected void populateEnumeration(RefDiscovery point) {
    point.units = units;
  }
}
