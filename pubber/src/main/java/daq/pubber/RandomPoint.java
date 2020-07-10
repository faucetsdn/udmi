package daq.pubber;

import daq.udmi.Message.PointData;
import daq.udmi.Message.PointState;

public class RandomPoint implements AbstractPoint {

  private final String name;
  private final double min;
  private final double max;
  private final PointData data = new PointData();
  private final PointState state = new PointState();

  public RandomPoint(String name, double min, double max, String units) {
    this.name = name;
    this.min = min;
    this.max = max;
    this.state.fault = max == min;
    this.state.units = units;
    updateData();
  }

  @Override
  public void updateData() {
    data.present_value = Math.round(Math.random() * (max - min) + min);
  }

  @Override
  public PointState getState() {
    return state;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public PointData getData() {
    return data;
  }
}
