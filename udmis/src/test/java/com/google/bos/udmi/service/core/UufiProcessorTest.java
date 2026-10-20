package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.ProcessorBase.FUNCTIONS_VERSION_MAX;
import static com.google.bos.udmi.service.core.ProcessorBase.FUNCTIONS_VERSION_MIN;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.PointsetEvents;
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
    envelope.gatewayId = "uufi";

    activeTestInstance(() -> getReverseDispatcher().publish(new Bundle(envelope, state)));

    // One message should be published back (the config)
    assertEquals(1, captured.size(), "captured message count");
    Map<String, Object> capturedMap = (Map<String, Object>) captured.get(0);
    UdmiConfig config = convertTo(UdmiConfig.class, capturedMap.get("payload"));

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
    transportEnvelope.gatewayId = "uufi";

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    // The unwrapped message should be published to the internal bus
    assertEquals(1, captured.size(), "captured message count");
    Map<String, Object> unwrapped = toMap(captured.get(0));
    assertNotNull(unwrapped.get("points"));
  }

  /**
   * Test that inbound UUFI-wrapped config messages with subFolder are merged via processConfig.
   */
  @Test
  public void inboundConfigRoutingTest() {
    Envelope innerEnvelope = new Envelope();
    innerEnvelope.subType = SubType.CONFIG;
    innerEnvelope.subFolder = SubFolder.POINTSET;
    innerEnvelope.deviceId = "dev-1";
    innerEnvelope.deviceRegistryId = "reg-1";

    Map<String, Object> uufiWrapper = toMap(innerEnvelope);
    Map<String, Object> innerPayload = Map.of(
        "version", "1.5.2",
        "timestamp", "2026-07-28T00:00:00Z",
        "points", Map.of("temp", 22));
    uufiWrapper.put("payload", innerPayload);

    Envelope transportEnvelope = new Envelope();
    transportEnvelope.source = "test-client";
    transportEnvelope.gatewayId = "uufi";

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Function<Entry<Long, String>, String>> configCaptor =
        ArgumentCaptor.forClass(Function.class);

    verify(provider, times(1)).modifyConfig(any(Envelope.class), configCaptor.capture());

    String updatedConfigJson = configCaptor.getValue().apply(new SimpleEntry<>(0L, "{}"));
    Map<String, Object> unwrapped = toMap(updatedConfigJson);
    assertEquals(TEST_VERSION, unwrapped.get("version"));
    assertNotNull(unwrapped.get("timestamp"));
    assertNotNull(unwrapped.get("pointset"), "pointset field present in top-level config");
    @SuppressWarnings("unchecked")
    Map<String, Object> pointsetMap = (Map<String, Object>) unwrapped.get("pointset");
    assertNotNull(pointsetMap.get("points"));
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

  /**
   * Test that inbound UUFI-wrapped messages omitting required device registry/ID
   * are rejected according to strict envelope requirements.
   */
  @Test
  public void inboundRoutingRedundancyTest() {
    Envelope innerEnvelope = new Envelope();
    innerEnvelope.subType = SubType.EVENTS;
    innerEnvelope.subFolder = SubFolder.POINTSET;
    // Omit device identity from the inner payload
    innerEnvelope.deviceId = null;
    innerEnvelope.deviceRegistryId = null;

    Map<String, Object> uufiWrapper = toMap(innerEnvelope);
    uufiWrapper.put("payload", Map.of(
        "points", Map.of(),
        "version", "1",
        "timestamp", "2026-07-17T12:00:00Z"
    ));

    Envelope transportEnvelope = new Envelope();
    transportEnvelope.source = "test-client";
    transportEnvelope.gatewayId = "uufi";
    transportEnvelope.deviceId = "dev-1";
    transportEnvelope.deviceRegistryId = "reg-1";

    List<Envelope> capturedEnvelopes = new ArrayList<>();
    getReverseDispatcher().registerHandler(PointsetEvents.class, (message) -> {
      Envelope env = getReverseDispatcher().getContinuation(message).getEnvelope();
      capturedEnvelopes.add(env);
    });

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    // Verify that the message omitting required device identity was rejected (not published)
    assertEquals(0, capturedEnvelopes.size(), "captured envelopes count");
  }

  /**
   * Test that inbound UUFI-wrapped messages with omitted subType and subFolder
   * are rejected according to strict envelope requirements.
   */
  @Test
  public void inboundRoutingSubTypeRedundancyTest() {
    // Payload map has NO subType/subFolder
    Map<String, Object> uufiWrapper = new java.util.HashMap<>();
    uufiWrapper.put("payload", Map.of(
        "points", Map.of(),
        "version", "1",
        "timestamp", "2026-07-17T12:00:00Z"
    ));

    Envelope transportEnvelope = new Envelope();
    transportEnvelope.source = "test-client";
    transportEnvelope.gatewayId = "uufi";
    transportEnvelope.deviceId = "dev-1";
    transportEnvelope.deviceRegistryId = "reg-1";
    transportEnvelope.subType = SubType.EVENTS;
    transportEnvelope.subFolder = SubFolder.POINTSET;

    List<Envelope> capturedEnvelopes = new ArrayList<>();
    getReverseDispatcher().registerHandler(PointsetEvents.class, (message) -> {
      Envelope env = getReverseDispatcher().getContinuation(message).getEnvelope();
      capturedEnvelopes.add(env);
    });

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    // Verify that the message omitting required subType/subFolder was rejected (not published)
    assertEquals(0, capturedEnvelopes.size(), "captured envelopes count");
  }

  /**
   * Test that inbound UUFI-wrapped messages with conflicting topic coordinates vs inner envelope
   * are rejected according to strict envelope header duplication requirements.
   */
  @Test
  public void inboundRoutingCoordinateMismatchTest() {
    Envelope innerEnvelope = new Envelope();
    innerEnvelope.subType = SubType.CONFIG;
    innerEnvelope.subFolder = SubFolder.SYSTEM;
    innerEnvelope.deviceId = "dev-1";
    innerEnvelope.deviceRegistryId = "reg-1";

    Map<String, Object> uufiWrapper = toMap(innerEnvelope);
    uufiWrapper.put("payload", Map.of(
        "version", "1",
        "timestamp", "2026-07-17T12:00:00Z"
    ));

    Envelope transportEnvelope = new Envelope();
    transportEnvelope.source = "test-client";
    transportEnvelope.gatewayId = "uufi";
    transportEnvelope.deviceId = "dev-1";
    transportEnvelope.deviceRegistryId = "reg-1";
    transportEnvelope.subType = SubType.EVENTS; // Mismatch with inner envelope subType
    transportEnvelope.subFolder = SubFolder.SYSTEM;

    List<Envelope> capturedEnvelopes = new ArrayList<>();
    getReverseDispatcher().registerHandler(PointsetEvents.class, (message) -> {
      Envelope env = getReverseDispatcher().getContinuation(message).getEnvelope();
      capturedEnvelopes.add(env);
    });

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    // Verify that the message with mismatched coordinates was rejected (not published)
    assertEquals(0, capturedEnvelopes.size(), "captured envelopes count");
  }

  /**
   * Test that outbound monolithic state messages (having null or UPDATE subfolder)
   * are correctly sharded into individual sub-blocks and published on compliant paths.
   */
  @Test
  public void outboundMonolithicStateShardingTest() {
    Envelope stateEnvelope = new Envelope();
    stateEnvelope.subType = SubType.STATE;
    stateEnvelope.subFolder = SubFolder.UPDATE;
    stateEnvelope.deviceId = "dev-1";
    stateEnvelope.deviceRegistryId = "reg-1";

    com.google.bos.udmi.service.messaging.StateUpdate monolithicState =
        new com.google.bos.udmi.service.messaging.StateUpdate();
    monolithicState.version = "1";
    monolithicState.timestamp = new java.util.Date();
    monolithicState.system = new udmi.schema.SystemState();
    monolithicState.pointset = new udmi.schema.PointsetState();

    // Send the system monolithic state update message to the processor's input
    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(stateEnvelope, monolithicState)));

    // Since monolithicState has 'system' and 'pointset' fields non-null,
    // it should shard them and publish 2 messages: one for system, one for pointset.
    assertEquals(2, captured.size(), "captured message count should match sharded count");

    Map<String, Object> wrappedMsg1 = toMap(captured.get(0));
    Map<String, Object> wrappedMsg2 = toMap(captured.get(1));

    // Verify subfolders are correctly sharded
    java.util.Set<String> subFolders = new java.util.HashSet<>();
    subFolders.add((String) wrappedMsg1.get("subFolder"));
    subFolders.add((String) wrappedMsg2.get("subFolder"));

    java.util.Set<String> expectedSubFolders =
        java.util.Set.of(SubFolder.SYSTEM.value(), SubFolder.POINTSET.value());
    assertEquals(expectedSubFolders, subFolders, "sharded subfolders mismatch");
  }

  /**
   * Test on-demand Model Discovery via UUFI config/discovery.
   */
  @Test
  public void modelDiscoveryTest() {
    udmi.schema.CloudModel registryModel = new udmi.schema.CloudModel();
    registryModel.device_ids = Map.of(
        "AHU-1", new udmi.schema.CloudModel(),
        "GAT-123", new udmi.schema.CloudModel()
    );
    org.mockito.Mockito.when(provider.listDevices(org.mockito.ArgumentMatchers.eq("reg-1"), any()))
        .thenReturn(registryModel);

    udmi.schema.CloudModel ahuModel = new udmi.schema.CloudModel();
    ahuModel.metadata = Map.of("udmi_metadata",
        "{\"version\":\"1.5.2\",\"timestamp\":\"2026-07-28T00:00:00Z\","
            + "\"system\":{\"serial_no\":\"SN-AHU-1\"}}");
    udmi.schema.CloudModel gatModel = new udmi.schema.CloudModel();
    gatModel.metadata = Map.of("udmi_metadata",
        "{\"version\":\"1.5.2\",\"timestamp\":\"2026-07-28T00:00:00Z\","
            + "\"system\":{\"serial_no\":\"SN-GAT-123\"}}");
    org.mockito.Mockito.when(provider.fetchDevice("reg-1", "AHU-1")).thenReturn(ahuModel);
    org.mockito.Mockito.when(provider.fetchDevice("reg-1", "GAT-123")).thenReturn(gatModel);

    Envelope innerEnvelope = new Envelope();
    innerEnvelope.subType = SubType.CONFIG;
    innerEnvelope.subFolder = SubFolder.DISCOVERY;
    innerEnvelope.deviceRegistryId = "reg-1";

    Map<String, Object> uufiWrapper = toMap(innerEnvelope);
    uufiWrapper.put("payload", Map.of(
        "version", "1.5.2",
        "timestamp", "2026-07-28T00:00:00Z",
        "families", Map.of("iot", Map.of(
            "generation", "2026-07-28T00:00:00Z",
            "depth", "details",
            "source", "model"
        ))
    ));

    Envelope transportEnvelope = new Envelope();
    transportEnvelope.source = "test-client";
    transportEnvelope.gatewayId = "uufi";
    transportEnvelope.deviceRegistryId = "reg-1";
    transportEnvelope.subType = SubType.CONFIG;
    transportEnvelope.subFolder = SubFolder.DISCOVERY;

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    // 5 messages published by ModelDiscoveryHandler to udmi_target:
    // - DiscoveryState (active, active_count=0)
    // - DiscoveryEvents (event_no=0, active_count=2)
    // - DiscoveryEvents (event_no=1, addr=AHU-1, model=...)
    // - DiscoveryEvents (event_no=2, addr=GAT-123, model=...)
    // - DiscoveryState (stopped, active_count=2)
    assertEquals(5, captured.size(), "Expected 5 model discovery messages");

    List<udmi.schema.DiscoveryState> states = captured.stream()
        .filter(o -> o instanceof udmi.schema.DiscoveryState)
        .map(o -> (udmi.schema.DiscoveryState) o)
        .toList();
    assertEquals(2, states.size(), "Expected 2 DiscoveryState bookend messages");

    udmi.schema.DiscoveryState startState = states.stream()
        .filter(s -> s.families.get("iot").phase == udmi.schema.FamilyDiscoveryState.Phase.ACTIVE)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing ACTIVE DiscoveryState"));
    assertEquals(0, startState.families.get("iot").active_count);
    assertEquals("model", startState.families.get("iot").source);

    udmi.schema.DiscoveryState stopState = states.stream()
        .filter(s -> s.families.get("iot").phase == udmi.schema.FamilyDiscoveryState.Phase.STOPPED)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing STOPPED DiscoveryState"));
    assertEquals(2, stopState.families.get("iot").active_count);
    assertEquals("model", stopState.families.get("iot").source);

    List<udmi.schema.DiscoveryEvents> events = captured.stream()
        .filter(o -> o instanceof udmi.schema.DiscoveryEvents)
        .map(o -> (udmi.schema.DiscoveryEvents) o)
        .toList();
    assertEquals(3, events.size(), "Expected 3 DiscoveryEvents messages");

    udmi.schema.DiscoveryEvents startEvent = events.stream()
        .filter(e -> Integer.valueOf(0).equals(e.event_no))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing event_no=0 DiscoveryEvents"));
    assertEquals(2, startEvent.active_count);
    assertEquals("iot", startEvent.family);
    assertEquals("model", startEvent.source);

    udmi.schema.DiscoveryEvents devEvent1 = events.stream()
        .filter(e -> Integer.valueOf(1).equals(e.event_no))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing event_no=1 DiscoveryEvents"));
    assertEquals("AHU-1", devEvent1.addr);
    assertEquals("model", devEvent1.source);
    assertNotNull(devEvent1.model);
    assertEquals("SN-AHU-1", devEvent1.model.system.serial_no);

    udmi.schema.DiscoveryEvents devEvent2 = events.stream()
        .filter(e -> Integer.valueOf(2).equals(e.event_no))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing event_no=2 DiscoveryEvents"));
    assertEquals("GAT-123", devEvent2.addr);
    assertEquals("model", devEvent2.source);
    assertNotNull(devEvent2.model);
    assertEquals("SN-GAT-123", devEvent2.model.system.serial_no);
  }

  /**
   * Test Model Discovery with depth=entries (count only) and addrs filter.
   */
  @Test
  public void modelDiscoveryEntriesAndAddrsTest() {
    udmi.schema.CloudModel registryModel = new udmi.schema.CloudModel();
    registryModel.device_ids = Map.of(
        "AHU-1", new udmi.schema.CloudModel(),
        "GAT-123", new udmi.schema.CloudModel()
    );
    org.mockito.Mockito.when(provider.listDevices(org.mockito.ArgumentMatchers.eq("reg-1"), any()))
        .thenReturn(registryModel);

    Envelope innerEnvelope = new Envelope();
    innerEnvelope.subType = SubType.CONFIG;
    innerEnvelope.subFolder = SubFolder.DISCOVERY;
    innerEnvelope.deviceRegistryId = "reg-1";

    Map<String, Object> uufiWrapper = toMap(innerEnvelope);
    uufiWrapper.put("payload", Map.of(
        "version", "1.5.2",
        "timestamp", "2026-07-28T00:00:00Z",
        "families", Map.of("iot", Map.of(
            "generation", "2026-07-28T00:00:00Z",
            "depth", "entries",
            "addrs", List.of("AHU-1"),
            "source", "model"
        ))
    ));

    Envelope transportEnvelope = new Envelope();
    transportEnvelope.source = "test-client";
    transportEnvelope.gatewayId = "uufi";
    transportEnvelope.deviceRegistryId = "reg-1";
    transportEnvelope.subType = SubType.CONFIG;
    transportEnvelope.subFolder = SubFolder.DISCOVERY;

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    // With depth=entries, only 3 messages: start state, event_no=0 count, stop state
    assertEquals(3, captured.size(), "Expected 3 messages for depth=entries");
    udmi.schema.DiscoveryEvents startEvent = captured.stream()
        .filter(o -> o instanceof udmi.schema.DiscoveryEvents)
        .map(o -> (udmi.schema.DiscoveryEvents) o)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing DiscoveryEvents for depth=entries"));
    assertEquals(0, startEvent.event_no);
    assertEquals(1, startEvent.active_count);
  }

  private UufiProcessor getProcessor() {
    return (UufiProcessor) UdmiServicePod.getComponent(UufiProcessor.class);
  }
}
