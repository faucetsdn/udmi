package com.google.daq.mqtt.sequencer;

import static org.junit.Assert.assertTrue;

import com.google.daq.mqtt.TestCommon;
import org.junit.Test;
import udmi.schema.ExecutionConfiguration;

/**
 * Unit tests for sequence runner (not to be confused with sequence tests themselves).
 */
public class SequenceRunnerTest {

  private static final String TEST_DEVICE = "AHU-1";

  @Test
  public void processSite() {
    ExecutionConfiguration config = TestCommon.testConfiguration();
    SequenceRunner sequenceRunner = SequenceRunner.processConfig(config);
    // TODO: SequenceRunner is not properly mocked, so everything fails.
    assertTrue("many failures", sequenceRunner.getFailures().size() > 10);
  }

  @Test
  public void processDevice() {
    ExecutionConfiguration config = TestCommon.testConfiguration();
    config.device_id = TEST_DEVICE;
    SequenceRunner sequenceRunner = SequenceRunner.processConfig(config);
    // TODO: SequenceRunner is not properly mocked, so everything fails.
    assertTrue("many failures", sequenceRunner.getFailures().size() > 10);
  }
}
