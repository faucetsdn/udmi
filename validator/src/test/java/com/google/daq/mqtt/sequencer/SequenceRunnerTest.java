package com.google.daq.mqtt.sequencer;

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
    return validatorConfig;
  }

  @Test
  public void processSite() {
    ValidatorConfig config = getValidatorConfig();
    SequenceRunner.processConfig(config);
  }

  @Test
  public void processDevice() {
    ValidatorConfig config = getValidatorConfig();
    config.device_id = TEST_DEVICE;
    SequenceRunner.processConfig(config);
  }
}
