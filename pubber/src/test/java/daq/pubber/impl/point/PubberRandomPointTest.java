package daq.pubber.impl.point;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointPointsetState.Value_state;

/**
 * Unit tests for {@link PubberRandomPoint} baseline value and writeback range handling.
 */
public class PubberRandomPointTest {

  @Test
  public void baselineWithoutToleranceEmitsExactBaselineAndAcceptsWriteback() {
    PointPointsetModel model = new PointPointsetModel();
    model.baseline_value = 3;
    model.units = "no-units";
    model.writable = true;

    PubberRandomPoint point = new PubberRandomPoint("cooling_request_count", model);
    for (int i = 0; i < 20; i++) {
      point.updateData();
      assertEquals(3, point.getData().present_value);
    }

    PointPointsetConfig writeConfig = new PointPointsetConfig();
    writeConfig.set_value = 5;
    point.setConfig(writeConfig);
    assertEquals(Value_state.APPLIED, point.getState().value_state);
    assertEquals(5, point.getData().present_value);

    point.setConfig(null);
    assertEquals(3, point.getData().present_value);
  }

  @Test
  public void baselineWithToleranceStaysWithinBounds() {
    PointPointsetModel model = new PointPointsetModel();
    model.baseline_value = 50;
    model.baseline_tolerance = 2.0;
    model.units = "Celsius";

    PubberRandomPoint point = new PubberRandomPoint("room_temp", model);
    for (int i = 0; i < 20; i++) {
      point.updateData();
      long value = ((Number) point.getData().present_value).longValue();
      assertTrue("Expected value within [48, 52], got " + value, value >= 48 && value <= 52);
    }
  }
}
