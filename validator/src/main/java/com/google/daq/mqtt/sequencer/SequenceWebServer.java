package com.google.daq.mqtt.sequencer;

import com.google.daq.mqtt.util.SimpleWebServer;
import java.util.List;
import java.util.Map;

public class SequenceWebServer {

  public static void main(String[] args) {
    SequenceWebServer server = new SequenceWebServer();
    List<String> remaining = SimpleWebServer.setup(args, server::handle);
    server.processArgs(remaining);
  }

  private void processArgs(List<String> remaining) {
    if (!remaining.isEmpty()) {
      throw new IllegalStateException("Extra unexpected args");
    }
  }

  private void handle(Map<String, String> params) {
    SequenceTestRunner.process();
  }

}
