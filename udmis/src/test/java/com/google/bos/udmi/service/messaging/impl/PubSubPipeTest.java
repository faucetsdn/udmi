package com.google.bos.udmi.service.messaging.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.base.Strings;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * Tests for PubSub message pipe.
 */
public class PubSubPipeTest extends MessagePipeTestBase {

  @Override
  protected void augmentConfig(EndpointConfiguration configuration, boolean reversed) {
    configuration.protocol = Protocol.PUBSUB;
  }

  protected boolean environmentIsEnabled() {
    boolean environmentEnabled = !Strings.isNullOrEmpty(PubSubPipe.EMULATOR_HOST);
    if (!environmentEnabled) {
      debug(
          "Skipping test because no emulator defined in " + PubSubPipe.EMULATOR_HOST_ENV);
    }
    return environmentEnabled;
  }

  @Test
  public void publishAndReceiveUufiNullAndSentinelPayloads() {
    Publisher mockPublisher = mock(Publisher.class);
    when(mockPublisher.getTopicNameString()).thenReturn("projects/test-proj/topics/udmi_uufi");
    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-1"));

    EndpointConfiguration config = new EndpointConfiguration();
    config.protocol = Protocol.PUBSUB;
    config.hostname = "test-proj";
    config.send_id = "udmi_uufi";

    PubSubPipe pipe = new PubSubPipe(config) {
      @Override
      Publisher getPublisher(String topicName) {
        return mockPublisher;
      }
    };

    try {
      // 1. Publish NullMessage with gatewayId="uufi" -> stripped from attributes, data="null"
      Envelope uufiEnv = new Envelope();
      uufiEnv.subType = SubType.STATE;
      uufiEnv.subFolder = SubFolder.POINTSET;
      uufiEnv.deviceRegistryId = "reg-1";
      uufiEnv.deviceId = "dev-1";
      uufiEnv.transactionId = "UUFI:txn-1";
      uufiEnv.principal = "app@";
      uufiEnv.gatewayId = "uufi";

      pipe.publish(new Bundle(uufiEnv, MessageDispatcher.nullMessage()));

      // 2. Publish SentinelPayload("missing") -> data="\"missing\""
      Envelope sentinelEnv = new Envelope();
      sentinelEnv.subType = SubType.CONFIG;
      sentinelEnv.subFolder = SubFolder.POINTSET;
      sentinelEnv.transactionId = "UUFI:txn-2";
      sentinelEnv.gatewayId = "gw-real";

      pipe.publish(new Bundle(sentinelEnv, MessageDispatcher.sentinelPayload("missing")));

      ArgumentCaptor<PubsubMessage> captor = ArgumentCaptor.forClass(PubsubMessage.class);
      // 1 hello bundle from constructor + 2 explicit publishes = 3 total
      verify(mockPublisher, org.mockito.Mockito.times(3)).publish(captor.capture());

      PubsubMessage nullMsg = captor.getAllValues().get(1);
      assertFalse(nullMsg.getAttributesMap().containsKey("gatewayId"),
          "Internal uufi gatewayId marker must be omitted from PubSub attributes");
      assertEquals("UUFI:txn-1", nullMsg.getAttributesMap().get("transactionId"));
      assertEquals("app@", nullMsg.getAttributesMap().get("principal"));
      assertEquals("null", nullMsg.getData().toStringUtf8());

      PubsubMessage sentinelMsg = captor.getAllValues().get(2);
      assertEquals("gw-real", sentinelMsg.getAttributesMap().get("gatewayId"),
          "Non-uufi gatewayId must be preserved");
      assertEquals("\"missing\"", sentinelMsg.getData().toStringUtf8());

      // 3. Receive wire "null" and "\"delete\"" with source="udmis" loopback prefix
      AckReplyConsumer ack = mock(AckReplyConsumer.class);
      PubsubMessage inboundNull = PubsubMessage.newBuilder()
          .setMessageId("100")
          .setPublishTime(Timestamp.newBuilder().setSeconds(1700000000L).build())
          .putAttributes("subType", "state")
          .putAttributes("subFolder", "pointset")
          .putAttributes("source", "udmis")
          .putAttributes("principal", "app@")
          .setData(ByteString.copyFromUtf8("null"))
          .build();
      pipe.receiveMessage(inboundNull, ack);
      Bundle polledNull = pipe.poll();
      assertTrue(Boolean.TRUE.equals(polledNull.nullMessage), "null payload maps to nullMessage");
      assertNull(polledNull.message);
      assertEquals("pubsub+udmis", polledNull.envelope.source);

      PubsubMessage inboundSentinel = PubsubMessage.newBuilder()
          .setMessageId("101")
          .setPublishTime(Timestamp.newBuilder().setSeconds(1700000001L).build())
          .putAttributes("subType", "config")
          .putAttributes("subFolder", "pointset")
          .putAttributes("source", "client-a")
          .putAttributes("principal", "client-a@")
          .setData(ByteString.copyFromUtf8("\"delete\""))
          .build();
      pipe.receiveMessage(inboundSentinel, ack);
      Bundle polledSentinel = pipe.poll();
      assertEquals("delete", polledSentinel.sentinelValue, "JSON string maps to sentinelValue");
      assertNull(polledSentinel.message);
      assertEquals("pubsub+client-a", polledSentinel.envelope.source);
    } finally {
      pipe.shutdown();
    }
  }
}
