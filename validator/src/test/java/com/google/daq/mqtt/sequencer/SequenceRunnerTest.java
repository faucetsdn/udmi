package com.google.daq.mqtt.sequencer;

import static java.lang.String.format;
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
  // TODO: Dynamically pull this count form the udmi_site_model/devices/ directory.
  private static final int MODEL_DEVICE_COUNT = 4;

  // Minimum number of tests expected. This is a "low-water mark" to be increased as appropriate
  // when new tests are added. Only tracks tests marked as non-ALPHA.
  // TODO: Dynamically pull this count from the etc/sequencer.out file.
  private static final int TEST_COUNT_MIN = 7;
  private static final int SITE_COUNT_MIN = TEST_COUNT_MIN * MODEL_DEVICE_COUNT;

  private static void assertTestCount(int runCount, int countMin) {
    int countMax = countMin * 2;
    String message = format("%d executions between %d and %d", runCount, countMin, countMax);
    assertTrue(message, runCount >= countMin && runCount < countMax);
  }

  @Test
  public void processSite() {
    ExecutionConfiguration config = TestCommon.testConfiguration();
    SequenceRunner.handleRequest(makeParams(config));
    final int runCount = SequenceRunner.getAllTests().size();
    final int failures = SequenceRunner.getFailures().size();

    // TODO: SequenceRunner is not properly mocked, so everything fails.
    assertEquals("site execution failures", runCount, failures);
    assertTestCount(runCount, SITE_COUNT_MIN);
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
    assertTestCount(runCount, TEST_COUNT_MIN);
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
