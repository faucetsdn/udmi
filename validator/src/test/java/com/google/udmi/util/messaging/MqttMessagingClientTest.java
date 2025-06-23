package com.google.udmi.util.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.pubsub.v1.PubsubMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for MqttMessagingClient class.
 */
@RunWith(MockitoJUnitRunner.class)
public class MqttMessagingClientTest {

  private static final String TEST_BROKER = "tcp://localhost:1883";
  private static final String TEST_TOPIC = "test/topic";

  private MockedConstruction<MqttClient> mockMqttClientConstruction;
  private IMqttMessageListener capturedListener;
  private MqttClient mockMqttClient;

  /**
   * Set up by mocking the MQTT client.
   */
  @Before
  public void setUp() {
    mockMqttClientConstruction = Mockito.mockConstruction(MqttClient.class,
        (mock, context) -> {
          mockMqttClient = mock;

          doAnswer(invocation -> {
            capturedListener = invocation.getArgument(1);
            return null;
          }).when(mock).subscribe(any(String.class), any(IMqttMessageListener.class));

          when(mock.isConnected()).thenReturn(true);
        });
  }

  /**
   * Ensure the mocked client is closed.
   */
  @After
  public void tearDown() {
    if (mockMqttClientConstruction != null) {
      mockMqttClientConstruction.close();
    }
  }

  @Test
  public void constructor_happyPath_connectsAndSubscribes() throws MqttException {
    // Act
    new MqttMessagingClient(TEST_BROKER, TEST_TOPIC, null);

    // Assert
    assertNotNull("A mock MqttClient should have been constructed", mockMqttClient);
    verify(mockMqttClient).connect(any(MqttConnectOptions.class));
    verify(mockMqttClient).subscribe(any(String.class), any(IMqttMessageListener.class));
    assertNotNull("An IMqttMessageListener should have been captured", capturedListener);
  }

  @Test(expected = RuntimeException.class)
  public void constructor_whenConnectFails_throwsRuntimeException() throws MqttException {
    // Arrange: Redefine the mock to throw an exception on connect
    doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
        .when(mockMqttClient).connect(any(MqttConnectOptions.class));

    // Act
    new MqttMessagingClient(TEST_BROKER, TEST_TOPIC, null);
  }

  @Test
  public void receiveMessage_structuredJson_isParsedCorrectly() throws Exception {
    // Arrange
    MqttMessagingClient client = new MqttMessagingClient(TEST_BROKER, TEST_TOPIC, null);
    String dataPayload = "{\"device_id\": \"ABC-123\"}";
    String escapedDataPayload = dataPayload.replace("\"", "\\\"");
    String fullPayload = String.format(
        "{\"data\": \"%s\", \"attributes\": {\"key1\": \"val1\"}}", escapedDataPayload);

    MqttMessage mqttMessage = new MqttMessage(fullPayload.getBytes(StandardCharsets.UTF_8));

    // Act: Manually trigger the listener to simulate a message arrival
    capturedListener.messageArrived(TEST_TOPIC, mqttMessage);
    PubsubMessage pubsubMessage = client.poll(Duration.ofSeconds(1));

    // Assert
    assertNotNull(pubsubMessage);
    assertEquals(dataPayload, pubsubMessage.getData().toStringUtf8());
    assertEquals("val1", pubsubMessage.getAttributesMap().get("key1"));
  }

  @Test
  public void receiveMessage_simpleString_isHandledAsFallback() throws Exception {
    // Arrange
    MqttMessagingClient client = new MqttMessagingClient(TEST_BROKER, TEST_TOPIC, null);
    String simplePayload = "this is not a json object";
    MqttMessage mqttMessage = new MqttMessage(simplePayload.getBytes(StandardCharsets.UTF_8));

    // Act
    capturedListener.messageArrived(TEST_TOPIC, mqttMessage);
    PubsubMessage pubsubMessage = client.poll(Duration.ofSeconds(1));

    // Assert
    assertNotNull(pubsubMessage);
    assertEquals(simplePayload, pubsubMessage.getData().toStringUtf8());
    assertTrue(pubsubMessage.getAttributesMap().isEmpty());
  }

  @Test
  public void receiveMessage_malformedJson_isHandledGracefully() throws Exception {
    // Arrange
    MqttMessagingClient client = new MqttMessagingClient(TEST_BROKER, TEST_TOPIC, null);
    // Malformed JSON (missing closing brace)
    String malformedPayload = "{\"data\": \"some_data\"";
    MqttMessage mqttMessage = new MqttMessage(malformedPayload.getBytes(StandardCharsets.UTF_8));

    // Act
    capturedListener.messageArrived(TEST_TOPIC, mqttMessage);
    // The error should be caught and logged, but nothing should be added to the queue
    PubsubMessage pubsubMessage = client.poll(Duration.ofMillis(50));

    // Assert
    assertNull("Message queue should be empty after a parsing error", pubsubMessage);
  }

  @Test
  public void poll_whenQueueEmpty_returnsNullAfterTimeout() {
    // Arrange
    MqttMessagingClient client = new MqttMessagingClient(TEST_BROKER, TEST_TOPIC, null);

    // Act
    PubsubMessage pubsubMessage = client.poll(Duration.ofMillis(50));

    // Assert
    assertNull(pubsubMessage);
  }

  @Test
  public void close_whenConnected_disconnectsAndCloses() throws MqttException {
    // Arrange
    MqttMessagingClient client = new MqttMessagingClient(TEST_BROKER, TEST_TOPIC, null);
    when(mockMqttClient.isConnected()).thenReturn(true);

    // Act
    client.close();

    // Assert
    verify(mockMqttClient).disconnect();
    verify(mockMqttClient).close();
  }

  @Test
  public void close_whenNotConnected_onlyCloses() throws MqttException {
    // Arrange
    MqttMessagingClient client = new MqttMessagingClient(TEST_BROKER, TEST_TOPIC, null);
    when(mockMqttClient.isConnected()).thenReturn(false);

    // Act
    client.close();

    // Assert
    verify(mockMqttClient, never()).disconnect();
    verify(mockMqttClient).close();
  }
}