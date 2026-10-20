package daq.pubber.impl.point;

import udmi.lib.base.BasicPoint;
import udmi.lib.intf.AbstractPoint;
import udmi.schema.PointPointsetModel;
import udmi.schema.RefDiscovery;

/**
 * Represents a string point whose wire value is a JSON string.
 */
public class PubberStringPoint extends BasicPoint implements AbstractPoint {

  private final String baselineValue;

  /**
   * Creates a string point generator for data simulation.
   */
  public PubberStringPoint(String name, PointPointsetModel pointModel) {
    super(name, pointModel);
    this.baselineValue = pointModel.baseline_value instanceof String s ? s : null;
  }

  @Override
  protected Object getValue() {
    return baselineValue != null ? baselineValue : "default-" + name;
  }

  @Override
  protected Object setValue(Object setValue) {
    return setValue;
  }

  @Override
  protected boolean validateValue(Object setValue) {
    return setValue instanceof String;
  }

  @Override
  protected void populateEnumeration(RefDiscovery point) {
    point.type = "string";
  }
}
