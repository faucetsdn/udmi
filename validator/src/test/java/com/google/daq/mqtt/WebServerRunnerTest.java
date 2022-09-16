package com.google.daq.mqtt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Tests for overall suite of web services to invoke subcomponents.
 */
public class WebServerRunnerTest {

  private static final List<String> SERVER_ARGS = ImmutableList.of("0", TestCommon.PROJECT_ID);

  @Test
  public void sitePathMissing() {
    WebServerRunner webServerRunner = new WebServerRunner(new ArrayList<>(SERVER_ARGS));
    Map<String, String> params = new HashMap<>();
    String sequencerResult = webServerRunner.tryHandler("sequencer", params);
    assertTrue("sitePath exception", sequencerResult.contains("sitePath not defined"));
  }

  @Test
  public void deviceRequest() {
    WebServerRunner webServerRunner = new WebServerRunner(new ArrayList<>(SERVER_ARGS));
    Map<String, String> params = new HashMap<>();
    params.put("site", TestCommon.SITE_DIR);
    params.put("device", TestCommon.DEVICE_ID);
    String sequencerResult = webServerRunner.tryHandler("sequencer", params);
    // TODO: Expand limited test that only checks that the requests (not tests) were successful.
    assertEquals("sequence success", "success", sequencerResult);
  }

}