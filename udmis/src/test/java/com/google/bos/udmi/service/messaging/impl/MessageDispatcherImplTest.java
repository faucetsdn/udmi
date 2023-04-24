package com.google.bos.udmi.service.messaging.impl;

import static com.google.bos.udmi.service.messaging.impl.MessageTestBase.TEST_DESTINATION;
import static com.google.bos.udmi.service.messaging.impl.MessageTestBase.TEST_NAMESPACE;
import static com.google.bos.udmi.service.messaging.impl.MessageTestBase.TEST_SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.bos.udmi.service.messaging.MessageDispatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import udmi.schema.DiscoveryConfig;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.GatewayConfig;
import udmi.schema.LocalnetModel;
import udmi.schema.PointsetEvent;

/**
 * Basic unit tests for the message dispatcher.
 */
public class MessageDispatcherImplTest {

  public static final long GET_TIMEOUT_SEC = 1;

  List<Object> devNullCapture = new ArrayList<>();

  private EndpointConfiguration getConfiguration(boolean reversed) {
    EndpointConfiguration config = new EndpointConfiguration();
    config.protocol = Protocol.LOCAL;
    config.hostname = TEST_NAMESPACE;
    config.recv_id = reversed ? TEST_DESTINATION : TEST_SOURCE;
    config.send_id = reversed ? TEST_SOURCE : TEST_DESTINATION;
    return config;
  }

  private MessageDispatcher getReversedDispatcher() {
    return new MessageDispatcherImpl(new LocalMessagePipe(getConfiguration(true)));
  }

  @Test
  public void activation() {
    MessageDispatcher dispatcher = new TestingDispatcher();
    Assertions.assertFalse(dispatcher.isActive());
    dispatcher.activate();
    Assertions.assertTrue(dispatcher.isActive());
    assertThrows(Exception.class, dispatcher::activate);
  }

  @Test
  public void publishMessages() throws Exception {
    MessageDispatcher dispatcher = new TestingDispatcher();
    CompletableFuture<GatewayConfig> future = new CompletableFuture<>();
    dispatcher.registerHandler(GatewayConfig.class, future::complete);
    dispatcher.registerHandler(DiscoveryConfig.class, message -> {
    });
    getReversedDispatcher().publish(new PointsetEvent());
    getReversedDispatcher().publish(new LocalnetModel());
    getReversedDispatcher().publish(new GatewayConfig());
    assertEquals(0, devNullCapture.size());
    assertThrows(TimeoutException.class, () -> future.get(GET_TIMEOUT_SEC, TimeUnit.SECONDS));
    assertEquals(0, dispatcher.getHandlerCount(LocalnetModel.class), "processed LocalnetModel");
    dispatcher.activate();
    assertNotNull(future.get(GET_TIMEOUT_SEC, TimeUnit.SECONDS));
    assertEquals(1, dispatcher.getHandlerCount(LocalnetModel.class), "processed LocalnetModel");
    assertEquals(1, dispatcher.getHandlerCount(GatewayConfig.class), "processed GatewayConfig");
    assertEquals(0, dispatcher.getHandlerCount(DiscoveryConfig.class), "processed DiscoveryConfig");
    assertEquals(2, devNullCapture.size());
  }

  class TestingDispatcher extends MessageDispatcherImpl {

    public TestingDispatcher() {
      super(new LocalMessagePipe(getConfiguration(false)));
    }

    @Override
    protected void devNullHandler(Object message) {
      devNullCapture.add(message);
    }
  }

  @AfterEach
  void resetForTest() {
    LocalMessagePipe.resetForTestStatic();
  }
}
