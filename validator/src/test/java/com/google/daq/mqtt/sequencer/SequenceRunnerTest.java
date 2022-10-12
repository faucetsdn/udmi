package com.google.daq.mqtt.sequencer;

import static com.google.daq.mqtt.sequencer.SequenceBase.SERIAL_NO_MISSING;
import static org.junit.Assert.assertTrue;

import com.google.daq.mqtt.TestCommon;
import org.junit.Test;
import udmi.schema.ExecutionConfiguration;

/**
 * Unit tests for sequence runner (not to be confused with sequence tests themselves).
 */
public class SequenceRunnerTest {

  private static final String TEST_DEVICE = "AHU-1";

  private ExecutionConfiguration getExecutionConfiguration() {
    ExecutionConfiguration validatorConfig = new ExecutionConfiguration();
    validatorConfig.project_id = TestCommon.PROJECT_ID;
    validatorConfig.site_model = TestCommon.SITE_DIR;
    validatorConfig.log_level = TestCommon.LOG_LEVEL;
    validatorConfig.serial_no = SERIAL_NO_MISSING;
    validatorConfig.key_file = TestCommon.KEY_FILE;
    return validatorConfig;
  }

  @Test
  public void processSite() {
    ExecutionConfiguration config = getExecutionConfiguration();
    SequenceRunner sequenceRunner = SequenceRunner.processConfig(config);
    // TODO: SequenceRunner is not properly mocked, so everything fails.
    assertTrue("many failures", sequenceRunner.getFailures().size() > 10);
  }

  @Test
  public void processDevice() {
    ExecutionConfiguration config = getExecutionConfiguration();
    config.device_id = TEST_DEVICE;
    SequenceRunner sequenceRunner = SequenceRunner.processConfig(config);
    // TODO: SequenceRunner is not properly mocked, so everything fails.
    assertTrue("many failures", sequenceRunner.getFailures().size() > 10);
  }
}
