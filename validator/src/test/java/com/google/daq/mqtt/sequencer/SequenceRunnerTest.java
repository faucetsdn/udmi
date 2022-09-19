package com.google.daq.mqtt.sequencer;

import static com.google.daq.mqtt.sequencer.SequencesTestBase.SERIAL_NO_MISSING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.util.ValidatorConfig;
import org.junit.Test;

/**
 * Unit tests for sequence runner (not to be confused with sequence tests themselves).
 */
public class SequenceRunnerTest {

  private static final String TEST_DEVICE = "AHU-1";

  private ValidatorConfig getValidatorConfig() {
    ValidatorConfig validatorConfig = new ValidatorConfig();
    validatorConfig.project_id = TestCommon.PROJECT_ID;
    validatorConfig.site_model = TestCommon.SITE_DIR;
    validatorConfig.log_level = TestCommon.LOG_LEVEL;
    validatorConfig.serial_no = SERIAL_NO_MISSING;
    validatorConfig.key_file = TestCommon.KEY_FILE;
    return validatorConfig;
  }

  @Test
  public void processSite() {
    ValidatorConfig config = getValidatorConfig();
    SequenceTestRunner sequenceRunner = SequenceTestRunner.processConfig(config);
    // TODO: SequenceRunner is not properly mocked, so everything fails.
    assertTrue("many failures", sequenceRunner.getFailures().size() > 10);
  }

  @Test
  public void processDevice() {
    ValidatorConfig config = getValidatorConfig();
    config.device_id = TEST_DEVICE;
    SequenceTestRunner sequenceRunner = SequenceTestRunner.processConfig(config);
    // TODO: SequenceRunner is not properly mocked, so everything fails.
    assertTrue("many failures", sequenceRunner.getFailures().size() > 10);
  }
}
