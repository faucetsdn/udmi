package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.THREE_MINUTES_MS;
import static java.lang.String.format;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.FeatureEnumeration.FeatureStage.BETA;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import java.util.Map;
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
  @Summary("device publishes correct make and model information in state messages")
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

    checkThat(format("make in state `%s` matches make in metadata `%s`",
            actual_make, expected_make),
        () -> expected_make.equals(actual_make)
    );

    checkThat(format("model in state `%s` matches model in metadata `%s`",
            actual_model, expected_model),
        () -> expected_model.equals(actual_model)
    );
  }

  /**
   * Checks system.software.* keys in metadata are:
   *  (1) in state message
   *  (2) match
   *  Because a device may report a lot information in the state message than that which is user
   *  controllable.
   *  For example, if firmware v1.1 is installed, the device may report the version of all packages
   *  within this firmware
   */
  @Test(timeout = THREE_MINUTES_MS)
  @Feature(stage = BETA, bucket = SYSTEM)
  @Summary("device publishes correct software information in state messages")
  public void state_software() {

    Map metadata_software = catchToNull(() -> deviceMetadata.system.software);
    if ( metadata_software == null ) {
      throw new AssumptionViolatedException(
          "software not defined in metadata"
      );
    }
    Map state_software = catchToNull(() -> deviceState.system.software);

    checkThat("software in state matches software in metadata",
        () -> state_software != null &&
            state_software.entrySet().containsAll(metadata_software.entrySet()
        )
    );
  }

}



