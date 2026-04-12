package com.google.bos.udmi.service.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PubsubMessage;
import java.util.Map;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MqttToPubSubBridgeTest {

  @Test
  void testSetupBridge() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    Publisher mockPublisher = mock(Publisher.class);
    String testTopic = "/r/my-registry/d/my-device/events";
    String payloadStr = "Hello World";
    MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    // Mock publisher to return a future
    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    // Call setupBridge
    MqttToPubSubBridge.setupBridge(mockMqttClient, mockPublisher, testTopic);

    // Verify subscription
    verify(mockMqttClient).subscribe(testTopic);

    // Capture callback
    ArgumentCaptor<MqttCallback> callbackCaptor = ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    // Simulate message arrival
    callback.messageArrived(testTopic, mqttMessage);

    // Verify Pub/Sub publish
    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor = ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    assertEquals(payloadStr, pubsubMessage.getData().toStringUtf8());

    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    assertEquals(testTopic, attributes.get("mqttTopic"));
    assertEquals("my-device", attributes.get("deviceId"));
    assertEquals("my-registry", attributes.get("deviceRegistryId"));
  }

  @Test
  void testSetupBridgeWithSubFolder() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    Publisher mockPublisher = mock(Publisher.class);
    String testTopic = "/r/my-registry/d/my-device/events/subfolder_name";
    String payloadStr = "Hello World";
    MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    MqttToPubSubBridge.setupBridge(mockMqttClient, mockPublisher, testTopic);

    ArgumentCaptor<MqttCallback> callbackCaptor = ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor = ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    assertEquals(testTopic, attributes.get("mqttTopic"));
    assertEquals("my-device", attributes.get("deviceId"));
    assertEquals("my-registry", attributes.get("deviceRegistryId"));
    assertEquals("subfolder_name", attributes.get("subFolder"));
  }

  @Test
  void testSetupBridgeUnrecognizedTopic() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    Publisher mockPublisher = mock(Publisher.class);
    String testTopic = "invalid/topic/structure";
    String payloadStr = "Hello World";
    MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    MqttToPubSubBridge.setupBridge(mockMqttClient, mockPublisher, testTopic);

    ArgumentCaptor<MqttCallback> callbackCaptor = ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor = ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    assertEquals(testTopic, attributes.get("mqttTopic"));
    assertEquals("unknown", attributes.get("deviceId"));
    assertEquals("unknown", attributes.get("deviceRegistryId"));
  }
}
