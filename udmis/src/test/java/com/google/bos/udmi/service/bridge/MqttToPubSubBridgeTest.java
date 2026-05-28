package com.google.bos.udmi.service.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.bos.udmi.service.support.DataRef;
import com.google.bos.udmi.service.support.EtcdDataProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PubsubMessage;
import java.util.Map;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
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
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    // Mock publisher to return a future
    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    // Call setupBridge
    MqttToPubSubBridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    // Verify subscription
    verify(mockMqttClient).subscribe(testTopic);

    // Capture callback
    ArgumentCaptor<MqttCallbackExtended> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallbackExtended.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallbackExtended callback = callbackCaptor.getValue();

    // Simulate message arrival
    callback.messageArrived(testTopic, mqttMessage);

    // Verify Pub/Sub publish
    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
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
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    MqttToPubSubBridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    ArgumentCaptor<MqttCallbackExtended> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallbackExtended.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallbackExtended callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
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
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    MqttToPubSubBridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    ArgumentCaptor<MqttCallbackExtended> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallbackExtended.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallbackExtended callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    assertEquals(testTopic, attributes.get("mqttTopic"));
    assertEquals("unknown", attributes.get("deviceId"));
    assertEquals("unknown", attributes.get("deviceRegistryId"));
  }

  @Test
  void testSetupBridgeWithEtcd() throws Exception {
    final IMqttClient mockMqttClient = mock(IMqttClient.class);
    final Publisher mockPublisher = mock(Publisher.class);
    final EtcdDataProvider mockEtcdProvider = mock(EtcdDataProvider.class);
    final DataRef mockDataRef = mock(DataRef.class);

    final String testTopic = "/r/my-registry/d/my-device/events";
    final String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    // Mock etcd provider to return a numId
    when(mockEtcdProvider.ref()).thenReturn(mockDataRef);
    when(mockDataRef.registry("my-registry")).thenReturn(mockDataRef);
    when(mockDataRef.device("my-device")).thenReturn(mockDataRef);
    when(mockDataRef.get("num_id")).thenReturn("123456");

    MqttToPubSubBridge.setupBridge(mockMqttClient, mockPublisher, testTopic, mockEtcdProvider);

    ArgumentCaptor<MqttCallbackExtended> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallbackExtended.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallbackExtended callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    assertEquals("123456", attributes.get("deviceNumId"));
  }

  @Test
  void testSetupBridgeWithEtcdNullResult() throws Exception {
    final IMqttClient mockMqttClient = mock(IMqttClient.class);
    final Publisher mockPublisher = mock(Publisher.class);
    final EtcdDataProvider mockEtcdProvider = mock(EtcdDataProvider.class);
    final DataRef mockDataRef = mock(DataRef.class);

    final String testTopic = "/r/my-registry/d/my-device/events";
    final String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    // Mock etcd provider to return null for numId
    when(mockEtcdProvider.ref()).thenReturn(mockDataRef);
    when(mockDataRef.registry("my-registry")).thenReturn(mockDataRef);
    when(mockDataRef.device("my-device")).thenReturn(mockDataRef);
    when(mockDataRef.get("num_id")).thenReturn(null);

    MqttToPubSubBridge.setupBridge(mockMqttClient, mockPublisher, testTopic, mockEtcdProvider);

    ArgumentCaptor<MqttCallbackExtended> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallbackExtended.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallbackExtended callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    org.junit.jupiter.api.Assertions.assertFalse(attributes.containsKey("deviceNumId"));
  }

  @Test
  void testSetupBridgeWithEtcdFailure() throws Exception {
    final IMqttClient mockMqttClient = mock(IMqttClient.class);
    final Publisher mockPublisher = mock(Publisher.class);
    final EtcdDataProvider mockEtcdProvider = mock(EtcdDataProvider.class);
    final DataRef mockDataRef = mock(DataRef.class);

    final String testTopic = "/r/my-registry/d/my-device/events";
    final String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    // Mock etcd provider to throw exception
    when(mockEtcdProvider.ref()).thenReturn(mockDataRef);
    when(mockDataRef.registry("my-registry")).thenReturn(mockDataRef);
    when(mockDataRef.device("my-device")).thenReturn(mockDataRef);
    when(mockDataRef.get("num_id")).thenThrow(new RuntimeException("etcd error"));

    MqttToPubSubBridge.setupBridge(mockMqttClient, mockPublisher, testTopic, mockEtcdProvider);

    ArgumentCaptor<MqttCallbackExtended> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallbackExtended.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallbackExtended callback = callbackCaptor.getValue();

    // This should not throw exception and message should still be published
    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    org.junit.jupiter.api.Assertions.assertFalse(attributes.containsKey("deviceNumId"));
  }

  @Test
  void testSetupBridgeAutoReconnect() throws Exception {
    final IMqttClient mockMqttClient = mock(IMqttClient.class);
    final Publisher mockPublisher = mock(Publisher.class);
    final String testTopic = "/r/my-registry/d/my-device/events";

    MqttToPubSubBridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    // Verify initial subscription
    verify(mockMqttClient).subscribe(testTopic);

    ArgumentCaptor<MqttCallbackExtended> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallbackExtended.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallbackExtended callback = callbackCaptor.getValue();

    // Simulate initial connection completed (reconnect = false)
    callback.connectComplete(false, "tcp://localhost:1883");
    // Verify subscribe NOT called again
    org.mockito.Mockito.verifyNoMoreInteractions(mockMqttClient);

    // Simulate automatic reconnection completed (reconnect = true)
    callback.connectComplete(true, "tcp://localhost:1883");
    // Verify re-subscribed
    verify(mockMqttClient, org.mockito.Mockito.times(2)).subscribe(testTopic);
  }

}
