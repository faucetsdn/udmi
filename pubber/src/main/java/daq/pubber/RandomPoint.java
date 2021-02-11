package daq.pubber;

import daq.udmi.Message.PointConfig;
import daq.udmi.Message.PointData;
import daq.udmi.Message.PointState;

public class RandomPoint extends BasicPoint implements AbstractPoint {

  private final String name;
  private final double min;
  private final double max;
  private final PointData data = new PointData();
  private final PointState state = new PointState();

  public RandomPoint(String name, boolean writable, double min, double max, String units) {
    super(name, writable);
    this.name = name;
    this.min = min;
    this.max = max;
  }

  Object getValue() {
    return Math.round(Math.random() * (max - min) + min);
  }

  @Override
  protected boolean validateValue(Object set_value) {
    if (!(set_value instanceof Double)) {
      return false;
    }
    double value = (double) set_value;
    return value >= min && value <= max;
  }
}
