package daq.pubber.impl.point;

import static java.lang.String.format;

import udmi.lib.base.BasicPoint;
import udmi.lib.intf.AbstractPoint;
import udmi.schema.PointPointsetModel;
import udmi.schema.RefDiscovery;

/**
 * Represents a randomly generated numerical point.
 */
public class PubberRandomPoint extends BasicPoint implements AbstractPoint {

  private static final double DEFAULT_BASELINE_VALUE = 50;
  private final double min;
  private final double max;
  private final String units;

  /**
   * Creates a random point generator for data simulation.
   */
  public PubberRandomPoint(String name, PointPointsetModel pointModel) {
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
    if (baselineValue instanceof Number number) {
      return number.doubleValue();
    }
    throw new RuntimeException(format("Unknown value type %s", baselineValue.getClass()));
  }

  @Override
  protected Object getValue() {
    return Math.round(Math.random() * (max - min) + min);
  }

  @Override
  protected boolean validateValue(Object setValue) {
    if (setValue instanceof Number number) {
      double value = number.doubleValue();
      return value >= min && value <= max;
    }
    return false;
  }

  @Override
  protected void populateEnumeration(RefDiscovery point) {
    point.units = units;
  }
}
