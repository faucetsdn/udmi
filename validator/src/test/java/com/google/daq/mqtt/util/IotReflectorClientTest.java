package com.google.daq.mqtt.util;

import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static com.google.udmi.util.Common.TRANSACTION_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.daq.mqtt.validator.Validator.MessageBundle;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.ModelOperation;

/**
 * Unit tests for IotReflectorClient transaction handling.
 */
public class IotReflectorClientTest {

  private static final String SESSION_PREFIX = "RC:e3a54c.";
  private static final String TEST_TXN_ID = SESSION_PREFIX + "00000002";

  @Test
  public void transactionHandlesImmediateReplyBeforePublishReturns() {
    com.google.bos.iot.core.proxy.IotReflectorClient mockMessageClient =
        mock(com.google.bos.iot.core.proxy.IotReflectorClient.class);
    BlockingQueue<MessageBundle> replyQueue = new LinkedBlockingQueue<>();
    AtomicBoolean active = new AtomicBoolean(true);

    when(mockMessageClient.getSessionPrefix()).thenReturn(SESSION_PREFIX);
    when(mockMessageClient.isActive()).thenAnswer(inv -> active.get());
    when(mockMessageClient.getLastProgressEvent()).thenReturn(Instant.EPOCH);
    when(mockMessageClient.takeNextMessage(any())).thenAnswer(
        inv -> replyQueue.poll(50, TimeUnit.MILLISECONDS));

    when(mockMessageClient.publish(any(), any(), any())).thenAnswer(inv -> {
      MessageBundle reply = new MessageBundle();
      reply.attributes = new HashMap<>();
      reply.attributes.put(TRANSACTION_KEY, TEST_TXN_ID);
      reply.attributes.put(SUBFOLDER_PROPERTY_KEY, "cloud");
      reply.attributes.put(SUBTYPE_PROPERTY_KEY, "model");
      reply.message = new HashMap<>();
      reply.message.put("operation", ModelOperation.UPDATE.value());
      reply.message.put("num_id", "123456789101112");
      replyQueue.offer(reply);
      Thread.sleep(100);
      return TEST_TXN_ID;
    });

    IotReflectorClient client = new IotReflectorClient(mockMessageClient);
    try {
      CloudModel registry = new CloudModel();
      registry.operation = ModelOperation.UPDATE;
      client.updateRegistry(registry);
    } finally {
      active.set(false);
      client.shutdown();
    }
  }
}
