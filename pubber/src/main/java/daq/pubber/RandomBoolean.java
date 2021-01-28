package daq.pubber;

import daq.udmi.Message.PointData;
import daq.udmi.Message.PointState;

public class RandomBoolean implements AbstractPoint {

  private final String name;
  private final PointData data = new PointData();
  private final PointState state = new PointState();

  public RandomBoolean(String name, String units) {
    this.name = name;
    updateData();
  }

  @Override
  public void updateData() {
    data.present_value = (Math.random() < 0.5);
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
