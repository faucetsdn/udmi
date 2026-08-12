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
import com.google.common.hash.Hashing;
import com.google.pubsub.v1.PubsubMessage;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.eclipse.paho.mqttv5.client.IMqttClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MqttToPubSubBridgeTest {

  @BeforeEach
  void setUp() {
    MqttToPubSubBridge.clearCacheForTest();
  }

  @Test
  void testSetupBridge() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String testTopic = "/r/my-registry/d/my-device/events";
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    // Mock publisher to return a future
    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    // Call setupBridge
    new MqttToPubSubBridge().setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    // Verify subscription
    verify(mockMqttClient).subscribe(testTopic, 1);

    // Capture callback
    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    // Simulate message arrival
    callback.messageArrived(testTopic, mqttMessage);

    // Verify Pub/Sub publish
    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000)).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    assertEquals(payloadStr, pubsubMessage.getData().toStringUtf8());

    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    assertEquals(testTopic, attributes.get("mqttTopic"));
    assertEquals("my-device", attributes.get("deviceId"));
    assertEquals("my-registry", attributes.get("deviceRegistryId"));
    assertEquals("bridge", attributes.get("source"));
    org.junit.jupiter.api.Assertions.assertNotNull(attributes.get("receiveTime"));
    assertEquals("test-client", attributes.get("distributorClientId"));
  }

  @Test
  void testSetupBridgeWithSubFolder() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String testTopic = "/r/my-registry/d/my-device/events/subfolder_name";
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    new MqttToPubSubBridge().setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000)).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    assertEquals(testTopic, attributes.get("mqttTopic"));
    assertEquals("my-device", attributes.get("deviceId"));
    assertEquals("my-registry", attributes.get("deviceRegistryId"));
    assertEquals("subfolder_name", attributes.get("subFolder"));
    org.junit.jupiter.api.Assertions.assertNotNull(attributes.get("receiveTime"));
    assertEquals("test-client", attributes.get("distributorClientId"));
  }

  @Test
  void testSetupBridgeUnrecognizedTopic() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String testTopic = "invalid/topic/structure";
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    new MqttToPubSubBridge().setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000)).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    assertEquals(testTopic, attributes.get("mqttTopic"));
    assertEquals("unknown", attributes.get("deviceId"));
    assertEquals("unknown", attributes.get("deviceRegistryId"));
    org.junit.jupiter.api.Assertions.assertNotNull(attributes.get("receiveTime"));
    assertEquals("test-client", attributes.get("distributorClientId"));
  }

  @Test
  void testSetupBridgeWithEtcd() throws Exception {
    final IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
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
    when(mockDataRef.getAsSerializable("num_id")).thenReturn("123456");

    new MqttToPubSubBridge()
        .setupBridge(mockMqttClient, mockPublisher, testTopic, mockEtcdProvider);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000)).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    assertEquals("123456", attributes.get("deviceNumId"));
  }

  @Test
  void testSetupBridgeWithEtcdNullResult() throws Exception {
    final IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
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
    when(mockDataRef.getAsSerializable("num_id")).thenReturn(null);

    new MqttToPubSubBridge()
        .setupBridge(mockMqttClient, mockPublisher, testTopic, mockEtcdProvider);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000)).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    org.junit.jupiter.api.Assertions.assertFalse(attributes.containsKey("deviceNumId"));
  }

  @Test
  void testSetupBridgeWithEtcdFailure() throws Exception {
    final IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
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
    when(mockDataRef.getAsSerializable("num_id")).thenThrow(new RuntimeException("etcd error"));

    new MqttToPubSubBridge()
        .setupBridge(mockMqttClient, mockPublisher, testTopic, mockEtcdProvider);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    // This should not throw exception and message should still be published
    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000)).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    org.junit.jupiter.api.Assertions.assertFalse(attributes.containsKey("deviceNumId"));
  }

  @Test
  void testSetupBridgeAutoReconnect() throws Exception {
    final IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    final Publisher mockPublisher = mock(Publisher.class);
    final String testTopic = "/r/my-registry/d/my-device/events";

    new MqttToPubSubBridge().setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    // Verify initial subscription
    verify(mockMqttClient).subscribe(testTopic, 1);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    verify(mockMqttClient).setManualAcks(true);
    MqttCallback callback = callbackCaptor.getValue();

    // Simulate initial connection completed (reconnect = false)
    callback.connectComplete(false, "tcp://localhost:1883");
    // Verify subscribe NOT called again
    org.mockito.Mockito.verifyNoMoreInteractions(mockMqttClient);

    // Simulate automatic reconnection completed (reconnect = true)
    callback.connectComplete(true, "tcp://localhost:1883");
    // Verify re-subscribed asynchronously
    verify(mockMqttClient, org.mockito.Mockito.timeout(5000).times(2)).subscribe(testTopic, 1);
  }

  @Test
  void testSetupBridgeWithCustomSource() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String testTopic = "/r/my-registry/d/my-device/events";
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    // Call 5-parameter setupBridge with custom source attribute
    new MqttToPubSubBridge()
        .setupBridge(mockMqttClient, mockPublisher, testTopic, null, "custom-source");

    verify(mockMqttClient).subscribe(testTopic, 1);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000)).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();
    assertEquals("custom-source", attributes.get("source"));
  }

  @Test
  void testSetupBridgeWithSharedSubscription() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String originalTopic = "/r/my-registry/d/my-device/events";
    String sharedSubscriptionName = "my-group";
    String expectedFilter = "$share/my-group//r/my-registry/d/my-device/events";
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    // Note: The double-slash before 'r' is expected because the MQTT topic explicitly
    // starts with a slash (e.g. "/r/..."). Thus, "$share/my-group/" concatenated with
    // "/r/..." correctly yields the double slash.
    // Call setupBridge with shared subscription
    new MqttToPubSubBridge()
        .setupBridge(mockMqttClient, mockPublisher, originalTopic, null,
            "bridge", sharedSubscriptionName);

    // Verify it subscribed to the shared subscription filter
    verify(mockMqttClient).subscribe(expectedFilter, 1);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    // The broker will typically send the original topic without the $share prefix
    // but we'll test the automatic topic parsing by passing the $share prefixed topic
    callback.messageArrived(expectedFilter, mqttMessage);

    ArgumentCaptor<PubsubMessage> pubsubMessageCaptor =
        ArgumentCaptor.forClass(PubsubMessage.class);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000)).publish(pubsubMessageCaptor.capture());

    PubsubMessage pubsubMessage = pubsubMessageCaptor.getValue();
    Map<String, String> attributes = pubsubMessage.getAttributesMap();

    // Should correctly strip $share/my-group/ and parse the registry and device
    assertEquals(originalTopic, attributes.get("mqttTopic"));
    assertEquals("my-device", attributes.get("deviceId"));
    assertEquals("my-registry", attributes.get("deviceRegistryId"));
  }

  @Test
  void testGetEtcdOptions() throws Exception {
    String[] args = {
        "--gcp_project_id=my-project",
        "--pubsub_topic_id=my-topic",
        "--mqtt_client_id=my-client",
        "--etcd_target=https://localhost:2379",
        "--etcd_ca_path=/path/to/ca.crt",
        "--etcd_client_cert_path=/path/to/client.crt",
        "--etcd_client_key_path=/path/to/client.key",
        "--etcd_options=enabled=true"
    };
    CommandLine commandLine = MqttToPubSubBridge.parseArgs(args);
    String etcdOptions = MqttToPubSubBridge.getEtcdOptions(commandLine);
    String expected = "enabled=true,ca_file=/path/to/ca.crt,cert_file=/path/to/client.crt,"
        + "key_file=/path/to/client.key";
    assertEquals(expected, etcdOptions);
  }

  @Test
  void testGetEtcdOptionsNoTarget() throws Exception {
    String[] args = {
        "--gcp_project_id=my-project",
        "--pubsub_topic_id=my-topic",
        "--mqtt_client_id=my-client",
        "--etcd_ca_path=/path/to/ca.crt",
        "--etcd_options=enabled=true"
    };
    CommandLine commandLine = MqttToPubSubBridge.parseArgs(args);
    String etcdOptions = MqttToPubSubBridge.getEtcdOptions(commandLine);
    // Should be null because etcd_target is missing, so it's ignored
    assertEquals(null, etcdOptions);
  }

  @Test
  void testGetEtcdOptionsPartialSsl() throws Exception {
    String[] args = {
        "--gcp_project_id=my-project",
        "--pubsub_topic_id=my-topic",
        "--mqtt_client_id=my-client",
        "--etcd_target=https://localhost:2379",
        "--etcd_ca_path=/path/to/ca.crt"
    };
    CommandLine commandLine = MqttToPubSubBridge.parseArgs(args);
    String etcdOptions = MqttToPubSubBridge.getEtcdOptions(commandLine);
    assertEquals("ca_file=/path/to/ca.crt", etcdOptions);
  }

  @Test
  void testSetupBridgePublishRetry() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String testTopic = "/r/my-registry/d/my-device/events";
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());

    // Mock publisher to fail on first attempt, succeed on second attempt
    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("Transient failure")))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    // Call setupBridge
    new MqttToPubSubBridge().setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    // Capture callback
    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    // Simulate message arrival
    callback.messageArrived(testTopic, mqttMessage);

    // Verify publish was called twice due to retry
    verify(mockPublisher, org.mockito.Mockito.timeout(5000).times(2))
        .publish(any(PubsubMessage.class));
  }

  @Test
  void testInProcessMessageSkipsQueue() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());
    mqttMessage.setId(1001);
    mqttMessage.setQos(1);

    // Return a never-completing future so the message stays IN_PROCESS
    com.google.api.core.SettableApiFuture<String> pendingFuture =
        com.google.api.core.SettableApiFuture.create();
    when(mockPublisher.publish(any(PubsubMessage.class))).thenReturn(pendingFuture);

    String testTopic = "/r/my-registry/d/my-device/events";
    MqttToPubSubBridge bridge = new MqttToPubSubBridge();
    bridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    // First arrival - message is queued and stays IN_PROCESS
    callback.messageArrived(testTopic, mqttMessage);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000).times(1))
        .publish(any(PubsubMessage.class));
    assertEquals(1, bridge.getUnackedCount());

    // Second arrival while IN_PROCESS - should skip queue
    MqttMessage dupMessage = new MqttMessage(payloadStr.getBytes());
    dupMessage.setId(1001);
    dupMessage.setQos(1);
    dupMessage.setDuplicate(true);

    callback.messageArrived(testTopic, dupMessage);
    // Publish should NOT be called a second time
    verify(mockPublisher, org.mockito.Mockito.timeout(2000).times(1))
        .publish(any(PubsubMessage.class));
    assertEquals(1, bridge.getUnackedCount());
  }

  @Test
  void testAbandonedMessageRequeuesOnRedelivery() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());
    mqttMessage.setId(3003);
    mqttMessage.setQos(1);

    // Publisher fails all 5 retries
    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("PubSub Outage")));

    String testTopic = "/r/my-registry/d/my-device/events";
    MqttToPubSubBridge bridge = new MqttToPubSubBridge();
    bridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);
    // 5 attempts expected (exponential backoff up to ~15s)
    verify(mockPublisher, org.mockito.Mockito.timeout(20000).times(5))
        .publish(any(PubsubMessage.class));
    Thread.sleep(500);
    // After 5 failed attempts, message is marked as ABANDONED and stays unacked
    assertEquals(1, bridge.getUnackedCount());
    assertEquals(1, bridge.getAbandonedCount());
    assertEquals(0, bridge.getInProcessCount());
    assertEquals(MqttToPubSubBridge.MessageState.ABANDONED,
        bridge.getMessageState(MqttToPubSubBridge.getMessageHash(testTopic, mqttMessage)));

    // Now publisher recovers
    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-success"));

    // Redelivery arrival of failed message - should be requeued and retried
    MqttMessage redeliveredMessage = new MqttMessage(payloadStr.getBytes());
    redeliveredMessage.setId(3003);
    redeliveredMessage.setQos(1);

    callback.messageArrived(testTopic, redeliveredMessage);
    // Total publish calls should now be 6 (5 initial + 1 redelivery)
    verify(mockPublisher, org.mockito.Mockito.timeout(20000).times(6))
        .publish(any(PubsubMessage.class));
    // After success, unacked count should be 0
    assertEquals(0, bridge.getUnackedCount());
    assertEquals(0, bridge.getAbandonedCount());
  }

  @Test
  void testUnackedMessagesMapTracking() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());
    mqttMessage.setId(2002);
    mqttMessage.setQos(1);

    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFuture("msg-123"));

    String testTopic = "/r/my-registry/d/my-device/events";
    MqttToPubSubBridge bridge = new MqttToPubSubBridge();
    bridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000).times(1))
        .publish(any(PubsubMessage.class));
    // Verify unacked count popped to 0 after delivery
    assertEquals(0, bridge.getUnackedCount());
  }

  @Test
  void testProcessingExceptionRemovesFromUnackedMessages() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String testTopic = "/r/my-registry/d/my-device/events";

    MqttMessage badMessage = mock(MqttMessage.class);
    when(badMessage.getId()).thenReturn(5005);
    when(badMessage.getPayload()).thenThrow(new RuntimeException("Corrupted payload"));

    MqttToPubSubBridge bridge = new MqttToPubSubBridge();
    bridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, badMessage);
    // Wait briefly for executor to process and throw exception
    Thread.sleep(1000);
    assertEquals(0, bridge.getUnackedCount());
  }

  @Test
  void testIdWrappingDoesNotSkipNewMessage() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());
    mqttMessage.setId(1001);
    mqttMessage.setQos(1);

    com.google.api.core.SettableApiFuture<String> pendingFuture =
        com.google.api.core.SettableApiFuture.create();
    when(mockPublisher.publish(any(PubsubMessage.class))).thenReturn(pendingFuture);

    String testTopic = "/r/my-registry/d/my-device/events";
    MqttToPubSubBridge bridge = new MqttToPubSubBridge();
    bridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    // First arrival - message is queued and stays in process
    callback.messageArrived(testTopic, mqttMessage);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000).times(1))
        .publish(any(PubsubMessage.class));
    assertEquals(1, bridge.getUnackedCount());

    // Second arrival with same ID but different payload (ID wrap-around for a new message)
    MqttMessage wrappedIdMessage = new MqttMessage("New payload after wrap".getBytes());
    wrappedIdMessage.setId(1001);
    wrappedIdMessage.setQos(1);

    callback.messageArrived(testTopic, wrappedIdMessage);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000).times(2))
        .publish(any(PubsubMessage.class));
  }

  @Test
  void testFailedTrafficAllowsSubsequentMessages() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage1 = new MqttMessage(payloadStr.getBytes());
    mqttMessage1.setId(4001);
    mqttMessage1.setQos(1);

    final MqttMessage mqttMessage2 = new MqttMessage(payloadStr.getBytes());
    mqttMessage2.setId(4002);
    mqttMessage2.setQos(1);

    Publisher mockPublisher = mock(Publisher.class);
    // First message fails all 5 attempts
    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("PubSub Outage")))
        .thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("PubSub Outage")))
        .thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("PubSub Outage")))
        .thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("PubSub Outage")))
        .thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("PubSub Outage")))
        .thenReturn(ApiFutures.immediateFuture("msg-success"));

    String testTopic = "/r/my-registry/d/my-device/events";
    MqttToPubSubBridge bridge = new MqttToPubSubBridge();
    bridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage1);
    verify(mockPublisher, org.mockito.Mockito.timeout(20000).times(5))
        .publish(any(PubsubMessage.class));
    Thread.sleep(500);
    // Message 1 is marked as ABANDONED (still counted in unackedMessages)
    assertEquals(1, bridge.getUnackedCount());
    assertEquals(1, bridge.getAbandonedCount());

    // Now second message arrives and succeeds
    callback.messageArrived(testTopic, mqttMessage2);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000).times(6))
        .publish(any(PubsubMessage.class));

    // Message 2 succeeded and removed, Message 1 remains abandoned
    assertEquals(1, bridge.getUnackedCount());
    assertEquals(1, bridge.getAbandonedCount());
  }

  @Test
  void testQueueWorkersExitImmediatelyWhenTripped() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());
    mqttMessage.setId(6001);
    mqttMessage.setQos(1);

    String testTopic = "/r/my-registry/d/my-device/events";
    MqttToPubSubBridge bridge = new MqttToPubSubBridge();
    bridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);
    bridge.setTrippedForTest(true);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    // Verify publisher is never called because bridge is tripped
    Thread.sleep(1000);
    verify(mockPublisher, org.mockito.Mockito.never()).publish(any(PubsubMessage.class));
  }

  @Test
  void testMessageHashFormat() {
    String payloadStr = "Test payload for hash";
    MqttMessage message = new MqttMessage(payloadStr.getBytes());
    message.setId(777);

    String expectedPayloadHash = Hashing.murmur3_128().hashBytes(payloadStr.getBytes()).toString();
    String expectedHash = "777:" + expectedPayloadHash;

    assertEquals(expectedHash, MqttToPubSubBridge.getMessageHash(message));
    assertEquals("/topic:" + expectedHash, MqttToPubSubBridge.getMessageHash("/topic", message));
  }

  @Test
  void testSameIdDifferentPayloadBothProcessed() throws Exception {
    final IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    final Publisher mockPublisher = mock(Publisher.class);

    final MqttMessage msg1 = new MqttMessage("Payload One".getBytes());
    msg1.setId(5050);
    msg1.setQos(1);

    final MqttMessage msg2 = new MqttMessage("Payload Two".getBytes());
    msg2.setId(5050);
    msg2.setQos(1);

    com.google.api.core.SettableApiFuture<String> pendingFuture1 =
        com.google.api.core.SettableApiFuture.create();
    com.google.api.core.SettableApiFuture<String> pendingFuture2 =
        com.google.api.core.SettableApiFuture.create();
    when(mockPublisher.publish(any(PubsubMessage.class)))
        .thenReturn(pendingFuture1)
        .thenReturn(pendingFuture2);

    String testTopic = "/r/my-registry/d/my-device/events";
    MqttToPubSubBridge bridge = new MqttToPubSubBridge();
    bridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    // First message arrives
    callback.messageArrived(testTopic, msg1);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000).times(1))
        .publish(any(PubsubMessage.class));
    assertEquals(1, bridge.getUnackedCount());

    // Second message arrives with same ID but different payload
    callback.messageArrived(testTopic, msg2);
    verify(mockPublisher, org.mockito.Mockito.timeout(5000).times(2))
        .publish(any(PubsubMessage.class));
    assertEquals(2, bridge.getUnackedCount());

    // Complete msg1
    pendingFuture1.set("msg-1");
    // Wait for callback
    Thread.sleep(500);
    assertEquals(1, bridge.getUnackedCount());

    // Complete msg2
    pendingFuture2.set("msg-2");
    Thread.sleep(500);
    assertEquals(0, bridge.getUnackedCount());
  }

  @Test
  void testCircuitBreakerTripsWhenMessageStuck() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    com.google.api.core.SettableApiFuture<String> pendingFuture =
        com.google.api.core.SettableApiFuture.create();
    when(mockPublisher.publish(any(PubsubMessage.class))).thenReturn(pendingFuture);

    final boolean[] exited = new boolean[]{false};
    MqttToPubSubBridge bridge = new MqttToPubSubBridge() {
      @Override
      protected void exit(int status) {
        exited[0] = true;
      }
    };

    String testTopic = "/r/my-registry/d/my-device/events";
    bridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    // Start circuit breaker with high abandoned threshold (100) and 1500ms stuck timeout
    bridge.startCircuitBreaker(mockMqttClient, 100, 1500L);

    // Send message 1 (which never completes, staying IN_PROCESS)
    MqttMessage msg1 = new MqttMessage("Payload 1".getBytes());
    msg1.setId(101);
    callback.messageArrived(testTopic, msg1);

    // Wait for stuck timeout (1500ms + polling buffer)
    long deadline = System.currentTimeMillis() + 8000;
    while ((!bridge.isTripped() || !exited[0]) && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }
    org.junit.jupiter.api.Assertions.assertTrue(bridge.isTripped());
    org.junit.jupiter.api.Assertions.assertTrue(exited[0]);
    verify(mockMqttClient).disconnectForcibly();
  }

  @Test
  void testCircuitBreakerTripsWhenAbandonedThresholdReached() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");

    final boolean[] exited = new boolean[]{false};
    MqttToPubSubBridge bridge = new MqttToPubSubBridge() {
      @Override
      protected void exit(int status) {
        exited[0] = true;
      }
    };

    // Start circuit breaker with abandoned threshold = 2 and long timeout (60000ms)
    bridge.startCircuitBreaker(mockMqttClient, 2, 60000L);

    // Add 1 abandoned message (below threshold)
    bridge.putMessageRecordForTest("msg1",
        new MqttToPubSubBridge.MessageRecord(MqttToPubSubBridge.MessageState.ABANDONED));
    Thread.sleep(1500);
    org.junit.jupiter.api.Assertions.assertFalse(bridge.isTripped());

    // Add 2nd abandoned message (reaches threshold 2 >= 2)
    bridge.putMessageRecordForTest("msg2",
        new MqttToPubSubBridge.MessageRecord(MqttToPubSubBridge.MessageState.ABANDONED));

    long deadline = System.currentTimeMillis() + 8000;
    while ((!bridge.isTripped() || !exited[0]) && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }
    org.junit.jupiter.api.Assertions.assertTrue(bridge.isTripped());
    org.junit.jupiter.api.Assertions.assertTrue(exited[0]);
    verify(mockMqttClient).disconnectForcibly();
  }

  @Test
  void testCircuitBreakerDoesNotTripUnderHighThroughputSaturation() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");

    final boolean[] exited = new boolean[]{false};
    MqttToPubSubBridge bridge = new MqttToPubSubBridge() {
      @Override
      protected void exit(int status) {
        exited[0] = true;
      }
    };

    // 1500ms timeout
    bridge.startCircuitBreaker(mockMqttClient, 100, 1500L);

    // Continuously add and remove messages so count is high but no message exceeds 1500ms
    long testEnd = System.currentTimeMillis() + 3000;
    int id = 0;
    while (System.currentTimeMillis() < testEnd) {
      String key = "key-" + (++id);
      bridge.putMessageRecordForTest(key,
          new MqttToPubSubBridge.MessageRecord(MqttToPubSubBridge.MessageState.IN_PROCESS));
      Thread.sleep(50);
      bridge.getMessageState(key);
      // simulate completion
      bridge.putMessageRecordForTest(key, null);
    }

    org.junit.jupiter.api.Assertions.assertFalse(bridge.isTripped());
    org.junit.jupiter.api.Assertions.assertFalse(exited[0]);
  }

  @Test
  void testParseArgsCircuitBreakerOptions() throws Exception {
    String[] args = {
        "--gcp_project_id=my-project",
        "--pubsub_topic_id=my-topic",
        "--mqtt_client_id=my-client",
        "--circuit_breaker_unacked_threshold=50",
        "--circuit_breaker_timeout_sec=120"
    };
    CommandLine commandLine = MqttToPubSubBridge.parseArgs(args);
    assertEquals("50", commandLine.getOptionValue("circuit_breaker_unacked_threshold"));
    assertEquals("120", commandLine.getOptionValue("circuit_breaker_timeout_sec"));
  }

  @Test
  void testParseArgsCleanStartOptions() throws Exception {
    String[] defaultArgs = {
        "--gcp_project_id=my-project",
        "--pubsub_topic_id=my-topic",
        "--mqtt_client_id=my-client"
    };
    CommandLine defaultCommandLine = MqttToPubSubBridge.parseArgs(defaultArgs);
    org.junit.jupiter.api.Assertions.assertFalse(
        defaultCommandLine.hasOption("mqtt_clean_start")
            || defaultCommandLine.hasOption("clean_start"));

    String[] cleanStartArgs = {
        "--gcp_project_id=my-project",
        "--pubsub_topic_id=my-topic",
        "--mqtt_client_id=my-client",
        "--mqtt_clean_start"
    };
    CommandLine cleanStartCommandLine = MqttToPubSubBridge.parseArgs(cleanStartArgs);
    org.junit.jupiter.api.Assertions.assertTrue(
        cleanStartCommandLine.hasOption("mqtt_clean_start"));

    String[] cleanStartAliasArgs = {
        "--gcp_project_id=my-project",
        "--pubsub_topic_id=my-topic",
        "--mqtt_client_id=my-client",
        "--clean_start"
    };
    CommandLine cleanStartAliasCommandLine = MqttToPubSubBridge.parseArgs(cleanStartAliasArgs);
    org.junit.jupiter.api.Assertions.assertTrue(
        cleanStartAliasCommandLine.hasOption("clean_start"));
  }

  @Test
  void testGracefulShutdown() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.isConnected()).thenReturn(true);
    Publisher mockPublisher = mock(Publisher.class);
    when(mockPublisher.awaitTermination(any(Long.class), any(java.util.concurrent.TimeUnit.class)))
        .thenReturn(true);
    EtcdDataProvider mockEtcdProvider = mock(EtcdDataProvider.class);
    MqttToPubSubBridge bridge = new MqttToPubSubBridge();

    MqttToPubSubBridge.gracefulShutdown(
        bridge, mockMqttClient, mockPublisher, mockEtcdProvider);

    verify(mockMqttClient).disconnect();
    verify(mockPublisher).shutdown();
    verify(mockPublisher).awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
    verify(mockEtcdProvider).shutdown();
  }

  @Test
  void testBridgeShutdownWithTimeout() {
    MqttToPubSubBridge bridge = new MqttToPubSubBridge();
    bridge.shutdown(2, java.util.concurrent.TimeUnit.SECONDS);
  }

  @Test
  void testQueueWorkersExitImmediatelyWhenStopping() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());
    mqttMessage.setId(6002);
    mqttMessage.setQos(1);

    String testTopic = "/r/my-registry/d/my-device/events";
    MqttToPubSubBridge bridge = new MqttToPubSubBridge();
    bridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);
    bridge.setStopping(true);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    // Verify publisher is never called because bridge is stopping
    Thread.sleep(1000);
    verify(mockPublisher, org.mockito.Mockito.never()).publish(any(PubsubMessage.class));
  }

  @Test
  void testRejectedExecutionMarksMessageAbandoned() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.getClientId()).thenReturn("test-client");
    Publisher mockPublisher = mock(Publisher.class);
    String payloadStr = "Hello World";
    final MqttMessage mqttMessage = new MqttMessage(payloadStr.getBytes());
    mqttMessage.setId(7007);
    mqttMessage.setQos(1);

    String testTopic = "/r/my-registry/d/my-device/events";
    MqttToPubSubBridge bridge = new MqttToPubSubBridge();
    bridge.setupBridge(mockMqttClient, mockPublisher, testTopic, null);

    // Shut down executor to trigger RejectedExecutionException upon messageArrived
    bridge.shutdown(100, java.util.concurrent.TimeUnit.MILLISECONDS);

    ArgumentCaptor<MqttCallback> callbackCaptor =
        ArgumentCaptor.forClass(MqttCallback.class);
    verify(mockMqttClient).setCallback(callbackCaptor.capture());
    MqttCallback callback = callbackCaptor.getValue();

    callback.messageArrived(testTopic, mqttMessage);

    // Verify message is recorded as ABANDONED in session
    assertEquals(1, bridge.getUnackedCount());
    assertEquals(1, bridge.getAbandonedCount());
    String expectedKey = MqttToPubSubBridge.getMessageHash(testTopic, mqttMessage);
    assertEquals(MqttToPubSubBridge.MessageState.ABANDONED, bridge.getMessageState(expectedKey));
  }

  @Test
  void testStartPeriodicReconnect() throws Exception {
    IMqttClient mockMqttClient = mock(IMqttClient.class);
    when(mockMqttClient.isConnected()).thenReturn(true);
    MqttToPubSubBridge bridge = new MqttToPubSubBridge();

    // Call startPeriodicReconnect with 1 second interval
    bridge.startPeriodicReconnect(mockMqttClient, 1);

    // Verify disconnect(1000L) and reconnect() are called
    verify(mockMqttClient, org.mockito.Mockito.timeout(5000)).disconnect(1000L);
    verify(mockMqttClient, org.mockito.Mockito.timeout(5000)).reconnect();
  }
}


