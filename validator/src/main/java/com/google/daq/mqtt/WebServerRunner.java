package com.google.daq.mqtt;

import com.google.common.base.Joiner;
import com.google.daq.mqtt.sequencer.SequenceTestRunner;
import com.google.daq.mqtt.util.SimpleWebServer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Web server for the entire suite of validation tools, including sequencer and registrar.
 */
public class WebServerRunner extends SimpleWebServer {

  public static final Joiner JOINER = Joiner.on(", ");
  public static final String SITE_PARAM = "site";
  public static final String PROJECT_PARAM = "project";
  public static final String DEVICE_PARAM = "device";
  public static final String SERIAL_PARAM = "serial";
  private String projectId;

  /**
   * Create a new instance.
   *
   * @param args parameterization arguments
   */
  public WebServerRunner(List<String> args) {
    super(args);
    processArgs(args);
    setHandler("sequencer", params -> handle(params, SequenceTestRunner::handleRequest));
  }

  /**
   * Main entry point for web server.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    List<String> argList = new ArrayList<>(Arrays.asList(args));
    new WebServerRunner(argList);
    if (!argList.isEmpty()) {
      throw new IllegalStateException("Extra unexpected args: " + JOINER.join(argList));
    }
  }

  private void handle(Map<String, String> params, Consumer<Map<String, String>> handler) {
    if (params.put(PROJECT_PARAM, projectId) != null) {
      throw new RuntimeException("Redundant project specified in request params");
    }
    handler.accept(params);
    if (!params.isEmpty()) {
      throw new RuntimeException("Unknown extra params: " + JOINER.join(params.keySet()));
    }
  }

  private void processArgs(List<String> remaining) {
    if (remaining.isEmpty()) {
      throw new IllegalArgumentException("Missing project_id argument");
    }
    projectId = remaining.remove(0);
  }
}
