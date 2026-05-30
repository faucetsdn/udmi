package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.ProcessorBase.FUNCTIONS_VERSION_MAX;
import static com.google.bos.udmi.service.core.ProcessorBase.FUNCTIONS_VERSION_MIN;
import static com.google.udmi.util.JsonUtil.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.SetupUdmiState;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

/**
 * Tests for the UUFI processor function.
 */
public class UufiProcessorTest extends ProcessorTestBase {

  private void activeTestInstance(Runnable action) {
    action.run();
    terminateAndWait();
  }

  /**
   * Initializes the test instance before each test.
   */
  @BeforeEach
  public void initializeInstance() {
    writeVersionDeployFile();
    UufiProcessor processor = initializeTestInstance(UufiProcessor.class);
    UdmiServicePod.putComponent(ContainerBase.getName(UufiProcessor.class), () -> processor);
  }

  /**
   * Test that the UUFI handshake works.
   */
  @Test
  public void handshakeTest() {
    UdmiState state = new UdmiState();
    state.setup = new SetupUdmiState();
    state.setup.user = TEST_USER;
    state.setup.transaction_id = "test-txn";
    
    Envelope envelope = new Envelope();
    envelope.subType = SubType.STATE;
    envelope.subFolder = SubFolder.UDMI;
    envelope.source = "test-client";
    envelope.transactionId = "test-txn";

    activeTestInstance(() -> getReverseDispatcher().publish(new Bundle(envelope, state)));

    // One message should be published back (the config)
    assertEquals(1, captured.size(), "captured message count");
    UdmiConfig config = (UdmiConfig) captured.get(0);
    
    assertEquals(FUNCTIONS_VERSION_MIN, config.setup.functions_min);
    assertEquals(FUNCTIONS_VERSION_MAX, config.setup.functions_max);
    assertEquals("test-txn", config.reply.transaction_id);
  }

  /**
   * Test that inbound UUFI-wrapped messages are correctly unwrapped and routed.
   */
  @Test
  public void inboundRoutingTest() {
    Envelope innerEnvelope = new Envelope();
    innerEnvelope.subType = SubType.EVENTS;
    innerEnvelope.subFolder = SubFolder.POINTSET;
    innerEnvelope.deviceId = "dev-1";
    innerEnvelope.deviceRegistryId = "reg-1";
    
    Map<String, Object> uufiWrapper = toMap(innerEnvelope);
    uufiWrapper.put("payload", Map.of("points", Map.of("temp", 22)));

    Envelope transportEnvelope = new Envelope();
    transportEnvelope.source = "test-client";

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    // The unwrapped message should be published to the internal bus
    assertEquals(1, captured.size(), "captured message count");
    Map<String, Object> unwrapped = toMap(captured.get(0));
    assertNotNull(unwrapped.get("points"));
  }

  /**
   * Test that outbound system messages are correctly wrapped for UUFI clients.
   */
  @Test
  public void outboundWrappingTest() {
    Envelope systemEnvelope = new Envelope();
    systemEnvelope.subType = SubType.EVENTS;
    systemEnvelope.subFolder = SubFolder.POINTSET;
    systemEnvelope.deviceId = "dev-1";
    systemEnvelope.deviceRegistryId = "reg-1";
    
    Map<String, Object> payload = Map.of("points", Map.of("temp", 25));

    // Send the system message to the processor's input
    activeTestInstance(() -> getReverseDispatcher().publish(new Bundle(systemEnvelope, payload)));

    // The wrapped message should be published to the UUFI client (reverse pipe)
    assertEquals(1, captured.size(), "captured message count");
    Map<String, Object> wrapped = toMap(captured.get(0));
    assertNotNull(wrapped.get("payload"), "wrapped payload should not be null");
    assertEquals(SubType.EVENTS.value(), wrapped.get("subType"));
  }
  
  private UufiProcessor getProcessor() {
    return (UufiProcessor) UdmiServicePod.getComponent(UufiProcessor.class);
  }
}
