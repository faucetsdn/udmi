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
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import udmi.schema.GatewayConfig;
import udmi.schema.LocalnetModel;
import udmi.schema.MessageConfiguration;
import udmi.schema.MessageConfiguration.Transport;
import udmi.schema.PointsetEvent;

public class MessageDispatcherImplTest {

  public static final long GET_TIMEOUT_SEC = 1;

  List<Object> nullHandlerCapture = new ArrayList<>();

  private MessageConfiguration getConfiguration(boolean reversed) {
    MessageConfiguration messageConfiguration = new MessageConfiguration();
    messageConfiguration.transport = Transport.LOCAL;
    messageConfiguration.namespace = TEST_NAMESPACE;
    messageConfiguration.source = reversed ? TEST_DESTINATION : TEST_SOURCE;
    messageConfiguration.destination = reversed ? TEST_SOURCE : TEST_DESTINATION;
    return messageConfiguration;
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
    getReversedDispatcher().publish(new PointsetEvent());
    getReversedDispatcher().publish(new LocalnetModel());
    getReversedDispatcher().publish(new GatewayConfig());
    assertEquals(0, nullHandlerCapture.size());
    assertThrows(TimeoutException.class, () -> future.get(GET_TIMEOUT_SEC, TimeUnit.SECONDS));
    assertEquals(-1, dispatcher.getHandlerCount(LocalnetModel.class),
        "expected no processed messages");
    dispatcher.activate();
    assertNotNull(future.get(GET_TIMEOUT_SEC, TimeUnit.SECONDS));
    assertEquals(1, dispatcher.getHandlerCount(LocalnetModel.class),
        "expected one processed message");
    assertEquals(2, nullHandlerCapture.size());
  }

  class TestingDispatcher extends MessageDispatcherImpl {

    public TestingDispatcher() {
      super(new LocalMessagePipe(getConfiguration(false)));
    }

    @Override
    protected void devNullHandler(Object message) {
      nullHandlerCapture.add(message);
    }
  }
}
