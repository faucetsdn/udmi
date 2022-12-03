package com.google.daq.mqtt.sequencer;

import com.google.daq.mqtt.TestCommon;
import org.junit.Test;
import udmi.schema.ExecutionConfiguration;

/**
 * Unit tests for the SequenceBaseTest class.
 */
public class SequenceBaseTest {

  static {
    SequenceRunner.executionConfiguration = TestCommon.testConfiguration();
    SequenceRunner.executionConfiguration.device_id = TestCommon.DEVICE_ID;
  }

  @Test
  public void messageInterrupted() {
    SequenceBase base1 = new SequenceBase();
    SequenceBase base2 = new SequenceBase();

    base1.testWatcher.starting(null);
    base1.nextMessageBundle();

    base2.testWatcher.starting(null);
    base1.nextMessageBundle();
    base2.nextMessageBundle();
  }
}
