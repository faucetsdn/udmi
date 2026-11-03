package daq.pubber.impl.point;

import java.util.Objects;
import udmi.lib.base.BasicPoint;
import udmi.lib.intf.AbstractPoint;
import udmi.schema.PointPointsetModel;
import udmi.schema.RefDiscovery;

/**
 * Represents a point with a static baseline value.
 */
public class PubberStaticPoint extends BasicPoint implements AbstractPoint {

  private final Object baselineValue;
  private final String units;

  /**
   * Creates a static point generator for data simulation.
   */
  public PubberStaticPoint(String name, PointPointsetModel pointModel) {
    super(name, pointModel);
    this.baselineValue = pointModel.baseline_value;
    this.units = pointModel.units;
  }

  @Override
  protected Object getValue() {
    return baselineValue;
  }

  @Override
  protected Object setValue(Object setValue) {
    return setValue;
  }

  @Override
  protected boolean validateValue(Object setValue) {
    return Objects.equals(baselineValue, setValue);
  }

  @Override
  protected void populateEnumeration(RefDiscovery point) {
    point.units = units;
  }
}
