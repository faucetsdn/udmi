package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.sequencer.Feature.Stage.BETA;
import static com.google.daq.mqtt.sequencer.Feature.Stage.STABLE;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.Bucket.SYSTEM_MODE;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.SkipTest;

import org.junit.Test;

/**
 * Validate system related functionality.
 */
public class SystemSequences extends SequenceBase {

  @Test
  @Feature(stage = BETA, bucket = SYSTEM)
  public void valid_serial_no() {
    if (serialNo == null) {
      throw new SkipTest("No test serial number provided");
    }
    untilTrue("received serial number matches", () -> serialNo.equals(lastSerialNo));
  }

}


