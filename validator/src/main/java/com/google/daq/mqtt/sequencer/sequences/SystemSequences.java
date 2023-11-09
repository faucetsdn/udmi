package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.THREE_MINUTES_MS;
import static java.lang.String.format;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.FeatureEnumeration.FeatureStage.BETA;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.Summary;
import java.util.Map;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

/**
 * Validate pointset related functionality.
 */
public class SystemSequences extends SequenceBase {

  /**
   * Simple check that device publishes pointset events.
   */
  @Test(timeout = THREE_MINUTES_MS)
  @Feature(stage = BETA, bucket = SYSTEM)
  @Summary("Check that a device publishes correct make and model information in state messages")
  public void state_make_model() {

    String expectedMake = ifCatchNullSkipTest(
        () -> deviceMetadata.system.hardware.make,
        "make not in metadata"
      );
    info("expected make" + expectedMake);
    String expectedModel = ifCatchNullSkipTest(
        () -> deviceMetadata.system.hardware.model,
        "model not in metadata"
    );
    String actualMake = catchToNull(() -> deviceState.system.hardware.make);
    String actualModel = catchToNull(() -> deviceState.system.hardware.model);
    checkThat("make and model in state matches make in metadata",
        () -> expectedMake.equals(actualMake) && expectedModel.equals(actualModel)
    );

  }

  /**
   * Checks system.software.* keys in metadata are:
   *  (1) in state message
   *  (2) match
   *  Because a device may report a lot information in the state message than that which is user
   *  controllable.
   *  For example, if firmware v1.1 is installed, the device may report the version of all packages
   *  within this firmware in addition to firmware v1.1
   */
  @Test(timeout = THREE_MINUTES_MS)
  @Feature(stage = BETA, bucket = SYSTEM)
  @Summary("Check that a device publishes correct software information in state messages")
  public void state_software() {

    Map expectedSoftware = ifCatchNullSkipTest(
        () -> deviceMetadata.system.software,
        "software not defined in metadata");

    Map actualSoftware = catchToNull(() -> deviceState.system.software);

    checkThat("software in metadata matches state",
        () -> actualSoftware != null
            && actualSoftware.entrySet().containsAll(expectedSoftware.entrySet()
        )
    );
  }

}



