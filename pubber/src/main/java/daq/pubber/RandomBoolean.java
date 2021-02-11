package daq.pubber;

public class RandomBoolean extends BasicPoint implements AbstractPoint {

  public RandomBoolean(String name, boolean writable) {
    super(name, writable);
  }

  Object getValue() {
    return Math.random() < 0.5;
  }

  @Override
  protected boolean validateValue(Object set_value) {
    return set_value instanceof Boolean;
  }
}
