package daq.pubber;

/**
 * Represents a randomly generated numerical point.
 */
public class RandomPoint extends BasicPoint implements AbstractPoint {

  private final String name;
  private final double min;
  private final double max;

  /**
   * Creates a random point generator for data simulation.
   *
   * @param name point name
   * @param writable indicates if point is writeable
   * @param min minimum value for generated point
   * @param max maximum value for generated point
   * @param units units of generated point
   */
  public RandomPoint(String name, boolean writable, double min, double max, String units) {
    super(name, writable);
    this.name = name;
    this.min = min;
    this.max = max;
  }

  @Override
  Object getValue() {
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
}
