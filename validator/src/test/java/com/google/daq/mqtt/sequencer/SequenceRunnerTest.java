package com.google.daq.mqtt.sequencer;

import static org.junit.Assert.assertEquals;
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

  // Number of devices in the testing site model (validator/src/test/site)
  private static final int MODEL_DEVICE_COUNT = 3;

  // Minimum number of tests expected. This is a "low-water mark" to be increased as appropriate.
  // Only tracks tests marked as non-ALPHA.
  private static final int TEST_COUNT_MIN = 3;
  private static final int TEST_COUNT_MAX = TEST_COUNT_MIN * 2;
  private static final int SITE_COUNT_MAX = TEST_COUNT_MAX * MODEL_DEVICE_COUNT;
  private static final int SITE_COUNT_MIN = TEST_COUNT_MIN * MODEL_DEVICE_COUNT;

  @Test
  public void processSite() {
    ExecutionConfiguration config = TestCommon.testConfiguration();
    SequenceRunner.handleRequest(makeParams(config));
    final int runCount = SequenceRunner.getAllTests().size();
    final int failures = SequenceRunner.getFailures().size();

    // TODO: SequenceRunner is not properly mocked, so everything fails.
    assertEquals("site execution failures", runCount, failures);
    assertTrue("site executions", runCount >= SITE_COUNT_MIN && runCount < SITE_COUNT_MAX);
  }

  @Test
  public void processDevice() {
    ExecutionConfiguration config = TestCommon.testConfiguration();
    config.device_id = TEST_DEVICE;
    SequenceRunner.handleRequest(makeParams(config));
    final int runCount = SequenceRunner.getAllTests().size();
    final int failures = SequenceRunner.getFailures().size();

    // TODO: SequenceRunner is not properly mocked, so everything fails.
    assertEquals("site execution failures", runCount, failures);
    assertTrue("device executions", runCount >= TEST_COUNT_MIN && runCount < TEST_COUNT_MAX);
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
