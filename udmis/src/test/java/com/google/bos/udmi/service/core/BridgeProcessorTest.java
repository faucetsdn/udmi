package com.google.bos.udmi.service.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.bos.udmi.service.messaging.impl.LocalMessagePipe;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.impl.MessageTestCore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import udmi.schema.EndpointConfiguration;

class BridgeProcessorTest extends MessageTestCore {

  Map<String, List<Bundle>> results = new ConcurrentHashMap<>();

  @Test
  public void basicBridge() {
    EndpointConfiguration from = getConfiguration(false, "from");
    EndpointConfiguration to = getConfiguration(false, "to");
    BridgeProcessor bridgeProcessor = new BridgeProcessor(from, to);
    bridgeProcessor.activate();
    MessagePipe reversedFrom = getReversePipe("from");
    MessagePipe reversedTo = getReversePipe("to");
    reversedFrom.publish(getTestBundle("hello"));
    reversedTo.publish(getTestBundle("monkey"));
    bridgeProcessor.shutdown();
    reversedFrom.shutdown();
    reversedTo.shutdown();
    int sum = results.values().stream().map(List::size).mapToInt(Integer::intValue).sum();
    assertEquals(2, sum, "messages received");
    assertEquals("monkey", results.get("from").get(0).message);
    assertEquals("hello", results.get("to").get(0).message);
  }

  private MessagePipe getReversePipe(String name) {
    LocalMessagePipe localMessagePipe = new LocalMessagePipe(getConfiguration(true, name));
    localMessagePipe.activate(bundle -> handleBundle(name, bundle));
    return localMessagePipe;
  }

  private void handleBundle(String name, Bundle bundle) {
    results.computeIfAbsent(name, key -> new ArrayList<>()).add(bundle);
  }

  private Bundle getTestBundle(String id) {
    return new Bundle(id);
  }

  private EndpointConfiguration getConfiguration(boolean reversed, String name) {
    EndpointConfiguration messageConfig = getMessageConfig(reversed);
    messageConfig.hostname = name;
    return messageConfig;
  }
}