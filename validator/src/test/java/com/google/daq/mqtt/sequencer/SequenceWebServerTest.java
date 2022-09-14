package com.google.daq.mqtt.sequencer;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SequenceWebServerTest {

  private static final String PROJECT_ENV_KEY = "PROJECT_ID";
  public static final String PROJECT_ID = System.getenv(PROJECT_ENV_KEY);
  public static final String NO_SERVER_PORT = "0";
  public static final String UDMI_SITE_MODEL = "sites/udmi_site_model";

  @Test
  public void udmiSiteModelTest() {
    List<String> args = new ArrayList<>(ImmutableList.of(NO_SERVER_PORT, PROJECT_ID));
    SequenceWebServer sequenceWebServer = new SequenceWebServer(args);
    sequenceWebServer.processSiteModel(UDMI_SITE_MODEL);
  }

}