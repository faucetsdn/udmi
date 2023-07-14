package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.THREE_MINUTES_MS;
import static java.lang.String.format;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.FeatureEnumeration.FeatureStage.BETA;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

/**
 * Validate pointset related functionality.
 */
public class SystemSequences extends SequenceBase {

  /**
   * Simple check that device publishes pointset events
   */
  @Test(timeout = THREE_MINUTES_MS)
  @Feature(stage = BETA, bucket = SYSTEM)
  @Summary("device publishes pointset events")
  public void state_make_model() {
    // Inspect make and model rather than complete hardware block
    String expected_make = catchToNull(() -> deviceMetadata.system.hardware.make);
    String expected_model = catchToNull(() -> deviceMetadata.system.hardware.model);
    if (expected_make == null || expected_model == null) {
      throw new AssumptionViolatedException(
          "make and model not defined in metadata"
      );
    }

    String actual_make = catchToNull(() -> deviceState.system.hardware.make);
    String actual_model = catchToNull(() -> deviceState.system.hardware.model);

    checkThat("make and model match",
        () -> expected_make.equals(actual_make) && expected_model.equals(actual_model)
    );
  }

}



