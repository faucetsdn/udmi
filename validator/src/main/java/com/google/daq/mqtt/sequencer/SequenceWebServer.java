package com.google.daq.mqtt.sequencer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.daq.mqtt.sequencer.SequenceTestRunner.validationConfig;

import com.google.common.base.Joiner;
import com.google.daq.mqtt.util.PubSubClient;
import com.google.daq.mqtt.util.SimpleWebServer;
import com.google.daq.mqtt.util.ValidatorConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class SequenceWebServer extends SimpleWebServer {

  public static final Joiner JOINER = Joiner.on(", ");
  private String projectId;

  public SequenceWebServer(List<String> args) {
    super(args);
    processArgs(args);
    setHandler(this::handle);
  }

  public static void main(String[] args) {
    List<String> argList = new ArrayList<>(Arrays.asList(args));
    new SequenceWebServer(argList);
    if (!argList.isEmpty()) {
      throw new IllegalStateException("Extra unexpected args: " + JOINER.join(argList));
    }
  }

  private void processArgs(List<String> remaining) {
    if (remaining.isEmpty()) {
      throw new IllegalArgumentException("Missing project_id argument");
    }
    projectId = remaining.remove(0);
  }

  private void handler(Map<String, Object> message, Map<String, String> attributes) {
    ValidatorConfig config = new ValidatorConfig();
    SequenceTestRunner.process(config);
  }

  private void handle(Map<String, String> params) {
    String subscriptionId = params.remove("subscription");
    if (params.isEmpty()) {
      throw new IllegalArgumentException("Unexpected arguments: " + JOINER.join(params.keySet()));
    }
    PubSubClient pubSubClient = new PubSubClient(checkNotNull(projectId, "projectId not defined"), subscriptionId);
    while (pubSubClient.isActive()) {
      pubSubClient.processMessage(this::handler);
    }
  }

}
