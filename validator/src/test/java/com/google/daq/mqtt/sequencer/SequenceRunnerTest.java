package com.google.daq.mqtt.sequencer;

import static org.junit.Assert.assertTrue;

import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.WebServerRunner;
import com.google.udmi.util.SiteModel;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import udmi.schema.ExecutionConfiguration;

/**
 * Unit tests for sequence runner (not to be confused with sequence tests themselves).
 */
public class SequenceRunnerTest {

  private static final String TEST_DEVICE = "AHU-1";

  // Minimum number of tests allowed. This is a "low water mark" to be increased as appropriate.
  private static final int TEST_COUNT_MIN = 15;
  private static final int MODEL_DEVICE_COUNT = 4;
  private static final int SITE_COUNT_MIN = TEST_COUNT_MIN * MODEL_DEVICE_COUNT;

  @Test
  public void processSite() {
    ExecutionConfiguration config = TestCommon.testConfiguration();
    SequenceRunner.handleRequest(makeParams(config));
    int runCount = SequenceRunner.getAllTests().size();
    int failures = SequenceRunner.getFailures().size();

    // TODO: SequenceRunner is not properly mocked, so everything fails.
    assertTrue("site executions", runCount >= SITE_COUNT_MIN);
    assertTrue("site failures", failures >= SITE_COUNT_MIN);
  }

  @Test
  public void processDevice() {
    ExecutionConfiguration config = TestCommon.testConfiguration();
    config.device_id = TEST_DEVICE;
    SequenceRunner.handleRequest(makeParams(config));
    int runCount = SequenceRunner.getAllTests().size();
    int failures = SequenceRunner.getFailures().size();

    // TODO: SequenceRunner is not properly mocked, so everything fails.
    assertTrue("device executions", runCount >= TEST_COUNT_MIN);
    assertTrue("too many", runCount < SITE_COUNT_MIN);
    assertTrue("device failures", failures >= TEST_COUNT_MIN);
  }

  private Map<String, String> makeParams(ExecutionConfiguration config) {
    Map<String, String> params = new HashMap<>();
    params.put(WebServerRunner.SITE_PARAM, config.site_model);
    params.put(WebServerRunner.PROJECT_PARAM, config.project_id);
    params.put(WebServerRunner.SERIAL_PARAM, config.serial_no);
    params.put(WebServerRunner.DEVICE_PARAM, config.device_id);
    params.put(WebServerRunner.TEST_PARAM, SiteModel.MOCK_PROJECT);
    return params;
  }

}
