package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.ProcessorBase.FUNCTIONS_VERSION_MAX;
import static com.google.bos.udmi.service.core.ProcessorBase.FUNCTIONS_VERSION_MIN;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.MessageDispatcher.SentinelPayload;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.schema.Category;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Auth_type;
import udmi.schema.Credential;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.Level;
import udmi.schema.PointsetEvents;
import udmi.schema.SetupUdmiState;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;
import udmi.schema.UufiControl;

/**
 * Tests for the UUFI processor function.
 */
public class UufiProcessorTest extends ProcessorTestBase {

  private static final String TEST_PRINCIPAL = "test-client";

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

    assertEquals(1, captured.size(), "captured message count");
    @SuppressWarnings("unchecked")
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
    innerEnvelope.principal = TEST_PRINCIPAL;

    Map<String, Object> uufiWrapper = toMap(innerEnvelope);
    uufiWrapper.put("payload", Map.of("points", Map.of("temp", 22)));

    Envelope transportEnvelope = new Envelope();
    transportEnvelope.source = "test-client";
    transportEnvelope.gatewayId = "uufi";

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    assertEquals(1, captured.size(), "captured message count");
    Map<String, Object> unwrapped = toMap(captured.get(0));
    assertNotNull(unwrapped.get("points"));
  }

  /**
   * Test that inbound UUFI-wrapped config messages with subFolder are merged via processConfig
   * and emit a same-channel Success Ack preserving principal and transactionId.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void inboundConfigRoutingTest() {
    when(provider.modifyConfig(any(Envelope.class), any(Function.class))).thenAnswer(inv -> {
      Function<Entry<Long, String>, String> munger = inv.getArgument(1);
      return munger.apply(new SimpleEntry<>(0L, "{}"));
    });

    Envelope innerEnvelope = new Envelope();
    innerEnvelope.subType = SubType.CONFIG;
    innerEnvelope.subFolder = SubFolder.POINTSET;
    innerEnvelope.deviceId = "dev-1";
    innerEnvelope.deviceRegistryId = "reg-1";
    innerEnvelope.principal = TEST_PRINCIPAL;
    innerEnvelope.transactionId = "UUFI:cfg-1";

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

    ArgumentCaptor<Function<Entry<Long, String>, String>> configCaptor =
        ArgumentCaptor.forClass(Function.class);

    verify(provider, times(1)).modifyConfig(any(Envelope.class), configCaptor.capture());

    String updatedConfigJson = configCaptor.getValue().apply(new SimpleEntry<>(0L, "{}"));
    Map<String, Object> unwrapped = toMap(updatedConfigJson);
    assertEquals(TEST_VERSION, unwrapped.get("version"));
    assertNotNull(unwrapped.get("timestamp"));
    assertNotNull(unwrapped.get("pointset"), "pointset field present in top-level config");
    Map<String, Object> pointsetMap = (Map<String, Object>) unwrapped.get("pointset");
    assertNotNull(pointsetMap.get("points"));

    // Verify 2 publications: internal bus config forward + same-channel UUFI Success Ack
    assertEquals(2, captured.size(), "expected internal forward + UUFI Success Ack");
    int ackIdx = -1;
    for (int i = 0; i < capturedEnvelopes.size(); i++) {
      if ("uufi".equals(capturedEnvelopes.get(i).gatewayId)) {
        ackIdx = i;
        break;
      }
    }
    assertTrue(ackIdx >= 0, "expected UUFI Success Ack envelope");
    Envelope ackEnv = capturedEnvelopes.get(ackIdx);
    assertEquals("uufi", ackEnv.gatewayId);
    assertEquals("udmis", ackEnv.source);
    assertEquals(TEST_PRINCIPAL, ackEnv.principal);
    assertEquals("UUFI:cfg-1", ackEnv.transactionId);
    assertEquals(SubType.CONFIG, ackEnv.subType);
    assertEquals(SubFolder.POINTSET, ackEnv.subFolder);
    Map<String, Object> ackWrapper = captured.stream()
        .map(com.google.udmi.util.JsonUtil::toMap)
        .filter(m -> "udmis".equals(m.get("source")) && m.containsKey("payload"))
        .findFirst()
        .orElseThrow();
    Map<String, Object> ackPayload = (Map<String, Object>) ackWrapper.get("payload");
    assertEquals(TEST_VERSION, ackPayload.get("version"));
    assertNotNull(ackPayload.get("timestamp"));
    assertNotNull(ackPayload.get("points"));
  }

  /**
   * Test that Client -> System messages without principal are dropped without reply.
   */
  @Test
  public void inboundMissingPrincipalDroppedWithoutReplyTest() {
    Envelope writeEnv = new Envelope();
    writeEnv.subType = SubType.CONFIG;
    writeEnv.subFolder = SubFolder.POINTSET;
    writeEnv.deviceId = "dev-1";
    writeEnv.deviceRegistryId = "reg-1";
    writeEnv.transactionId = "UUFI:no-principal-1";

    Map<String, Object> writeWrapper = toMap(writeEnv);
    writeWrapper.put("payload", Map.of("points", Map.of("temp", 22)));

    Map<String, Object> queryWrapper = toMap(writeEnv);
    queryWrapper.put("payload", null);

    Envelope transportEnvelope = new Envelope();
    transportEnvelope.source = "test-client";
    transportEnvelope.gatewayId = "uufi";
    transportEnvelope.subType = SubType.CONFIG;
    transportEnvelope.subFolder = SubFolder.POINTSET;

    activeTestInstance(() -> {
      getReverseDispatcher().publish(new Bundle(transportEnvelope, writeWrapper));
      getReverseDispatcher().publish(new Bundle(transportEnvelope, queryWrapper));
    });

    assertEquals(0, captured.size(), "Messages without principal must be dropped without reply");
  }

  /**
   * Test that client-originated payloads with root "control" are dropped without reply.
   */
  @Test
  public void inboundClientControlDroppedWithoutReplyTest() {
    Envelope innerEnvelope = new Envelope();
    innerEnvelope.subType = SubType.CONFIG;
    innerEnvelope.subFolder = SubFolder.POINTSET;
    innerEnvelope.deviceId = "dev-1";
    innerEnvelope.deviceRegistryId = "reg-1";
    innerEnvelope.principal = TEST_PRINCIPAL;
    innerEnvelope.transactionId = "UUFI:ctrl-loop";

    Map<String, Object> uufiWrapper = toMap(innerEnvelope);
    uufiWrapper.put("payload", Map.of(
        "version", TEST_VERSION,
        "timestamp", "2026-11-24T19:00:01Z",
        "control", Map.of("status", Map.of(
            "message", "Client control attempt",
            "category", Category.SYSTEM_CONFIG_PARSE,
            "level", 500,
            "timestamp", "2026-11-24T19:00:01Z"
        ))));

    Envelope transportEnvelope = new Envelope();
    transportEnvelope.source = "test-client";
    transportEnvelope.gatewayId = "uufi";
    transportEnvelope.subType = SubType.CONFIG;
    transportEnvelope.subFolder = SubFolder.POINTSET;

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    assertEquals(0, captured.size(), "Client-originated control messages must drop without reply");
  }

  /**
   * Test single-channel payload:null queries for config, state, model, and absent targets.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void inboundSingleChannelQueriesTest() {
    String storedConfig = stringify(Map.of(
        "version", TEST_VERSION,
        "timestamp", "2026-11-24T19:00:00Z",
        "pointset", Map.of("points", Map.of("temp", Map.of("set_value", 21)))));
    when(provider.fetchConfig("reg-1", "dev-1")).thenReturn(new SimpleEntry<>(1L, storedConfig));

    String storedState = stringify(Map.of(
        "version", TEST_VERSION,
        "timestamp", "2026-11-24T19:00:02Z",
        "blobset", Map.of("blobs", Map.of("system", Map.of("phase", "applied")))));
    when(provider.fetchState("reg-1", "dev-1")).thenReturn(storedState);

    CloudModel storedDevice = new CloudModel();
    storedDevice.version = TEST_VERSION;
    storedDevice.metadata = Map.of("udmi_metadata",
        "{\"system\":{\"guid\":\"guid-123\",\"software\":{\"system\":\"2.0.0\"}}}");
    when(provider.fetchDevice("reg-1", "dev-1")).thenReturn(storedDevice);
    when(provider.fetchState("reg-1", "dev-missing")).thenReturn(null);

    Envelope transport = new Envelope();
    transport.source = "app-1";
    transport.gatewayId = "uufi";
    transport.deviceRegistryId = "reg-1";

    Map<String, Object> configQuery = new HashMap<>();
    configQuery.put("subType", "config");
    configQuery.put("subFolder", "pointset");
    configQuery.put("deviceRegistryId", "reg-1");
    configQuery.put("deviceId", "dev-1");
    configQuery.put("principal", TEST_PRINCIPAL);
    configQuery.put("transactionId", "UUFI:q-cfg");
    configQuery.put("payload", null);

    Map<String, Object> stateQuery = new HashMap<>(configQuery);
    stateQuery.put("subType", "state");
    stateQuery.put("subFolder", "blobset");
    stateQuery.put("transactionId", "UUFI:q-state");

    Map<String, Object> modelQuery = new HashMap<>(configQuery);
    modelQuery.put("subType", "model");
    modelQuery.put("subFolder", "system");
    modelQuery.put("transactionId", "UUFI:q-model");

    Map<String, Object> missingQuery = new HashMap<>(stateQuery);
    missingQuery.put("deviceId", "dev-missing");
    missingQuery.put("transactionId", "UUFI:q-missing");

    activeTestInstance(() -> {
      getReverseDispatcher().publish(new Bundle(transport, configQuery));
      getReverseDispatcher().publish(new Bundle(transport, stateQuery));
      getReverseDispatcher().publish(new Bundle(transport, modelQuery));
      getReverseDispatcher().publish(new Bundle(transport, missingQuery));
    });

    assertEquals(4, captured.size(), "expected 4 query replies");
    Map<String, Map<String, Object>> byTxn = new HashMap<>();
    for (Object item : captured) {
      Map<String, Object> m = toMap(item);
      byTxn.put((String) m.get("transactionId"), m);
    }

    Map<String, Object> cfgReply = byTxn.get("UUFI:q-cfg");
    assertEquals("udmis", cfgReply.get("source"));
    assertEquals(TEST_PRINCIPAL, cfgReply.get("principal"));
    assertEquals("config", cfgReply.get("subType"));
    assertEquals("pointset", cfgReply.get("subFolder"));
    assertNotNull(((Map<String, Object>) cfgReply.get("payload")).get("points"));

    Map<String, Object> stateReply = byTxn.get("UUFI:q-state");
    assertEquals("state", stateReply.get("subType"));
    assertEquals("blobset", stateReply.get("subFolder"));
    assertNotNull(((Map<String, Object>) stateReply.get("payload")).get("blobs"));

    Map<String, Object> modelReply = byTxn.get("UUFI:q-model");
    assertEquals("model", modelReply.get("subType"));
    assertEquals("system", modelReply.get("subFolder"));
    assertEquals("guid-123", modelReply.get("deviceGuid"));
    assertNotNull(((Map<String, Object>) modelReply.get("payload")).get("software"));

    Map<String, Object> missingReply = byTxn.get("UUFI:q-missing");
    assertEquals("missing", missingReply.get("payload"),
        "Absent query target should return 'missing' string sentinel");
  }

  /**
   * Test unknown client string sentinels and retired SubType.QUERY return UufiControl level 500.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void inboundUnknownSentinelAndRetiredQueryReturnControlErrorTest() {
    Envelope transport = new Envelope();
    transport.source = "app-1";
    transport.gatewayId = "uufi";
    transport.deviceRegistryId = "reg-1";
    transport.deviceId = "dev-1";

    Map<String, Object> badSentinel = new HashMap<>();
    badSentinel.put("subType", "config");
    badSentinel.put("subFolder", "pointset");
    badSentinel.put("deviceRegistryId", "reg-1");
    badSentinel.put("deviceId", "dev-1");
    badSentinel.put("principal", TEST_PRINCIPAL);
    badSentinel.put("transactionId", "UUFI:bad-sentinel");
    badSentinel.put("payload", "bogus_token");

    Map<String, Object> retiredQuery = new HashMap<>(badSentinel);
    retiredQuery.put("subType", "query");
    retiredQuery.put("subFolder", "system");
    retiredQuery.put("transactionId", "UUFI:retired-query");
    retiredQuery.put("payload", Map.of());

    activeTestInstance(() -> {
      getReverseDispatcher().publish(new Bundle(transport, badSentinel));
      getReverseDispatcher().publish(new Bundle(transport, retiredQuery));
    });

    assertEquals(2, captured.size(), "expected 2 control error replies");
    for (Object item : captured) {
      Map<String, Object> wrapper = toMap(item);
      assertEquals("udmis", wrapper.get("source"));
      assertEquals(TEST_PRINCIPAL, wrapper.get("principal"));
      UufiControl control = convertTo(UufiControl.class, wrapper.get("payload"));
      assertNotNull(control.control);
      assertNotNull(control.control.status);
      assertEquals(Level.ERROR.value(), control.control.status.level);
    }
  }

  /**
   * Test that SubFolder.ERROR and SubType.ERRORS are excluded from UUFI inbound and outbound paths.
   */
  @Test
  public void errorChannelExcludedInboundAndOutboundTest() {
    Envelope outboundErrorFolder = new Envelope();
    outboundErrorFolder.subType = SubType.EVENTS;
    outboundErrorFolder.subFolder = SubFolder.ERROR;
    outboundErrorFolder.deviceRegistryId = "reg-1";
    outboundErrorFolder.deviceId = "dev-1";

    Envelope outboundErrorsType = new Envelope();
    outboundErrorsType.subType = SubType.ERRORS;
    outboundErrorsType.subFolder = SubFolder.SYSTEM;
    outboundErrorsType.deviceRegistryId = "reg-1";
    outboundErrorsType.deviceId = "dev-1";

    Envelope inboundErrorFolder = new Envelope();
    inboundErrorFolder.gatewayId = "uufi";
    inboundErrorFolder.source = "client-1";
    inboundErrorFolder.subType = SubType.EVENTS;
    inboundErrorFolder.subFolder = SubFolder.ERROR;

    Map<String, Object> inboundWrapper = Map.of(
        "subType", "events",
        "subFolder", "error",
        "deviceRegistryId", "reg-1",
        "deviceId", "dev-1",
        "principal", TEST_PRINCIPAL,
        "payload", Map.of("message", "bad"));

    activeTestInstance(() -> {
      getReverseDispatcher().publish(new Bundle(outboundErrorFolder, Map.of("error", "boom")));
      getReverseDispatcher().publish(new Bundle(outboundErrorsType, Map.of("error", "boom")));
      getReverseDispatcher().publish(new Bundle(inboundErrorFolder, inboundWrapper));
    });

    assertEquals(0, captured.size(), "Error channels must be excluded from UUFI");
  }

  /**
   * Test that outbound system messages are correctly wrapped for UUFI clients,
   * preserving transactionId and clearing principal on unsolicited broadcast streams.
   */
  @Test
  public void outboundWrappingTest() {
    Envelope systemEnvelope = new Envelope();
    systemEnvelope.subType = SubType.EVENTS;
    systemEnvelope.subFolder = SubFolder.POINTSET;
    systemEnvelope.deviceId = "dev-1";
    systemEnvelope.deviceRegistryId = "reg-1";
    systemEnvelope.transactionId = "SYS:txn-99";
    systemEnvelope.principal = "should-be-cleared-for-broadcast";

    Map<String, Object> payload = Map.of("points", Map.of("temp", 25));

    activeTestInstance(() -> getReverseDispatcher().publish(new Bundle(systemEnvelope, payload)));

    assertEquals(1, captured.size(), "captured message count");
    Map<String, Object> wrapped = toMap(captured.get(0));
    Envelope outEnv = capturedEnvelopes.get(0);
    assertNotNull(wrapped.get("payload"), "wrapped payload should not be null");
    assertEquals(SubType.EVENTS.value(), wrapped.get("subType"));
    assertEquals("SYS:txn-99", outEnv.transactionId, "transactionId must be preserved");
    assertNull(outEnv.principal, "Unsolicited event stream must have null principal");
    assertNull(wrapped.get("principal"));
  }

  /**
   * Test that inbound UUFI-wrapped messages omitting required device registry/ID
   * are rejected and emit a UufiControl error reply when principal is present.
   */
  @Test
  public void inboundRoutingRedundancyTest() {
    Envelope innerEnvelope = new Envelope();
    innerEnvelope.subType = SubType.EVENTS;
    innerEnvelope.subFolder = SubFolder.POINTSET;
    innerEnvelope.principal = TEST_PRINCIPAL;
    innerEnvelope.transactionId = "UUFI:err-dev";
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
    transportEnvelope.subType = SubType.EVENTS;
    transportEnvelope.subFolder = SubFolder.POINTSET;

    List<Envelope> forwardedEvents = new ArrayList<>();
    getReverseDispatcher().registerHandler(PointsetEvents.class, (message) -> {
      Envelope env = getReverseDispatcher().getContinuation(message).getEnvelope();
      forwardedEvents.add(env);
    });

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    assertEquals(0, forwardedEvents.size(), "invalid message must not forward to internal bus");
    assertEquals(1, captured.size(), "expected UufiControl error reply");
    UufiControl ctrl = convertTo(UufiControl.class, toMap(captured.get(0)).get("payload"));
    assertEquals(Level.ERROR.value(), ctrl.control.status.level);
    assertEquals("UUFI:err-dev", capturedEnvelopes.get(0).transactionId);
    assertEquals(TEST_PRINCIPAL, capturedEnvelopes.get(0).principal);
  }

  /**
   * Test that inbound UUFI-wrapped messages with omitted subType and subFolder
   * are rejected and emit a UufiControl error reply on transport coordinates.
   */
  @Test
  public void inboundRoutingSubTypeRedundancyTest() {
    Map<String, Object> uufiWrapper = new HashMap<>();
    uufiWrapper.put("principal", TEST_PRINCIPAL);
    uufiWrapper.put("transactionId", "UUFI:err-sub");
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

    List<Envelope> forwardedEvents = new ArrayList<>();
    getReverseDispatcher().registerHandler(PointsetEvents.class, (message) -> {
      Envelope env = getReverseDispatcher().getContinuation(message).getEnvelope();
      forwardedEvents.add(env);
    });

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    assertEquals(0, forwardedEvents.size(), "invalid message must not forward to internal bus");
    assertEquals(1, captured.size(), "expected UufiControl error reply");
    UufiControl ctrl = convertTo(UufiControl.class, toMap(captured.get(0)).get("payload"));
    assertEquals(Level.ERROR.value(), ctrl.control.status.level);
  }

  /**
   * Test that inbound UUFI-wrapped messages with conflicting topic coordinates vs inner envelope
   * are rejected and emit a UufiControl error reply.
   */
  @Test
  public void inboundRoutingCoordinateMismatchTest() {
    Envelope innerEnvelope = new Envelope();
    innerEnvelope.subType = SubType.CONFIG;
    innerEnvelope.subFolder = SubFolder.SYSTEM;
    innerEnvelope.deviceId = "dev-1";
    innerEnvelope.deviceRegistryId = "reg-1";
    innerEnvelope.principal = TEST_PRINCIPAL;
    innerEnvelope.transactionId = "UUFI:err-mismatch";

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
    transportEnvelope.subType = SubType.EVENTS;
    transportEnvelope.subFolder = SubFolder.SYSTEM;

    List<Envelope> forwardedEvents = new ArrayList<>();
    getReverseDispatcher().registerHandler(PointsetEvents.class, (message) -> {
      Envelope env = getReverseDispatcher().getContinuation(message).getEnvelope();
      forwardedEvents.add(env);
    });

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(transportEnvelope, uufiWrapper)));

    assertEquals(0, forwardedEvents.size(), "mismatched message must not forward");
    assertEquals(1, captured.size(), "expected UufiControl error reply");
    UufiControl ctrl = convertTo(UufiControl.class, toMap(captured.get(0)).get("payload"));
    assertEquals(Level.ERROR.value(), ctrl.control.status.level);
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
    stateEnvelope.transactionId = "STATE:shard-1";

    com.google.bos.udmi.service.messaging.StateUpdate monolithicState =
        new com.google.bos.udmi.service.messaging.StateUpdate();
    monolithicState.version = "1";
    monolithicState.timestamp = new Date();
    monolithicState.system = new udmi.schema.SystemState();
    monolithicState.pointset = new udmi.schema.PointsetState();

    activeTestInstance(() -> getReverseDispatcher().publish(
        new Bundle(stateEnvelope, monolithicState)));

    assertEquals(2, captured.size(), "captured message count should match sharded count");

    Map<String, Object> wrappedMsg1 = toMap(captured.get(0));
    Map<String, Object> wrappedMsg2 = toMap(captured.get(1));

    Set<String> subFolders = new HashSet<>();
    subFolders.add((String) wrappedMsg1.get("subFolder"));
    subFolders.add((String) wrappedMsg2.get("subFolder"));

    Set<String> expectedSubFolders =
        Set.of(SubFolder.SYSTEM.value(), SubFolder.POINTSET.value());
    assertEquals(expectedSubFolders, subFolders, "sharded subfolders mismatch");
    assertEquals("STATE:shard-1", capturedEnvelopes.get(0).transactionId);
  }

  @Test
  public void outboundSystemModelPropagatesDeviceGuidTest() {
    final String expectedGuid = "11111111-2222-3333-4444-555555555555";
    Envelope systemEnvelope = new Envelope();
    systemEnvelope.subType = SubType.MODEL;
    systemEnvelope.subFolder = SubFolder.SYSTEM;
    systemEnvelope.deviceId = "dev-1";
    systemEnvelope.deviceRegistryId = "reg-1";

    udmi.schema.SystemModel systemModel = new udmi.schema.SystemModel();
    systemModel.guid = expectedGuid;

    Envelope cloudEnvelope = new Envelope();
    cloudEnvelope.subType = SubType.MODEL;
    cloudEnvelope.subFolder = SubFolder.CLOUD;
    cloudEnvelope.deviceId = "dev-2";
    cloudEnvelope.deviceRegistryId = "reg-1";

    udmi.schema.CloudModel cloudModel = new udmi.schema.CloudModel();
    cloudModel.metadata = Map.of(
        "udmi_metadata", "{\"system\":{\"guid\":\"" + expectedGuid + "\"}}");

    activeTestInstance(() -> {
      getReverseDispatcher().publish(new Bundle(systemEnvelope, systemModel));
      getReverseDispatcher().publish(new Bundle(cloudEnvelope, cloudModel));
    });

    assertEquals(2, captured.size(), "captured message count");
    assertEquals(expectedGuid, toMap(captured.get(0)).get("deviceGuid"));
    assertEquals(expectedGuid, capturedEnvelopes.get(0).deviceGuid);
    assertEquals(expectedGuid, toMap(captured.get(1)).get("deviceGuid"));
    assertEquals(expectedGuid, capturedEnvelopes.get(1).deviceGuid);
  }

  /**
   * Test PubSub wire mode with sidecar pipe across loopback, query, config ack, error, progress.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void pubSubModeWithSidecarFullFlowTest() {
    // Shut down default MQTT-style instance and spin up a PubSub + sidecar UufiProcessor
    terminateAndWait();
    UdmiServicePod.resetForTest();
    super.resetAfter();

    final String sidecarQueue = "uufi_sidecar_out";
    final String psRecv = "ps_uufi_in";
    final String psSend = "ps_target_out";

    EndpointConfiguration config = new EndpointConfiguration();
    config.name = "uufi";
    config.protocol = Protocol.LOCAL;
    config.hostname = TEST_NAMESPACE;
    config.recv_id = psRecv;
    config.send_id = psSend;
    config.side_id = sidecarQueue;

    UufiProcessor psProcessor = new UufiProcessor(config);
    psProcessor.setPubSubProtocolForTest(true);
    UdmiServicePod.putComponent(ProcessorBase.IOT_ACCESS_COMPONENT, () -> provider);
    psProcessor.activate();

    // Reverse dispatcher to inject into psRecv and capture from psSend (internal bus)
    EndpointConfiguration mainPeerConfig = new EndpointConfiguration();
    mainPeerConfig.protocol = Protocol.LOCAL;
    mainPeerConfig.hostname = TEST_NAMESPACE;
    mainPeerConfig.recv_id = psSend;
    mainPeerConfig.send_id = psRecv;
    MessageDispatcherImpl mainPeer = new MessageDispatcherImpl(mainPeerConfig);
    List<Object> internalForwards = Collections.synchronizedList(new ArrayList<>());
    mainPeer.registerHandler(Object.class, internalForwards::add);
    mainPeer.activate();

    // Sidecar listener capturing UUFI PubSub replies published to side_id
    EndpointConfiguration sidePeerConfig = new EndpointConfiguration();
    sidePeerConfig.protocol = Protocol.LOCAL;
    sidePeerConfig.hostname = TEST_NAMESPACE;
    sidePeerConfig.recv_id = sidecarQueue;
    MessageDispatcherImpl sidePeer = new MessageDispatcherImpl(sidePeerConfig);
    List<Entry<Envelope, Object>> sideMessages = Collections.synchronizedList(new ArrayList<>());
    sidePeer.registerHandler(Object.class, msg -> {
      Envelope env = sidePeer.getContinuation(msg).getEnvelope();
      sideMessages.add(new SimpleEntry<>(env, msg));
    });
    sidePeer.activate();

    when(provider.modifyConfig(any(Envelope.class), any(Function.class))).thenAnswer(inv -> {
      Function<Entry<Long, String>, String> munger = inv.getArgument(1);
      return munger.apply(new SimpleEntry<>(0L, "{}"));
    });
    when(provider.fetchState(eq("reg-1"), eq("dev-absent"))).thenReturn(null);

    // 1. Loopback with source="pubsub+udmis" must be ignored
    Envelope loopback = new Envelope();
    loopback.subType = SubType.CONFIG;
    loopback.subFolder = SubFolder.POINTSET;
    loopback.deviceRegistryId = "reg-1";
    loopback.deviceId = "dev-1";
    loopback.source = "pubsub+udmis";
    loopback.principal = "app@";
    loopback.transactionId = "UUFI:loop-1";
    mainPeer.publish(new Bundle(loopback, Map.of("points", Map.of())));

    // 2. NullMessage Query on absent state -> unwrapped SentinelPayload("missing") on sidecar
    Envelope queryEnv = new Envelope();
    queryEnv.subType = SubType.STATE;
    queryEnv.subFolder = SubFolder.BLOBSET;
    queryEnv.deviceRegistryId = "reg-1";
    queryEnv.deviceId = "dev-absent";
    queryEnv.source = "pubsub+app";
    queryEnv.principal = "app@";
    queryEnv.transactionId = "UUFI:ps-q1";
    mainPeer.publish(new Bundle(queryEnv, MessageDispatcher.nullMessage()));

    // 3. Valid CONFIG write -> internal forward on mainPeer + unwrapped Success Ack on sidecar
    Envelope cfgEnv = new Envelope();
    cfgEnv.subType = SubType.CONFIG;
    cfgEnv.subFolder = SubFolder.POINTSET;
    cfgEnv.deviceRegistryId = "reg-1";
    cfgEnv.deviceId = "dev-1";
    cfgEnv.source = "pubsub+app";
    cfgEnv.principal = "app@";
    cfgEnv.transactionId = "UUFI:ps-cfg1";
    mainPeer.publish(new Bundle(cfgEnv, Map.of("points", Map.of("temp", Map.of("set_value", 23)))));

    // 4. Unknown SentinelPayload write -> unwrapped UufiControl level 500 on sidecar
    Envelope sentinelEnv = new Envelope();
    sentinelEnv.subType = SubType.CONFIG;
    sentinelEnv.subFolder = SubFolder.POINTSET;
    sentinelEnv.deviceRegistryId = "reg-1";
    sentinelEnv.deviceId = "dev-1";
    sentinelEnv.source = "pubsub+app";
    sentinelEnv.principal = "app@";
    sentinelEnv.transactionId = "UUFI:ps-bad-sentinel";
    mainPeer.publish(new Bundle(sentinelEnv, MessageDispatcher.sentinelPayload("unknown_op")));

    // 5. Interim progress control reply -> unwrapped UufiControl level 200 on sidecar
    psProcessor.sendControlProgress(cfgEnv, "app@", "UUFI:ps-prog",
        Category.SYSTEM_CONFIG_RECEIVE, "Applying step 1/2");

    // Flush queues
    ((MessageDispatcherImpl) psProcessor.getDispatcher()).terminate();
    ((MessageDispatcherImpl) psProcessor.getDispatcher()).awaitShutdown();
    mainPeer.terminate();
    mainPeer.awaitShutdown();
    sidePeer.terminate();
    sidePeer.awaitShutdown();
    psProcessor.shutdown();

    assertEquals(1, internalForwards.size(), "Only valid CONFIG write should forward internally");
    assertEquals(4, sideMessages.size(),
        "Expected 4 unwrapped PubSub replies on sidecar (query, config ack, error, progress)");

    Map<String, Object> replyByTxn = new HashMap<>();
    for (Entry<Envelope, Object> entry : sideMessages) {
      Envelope env = entry.getKey();
      replyByTxn.put(env.transactionId, entry.getValue());
      assertEquals("udmis", env.source);
      assertEquals("app@", env.principal);
      assertNull(env.gatewayId, "PubSub replies must not carry gatewayId=uufi");
    }

    assertTrue(replyByTxn.get("UUFI:ps-q1") instanceof SentinelPayload);
    assertEquals("missing", ((SentinelPayload) replyByTxn.get("UUFI:ps-q1")).value);

    Map<String, Object> cfgAck = toMap(replyByTxn.get("UUFI:ps-cfg1"));
    assertNull(cfgAck.get("payload"), "PubSub Ack payload must be unwrapped (no wrapper key)");
    assertNotNull(cfgAck.get("points"));

    UufiControl errCtrl = convertTo(UufiControl.class, replyByTxn.get("UUFI:ps-bad-sentinel"));
    assertEquals(Level.ERROR.value(), errCtrl.control.status.level);

    UufiControl progCtrl = convertTo(UufiControl.class, replyByTxn.get("UUFI:ps-prog"));
    assertEquals(Level.INFO.value(), progCtrl.control.status.level);
    assertEquals("Applying step 1/2", progCtrl.control.status.message);
  }

  /**
   * Test that PubSub uufi -> udmi_target -> uufi_events wiring avoids spurious config/null
   * echoes and duplicate model/system replies while still forwarding external events and
   * external model/system broadcasts from udmi_target.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void pubSubOutboundTopologyAvoidsDuplicateAcksAndConfigNullTest() {
    terminateAndWait();
    UdmiServicePod.resetForTest();
    super.resetAfter();

    final String psUufiIn = "ps_topo_uufi_in";
    final String psTarget = "ps_topo_target";
    final String psUufiOut = "ps_topo_uufi_out";

    EndpointConfiguration uufiConfig = new EndpointConfiguration();
    uufiConfig.name = "uufi";
    uufiConfig.protocol = Protocol.LOCAL;
    uufiConfig.hostname = TEST_NAMESPACE;
    uufiConfig.recv_id = psUufiIn;
    uufiConfig.send_id = psTarget;
    uufiConfig.side_id = psUufiOut;
    UufiProcessor uufiProc = new UufiProcessor(uufiConfig);
    uufiProc.setPubSubProtocolForTest(true);

    EndpointConfiguration eventsConfig = new EndpointConfiguration();
    eventsConfig.name = "uufi_events";
    eventsConfig.protocol = Protocol.LOCAL;
    eventsConfig.hostname = TEST_NAMESPACE;
    eventsConfig.recv_id = psTarget;
    eventsConfig.send_id = psUufiOut;
    UufiProcessor eventsProc = new UufiProcessor(eventsConfig);
    eventsProc.setPubSubProtocolForTest(true);

    UdmiServicePod.putComponent(ProcessorBase.IOT_ACCESS_COMPONENT, () -> provider);
    uufiProc.activate();
    eventsProc.activate();

    EndpointConfiguration inPeerConfig = new EndpointConfiguration();
    inPeerConfig.protocol = Protocol.LOCAL;
    inPeerConfig.hostname = TEST_NAMESPACE;
    inPeerConfig.send_id = psUufiIn;
    final MessageDispatcherImpl inPeer = new MessageDispatcherImpl(inPeerConfig);

    EndpointConfiguration targetPeerConfig = new EndpointConfiguration();
    targetPeerConfig.protocol = Protocol.LOCAL;
    targetPeerConfig.hostname = TEST_NAMESPACE;
    targetPeerConfig.send_id = psTarget;
    final MessageDispatcherImpl targetPeer = new MessageDispatcherImpl(targetPeerConfig);

    EndpointConfiguration outPeerConfig = new EndpointConfiguration();
    outPeerConfig.protocol = Protocol.LOCAL;
    outPeerConfig.hostname = TEST_NAMESPACE;
    outPeerConfig.recv_id = psUufiOut;
    MessageDispatcherImpl outPeer = new MessageDispatcherImpl(outPeerConfig);
    List<Entry<Envelope, Object>> uufiReplies = Collections.synchronizedList(new ArrayList<>());
    outPeer.registerHandler(Object.class, msg -> {
      Envelope env = outPeer.getContinuation(msg).getEnvelope();
      uufiReplies.add(new SimpleEntry<>(env, msg));
    });
    outPeer.activate();

    when(provider.modifyConfig(any(Envelope.class), any(Function.class))).thenAnswer(inv -> {
      Function<Entry<Long, String>, String> munger = inv.getArgument(1);
      return munger.apply(new SimpleEntry<>(0L, "{}"));
    });

    // 1. Inbound config/pointset write -> single Success Ack (no config/null echo)
    Envelope cfgEnv = new Envelope();
    cfgEnv.subType = SubType.CONFIG;
    cfgEnv.subFolder = SubFolder.POINTSET;
    cfgEnv.deviceRegistryId = "reg-1";
    cfgEnv.deviceId = "dev-1";
    cfgEnv.source = "pubsub+app";
    cfgEnv.principal = "app@";
    cfgEnv.transactionId = "UUFI:topo-cfg";
    inPeer.publish(new Bundle(cfgEnv, Map.of("points", Map.of("temp", Map.of("set_value", 21)))));

    // 2. Inbound model/system write -> single level-500 Control Ack (not forwarded to target)
    Envelope modelWriteEnv = new Envelope();
    modelWriteEnv.subType = SubType.MODEL;
    modelWriteEnv.subFolder = SubFolder.SYSTEM;
    modelWriteEnv.deviceRegistryId = "reg-1";
    modelWriteEnv.deviceId = "dev-1";
    modelWriteEnv.source = "pubsub+app";
    modelWriteEnv.principal = "app@";
    modelWriteEnv.transactionId = "UUFI:topo-model-write";
    inPeer.publish(
        new Bundle(modelWriteEnv, Map.of("software", Map.of("system", "2.0.0"))));

    // 3. Externally-originated model/system broadcast on udmi_target -> forwarded once
    Envelope extModelEnv = new Envelope();
    extModelEnv.subType = SubType.MODEL;
    extModelEnv.subFolder = SubFolder.SYSTEM;
    extModelEnv.deviceRegistryId = "reg-1";
    extModelEnv.deviceId = "dev-1";
    extModelEnv.source = "implicit";
    extModelEnv.transactionId = "RP:topo-ext-model";
    targetPeer.publish(
        new Bundle(extModelEnv, Map.of("software", Map.of("system", "2.1.0"))));

    // 4. External events/pointset broadcast on udmi_target -> forwarded once
    Envelope extEventEnv = new Envelope();
    extEventEnv.subType = SubType.EVENTS;
    extEventEnv.subFolder = SubFolder.POINTSET;
    extEventEnv.deviceRegistryId = "reg-1";
    extEventEnv.deviceId = "dev-1";
    extEventEnv.source = "device";
    extEventEnv.transactionId = "DEV:topo-ev";
    targetPeer.publish(
        new Bundle(extEventEnv, Map.of("points", Map.of("temp", Map.of("present_value", 21.5)))));

    ((MessageDispatcherImpl) uufiProc.getDispatcher()).terminate();
    ((MessageDispatcherImpl) uufiProc.getDispatcher()).awaitShutdown();
    ((MessageDispatcherImpl) eventsProc.getDispatcher()).terminate();
    ((MessageDispatcherImpl) eventsProc.getDispatcher()).awaitShutdown();
    outPeer.terminate();
    outPeer.awaitShutdown();
    uufiProc.shutdown();
    eventsProc.shutdown();

    assertEquals(4, uufiReplies.size(),
        "Expected config ack + model write error + external model + external event without echoes");
    Map<String, Entry<Envelope, Object>> byTxn = new HashMap<>();
    for (Entry<Envelope, Object> entry : uufiReplies) {
      assertNotNull(entry.getKey().subFolder,
          "UUFI outbound reply must never emit a null subFolder");
      byTxn.put(entry.getKey().transactionId, entry);
    }
    assertEquals(4, byTxn.size(), "Expected distinct transactions without duplicate echoes");

    UufiControl modelErr =
        convertTo(UufiControl.class, byTxn.get("UUFI:topo-model-write").getValue());
    assertNotNull(modelErr.control, "Inbound model write should return UufiControl error");
    assertEquals(Level.ERROR.value(), modelErr.control.status.level);
    assertNull(byTxn.get("RP:topo-ext-model").getKey().principal,
        "External model/system broadcast should have null principal");
    assertNull(byTxn.get("DEV:topo-ev").getKey().principal,
        "External events/pointset broadcast should have null principal");
  }

  /**
   * Test that inbound model/* writes over UUFI are rejected with a level-500 Control Ack
   * and are not forwarded to the internal bus.
   */
  @Test
  public void inboundModelWriteRejectedWithControlErrorTest() {
    Envelope transport = new Envelope();
    transport.source = "app-1";
    transport.gatewayId = "uufi";
    transport.deviceRegistryId = "reg-1";
    transport.deviceId = "dev-1";
    transport.subType = SubType.MODEL;
    transport.subFolder = SubFolder.SYSTEM;

    Map<String, Object> wrapper = new HashMap<>();
    wrapper.put("subType", "model");
    wrapper.put("subFolder", "system");
    wrapper.put("deviceRegistryId", "reg-1");
    wrapper.put("deviceId", "dev-1");
    wrapper.put("principal", TEST_PRINCIPAL);
    wrapper.put("transactionId", "UUFI:model-write");
    wrapper.put("payload", Map.of("software", Map.of("system", "2.0.0")));

    activeTestInstance(() -> getReverseDispatcher().publish(new Bundle(transport, wrapper)));

    assertEquals(1, captured.size(),
        "Inbound model write must emit only a Control Ack without forwarding to internal bus");
    Envelope ackEnv = capturedEnvelopes.get(0);
    assertEquals("uufi", ackEnv.gatewayId);
    assertEquals(TEST_PRINCIPAL, ackEnv.principal);
    assertEquals("UUFI:model-write", ackEnv.transactionId);
    UufiControl ctrl = convertTo(UufiControl.class, toMap(captured.get(0)).get("payload"));
    assertNotNull(ctrl.control, "Inbound model write must return UufiControl error");
    assertEquals(Level.ERROR.value(), ctrl.control.status.level);
  }

  /**
   * Test that model/cloud queries over UUFI succeed while scrubbing sensitive fields
   * (password and credentials, including nested device_ids) and preserving non-secret metadata.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void inboundModelCloudQueryScrubsSensitiveCredentialsTest() {
    Credential cred = new Credential();
    cred.key_data = "PRIVATE-OR-AUTH-KEY";

    CloudModel childModel = new CloudModel();
    childModel.num_id = "111222";
    childModel.password = "nested-secret-pass";
    childModel.credentials = List.of(cred);

    CloudModel secretModel = new CloudModel();
    secretModel.version = TEST_VERSION;
    secretModel.auth_type = Auth_type.ES_256;
    secretModel.num_id = "987654321";
    secretModel.blocked = false;
    secretModel.password = "secret-broker-pass";
    secretModel.credentials = List.of(cred);
    secretModel.device_ids = Map.of("child-1", childModel);
    when(provider.fetchDevice("reg-1", "dev-1")).thenReturn(secretModel);

    Envelope transport = new Envelope();
    transport.source = "app-1";
    transport.gatewayId = "uufi";
    transport.deviceRegistryId = "reg-1";
    transport.deviceId = "dev-1";
    transport.subType = SubType.MODEL;
    transport.subFolder = SubFolder.CLOUD;

    Map<String, Object> queryWrapper = new HashMap<>();
    queryWrapper.put("subType", "model");
    queryWrapper.put("subFolder", "cloud");
    queryWrapper.put("deviceRegistryId", "reg-1");
    queryWrapper.put("deviceId", "dev-1");
    queryWrapper.put("principal", TEST_PRINCIPAL);
    queryWrapper.put("transactionId", "UUFI:q-cloud");
    queryWrapper.put("payload", null);

    activeTestInstance(() -> getReverseDispatcher().publish(new Bundle(transport, queryWrapper)));

    assertEquals(1, captured.size(), "expected a single scrubbed model/cloud Success Ack");
    String rawReply = stringify(captured.get(0));
    assertFalse(rawReply.contains("secret-broker-pass"),
        "UUFI model/cloud query must scrub device password");
    assertFalse(rawReply.contains("nested-secret-pass"),
        "UUFI model/cloud query must scrub nested device_ids password");
    assertFalse(rawReply.contains("PRIVATE-OR-AUTH-KEY"),
        "UUFI model/cloud query must scrub device credentials");

    Map<String, Object> payload = toMap(toMap(captured.get(0)).get("payload"));
    assertNull(payload.get("control"), "model/cloud query should succeed without error control");
    assertNull(payload.get("password"), "password must be removed");
    assertNull(payload.get("credentials"), "credentials must be removed");
    assertEquals("987654321", payload.get("num_id"), "non-secret num_id must be preserved");
    assertEquals("ES256", payload.get("auth_type"), "non-secret auth_type must be preserved");
    assertNotNull(payload.get("timestamp"), "model/cloud reply should include timestamp");
    assertNotNull(payload.get("version"), "model/cloud reply should include version");

    Map<String, Object> deviceIds = (Map<String, Object>) payload.get("device_ids");
    Map<String, Object> childMap = toMap(deviceIds.get("child-1"));
    assertEquals("111222", childMap.get("num_id"), "nested non-secret fields must be preserved");
    assertNull(childMap.get("password"), "nested password must be removed");
    assertNull(childMap.get("credentials"), "nested credentials must be removed");
  }

  /**
   * Test that registry-less MQTT topic (/uufi/c/...) cannot mutate a scoped device via
   * inner envelope deviceRegistryId/deviceId (prevents MQTT topic-ACL bypass).
   */
  @Test
  @SuppressWarnings("unchecked")
  public void inboundUnscopedTopicWithScopedInnerEnvelopeRejectedTest() {
    when(provider.modifyConfig(any(Envelope.class), any(Function.class))).thenAnswer(inv -> {
      Function<Entry<Long, String>, String> munger = inv.getArgument(1);
      return munger.apply(new SimpleEntry<>(0L, "{}"));
    });

    Envelope transport = new Envelope();
    transport.source = "app-1";
    transport.gatewayId = "uufi";
    transport.subType = SubType.CONFIG;
    transport.subFolder = SubFolder.POINTSET;
    // Simulates /uufi/c/config/pointset where topic has no registry/device segments.
    transport.deviceRegistryId = null;
    transport.deviceId = null;

    Map<String, Object> wrapper = new HashMap<>();
    wrapper.put("subType", "config");
    wrapper.put("subFolder", "pointset");
    wrapper.put("deviceRegistryId", "reg-restricted");
    wrapper.put("deviceId", "dev-1");
    wrapper.put("principal", TEST_PRINCIPAL);
    wrapper.put("transactionId", "UUFI:acl-bypass");
    wrapper.put("payload", Map.of("points", Map.of("temp", Map.of("set_value", 99))));

    activeTestInstance(() -> getReverseDispatcher().publish(new Bundle(transport, wrapper)));

    verify(provider, never()).modifyConfig(any(Envelope.class), any(Function.class));
    assertEquals(1, captured.size(), "expected UufiControl error reply for coordinate mismatch");
    UufiControl ctrl = convertTo(UufiControl.class, toMap(captured.get(0)).get("payload"));
    assertNotNull(ctrl.control);
    assertEquals(Level.ERROR.value(), ctrl.control.status.level);
  }

  /**
   * Test that non-object config write payloads fail fast with a level 500 Control Ack
   * instead of calling modifyConfig and silently hanging when processConfigChange returns null.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void inboundConfigWriteNonMapPayloadReturnsControlErrorTest() {
    when(provider.modifyConfig(any(Envelope.class), any(Function.class))).thenReturn(null);

    Envelope transport = new Envelope();
    transport.source = "app-1";
    transport.gatewayId = "uufi";
    transport.deviceRegistryId = "reg-1";
    transport.deviceId = "dev-1";
    transport.subType = SubType.CONFIG;
    transport.subFolder = SubFolder.POINTSET;

    Map<String, Object> wrapper = new HashMap<>();
    wrapper.put("subType", "config");
    wrapper.put("subFolder", "pointset");
    wrapper.put("deviceRegistryId", "reg-1");
    wrapper.put("deviceId", "dev-1");
    wrapper.put("principal", TEST_PRINCIPAL);
    wrapper.put("transactionId", "UUFI:bad-cfg-type");
    wrapper.put("payload", 123);

    activeTestInstance(() -> getReverseDispatcher().publish(new Bundle(transport, wrapper)));

    verify(provider, never()).modifyConfig(any(Envelope.class), any(Function.class));
    assertEquals(1, captured.size(), "expected level 500 Control Ack instead of silent drop");
    UufiControl ctrl = convertTo(UufiControl.class, toMap(captured.get(0)).get("payload"));
    assertNotNull(ctrl.control);
    assertEquals(Level.ERROR.value(), ctrl.control.status.level);
  }

  /**
   * Test that PubSub config writes omitting deviceRegistryId/deviceId fail fast with Control Ack.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void inboundPubSubConfigWriteMissingDeviceCoordsReturnsControlErrorTest() {
    terminateAndWait();
    UdmiServicePod.resetForTest();
    super.resetAfter();

    final String psRecv = "ps_missing_dev_in";
    final String psSend = "ps_missing_dev_target";
    final String sideQueue = "ps_missing_dev_out";

    EndpointConfiguration config = new EndpointConfiguration();
    config.name = "uufi";
    config.protocol = Protocol.LOCAL;
    config.hostname = TEST_NAMESPACE;
    config.recv_id = psRecv;
    config.send_id = psSend;
    config.side_id = sideQueue;

    UufiProcessor psProcessor = new UufiProcessor(config);
    psProcessor.setPubSubProtocolForTest(true);
    UdmiServicePod.putComponent(ProcessorBase.IOT_ACCESS_COMPONENT, () -> provider);
    psProcessor.activate();

    EndpointConfiguration inPeerConfig = new EndpointConfiguration();
    inPeerConfig.protocol = Protocol.LOCAL;
    inPeerConfig.hostname = TEST_NAMESPACE;
    inPeerConfig.send_id = psRecv;
    final MessageDispatcherImpl inPeer = new MessageDispatcherImpl(inPeerConfig);

    EndpointConfiguration sidePeerConfig = new EndpointConfiguration();
    sidePeerConfig.protocol = Protocol.LOCAL;
    sidePeerConfig.hostname = TEST_NAMESPACE;
    sidePeerConfig.recv_id = sideQueue;
    MessageDispatcherImpl sidePeer = new MessageDispatcherImpl(sidePeerConfig);
    List<Object> sideMessages = Collections.synchronizedList(new ArrayList<>());
    sidePeer.registerHandler(Object.class, sideMessages::add);
    sidePeer.activate();

    Envelope cfgEnv = new Envelope();
    cfgEnv.subType = SubType.CONFIG;
    cfgEnv.subFolder = SubFolder.POINTSET;
    cfgEnv.source = "pubsub+app";
    cfgEnv.principal = "app@";
    cfgEnv.transactionId = "UUFI:ps-missing-dev";
    inPeer.publish(new Bundle(cfgEnv, Map.of("points", Map.of())));

    ((MessageDispatcherImpl) psProcessor.getDispatcher()).terminate();
    ((MessageDispatcherImpl) psProcessor.getDispatcher()).awaitShutdown();
    sidePeer.terminate();
    sidePeer.awaitShutdown();
    psProcessor.shutdown();

    verify(provider, never()).modifyConfig(any(Envelope.class), any(Function.class));
    assertEquals(1, sideMessages.size(), "expected level 500 Control Ack on sidecar");
    UufiControl ctrl = convertTo(UufiControl.class, sideMessages.get(0));
    assertNotNull(ctrl.control);
    assertEquals(Level.ERROR.value(), ctrl.control.status.level);
  }

  /**
   * Test that an MQTT string sentinel that happens to be valid base64 JSON (e.g. "e30=")
   * is not speculatively base64-decoded and applied as a config object.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void inboundBase64LikeStringSentinelNotDecodedAsConfigObjectTest() {
    when(provider.modifyConfig(any(Envelope.class), any(Function.class))).thenAnswer(inv -> {
      Function<Entry<Long, String>, String> munger = inv.getArgument(1);
      return munger.apply(new SimpleEntry<>(0L, "{}"));
    });

    Envelope transport = new Envelope();
    transport.source = "app-1";
    transport.gatewayId = "uufi";
    transport.deviceRegistryId = "reg-1";
    transport.deviceId = "dev-1";
    transport.subType = SubType.CONFIG;
    transport.subFolder = SubFolder.POINTSET;

    Map<String, Object> wrapper = new HashMap<>();
    wrapper.put("subType", "config");
    wrapper.put("subFolder", "pointset");
    wrapper.put("deviceRegistryId", "reg-1");
    wrapper.put("deviceId", "dev-1");
    wrapper.put("principal", TEST_PRINCIPAL);
    wrapper.put("transactionId", "UUFI:b64-sentinel");
    wrapper.put("payload", "e30=");

    activeTestInstance(() -> getReverseDispatcher().publish(new Bundle(transport, wrapper)));

    verify(provider, never()).modifyConfig(any(Envelope.class), any(Function.class));
    assertEquals(1, captured.size(), "expected level 500 Control Ack for unknown string sentinel");
    UufiControl ctrl = convertTo(UufiControl.class, toMap(captured.get(0)).get("payload"));
    assertNotNull(ctrl.control, "base64-like string sentinel must not be applied as JSON object");
    assertEquals(Level.ERROR.value(), ctrl.control.status.level);
  }

  /**
   * Test that malformed UUFI JSON producing a BundleException does not reflect to UDMI-REFLECT,
   * and returns a level-500 Control Ack when MQTT routing properties (principal/subType/subFolder)
   * are present.
   */
  @Test
  public void inboundMalformedBundleExceptionReturnsControlAckWithoutReflectingTest() {
    Bundle malformedBundle = new Bundle();
    malformedBundle.message = "While parsing json object";
    malformedBundle.payload = "{not-json";
    malformedBundle.attributesMap = new HashMap<>(Map.of(
        "gatewayId", "uufi",
        "deviceRegistryId", "reg-1",
        "deviceId", "dev-1",
        "subType", "config",
        "subFolder", "pointset",
        "principal", TEST_PRINCIPAL,
        "transactionId", "UUFI:malformed-json"));

    activeTestInstance(() -> getReverseDispatcher().publish(malformedBundle));

    verify(provider, never()).sendCommand(any(Envelope.class), any(SubFolder.class), any());
    assertEquals(1, captured.size(),
        "Malformed UUFI bundle with principal/subType/subFolder should emit Control Ack");
    Envelope ackEnv = capturedEnvelopes.get(0);
    assertEquals(TEST_PRINCIPAL, ackEnv.principal);
    assertEquals("UUFI:malformed-json", ackEnv.transactionId);
    UufiControl ctrl = convertTo(UufiControl.class, toMap(captured.get(0)).get("payload"));
    assertNotNull(ctrl.control, "Expected UufiControl error reply");
    assertEquals(Level.ERROR.value(), ctrl.control.status.level);
  }

  /**
   * Test that PubSub malformed bundles with only principal (minimal routing property) and
   * null-payload Layer 1 handshakes with principal emit level-500 Control Acks on sidecar
   * without reflecting to UDMI-REFLECT.
   */
  @Test
  public void inboundPubSubMalformedBundleMinimalPrincipalAndNullHandshakeTest() {
    terminateAndWait();
    UdmiServicePod.resetForTest();
    super.resetAfter();

    final String psRecv = "ps_err_in";
    final String psSend = "ps_err_target";
    final String sideQueue = "ps_err_out";

    EndpointConfiguration config = new EndpointConfiguration();
    config.name = "uufi";
    config.protocol = Protocol.LOCAL;
    config.hostname = TEST_NAMESPACE;
    config.recv_id = psRecv;
    config.send_id = psSend;
    config.side_id = sideQueue;

    UufiProcessor psProcessor = new UufiProcessor(config);
    psProcessor.setPubSubProtocolForTest(true);
    UdmiServicePod.putComponent(ProcessorBase.IOT_ACCESS_COMPONENT, () -> provider);
    psProcessor.activate();

    EndpointConfiguration inPeerConfig = new EndpointConfiguration();
    inPeerConfig.protocol = Protocol.LOCAL;
    inPeerConfig.hostname = TEST_NAMESPACE;
    inPeerConfig.send_id = psRecv;
    final MessageDispatcherImpl inPeer = new MessageDispatcherImpl(inPeerConfig);

    EndpointConfiguration sidePeerConfig = new EndpointConfiguration();
    sidePeerConfig.protocol = Protocol.LOCAL;
    sidePeerConfig.hostname = TEST_NAMESPACE;
    sidePeerConfig.recv_id = sideQueue;
    MessageDispatcherImpl sidePeer = new MessageDispatcherImpl(sidePeerConfig);
    List<Entry<Envelope, Object>> sideMessages =
        Collections.synchronizedList(new ArrayList<>());
    sidePeer.registerHandler(Object.class, msg -> {
      Envelope env = sidePeer.getContinuation(msg).getEnvelope();
      sideMessages.add(new SimpleEntry<>(env, msg));
    });
    sidePeer.activate();

    // 1. PubSub malformed BundleException carrying only minimal principal + transactionId
    Bundle malformedBundle = new Bundle();
    malformedBundle.message = "While parsing json object";
    malformedBundle.payload = "{not-json";
    malformedBundle.attributesMap = new HashMap<>(Map.of(
        "principal", "app@",
        "transactionId", "UUFI:ps-bad-json"));
    inPeer.publish(malformedBundle);

    // 2. PubSub null-payload Layer 1 handshake with principal
    Envelope nullHandshake = new Envelope();
    nullHandshake.subType = SubType.STATE;
    nullHandshake.subFolder = SubFolder.UDMI;
    nullHandshake.source = "pubsub+app";
    nullHandshake.principal = "app@";
    nullHandshake.transactionId = "UUFI:ps-null-hs";
    inPeer.publish(new Bundle(nullHandshake, MessageDispatcher.nullMessage()));

    ((MessageDispatcherImpl) psProcessor.getDispatcher()).terminate();
    ((MessageDispatcherImpl) psProcessor.getDispatcher()).awaitShutdown();
    sidePeer.terminate();
    sidePeer.awaitShutdown();
    psProcessor.shutdown();

    verify(provider, never()).sendCommand(any(Envelope.class), any(SubFolder.class), any());
    assertEquals(2, sideMessages.size(),
        "Expected Control Acks for malformed bundle (minimal principal) and null handshake");
    Map<String, Entry<Envelope, Object>> byTxn = new HashMap<>();
    for (Entry<Envelope, Object> entry : sideMessages) {
      byTxn.put(entry.getKey().transactionId, entry);
      assertEquals("app@", entry.getKey().principal);
    }
    UufiControl badJsonCtrl =
        convertTo(UufiControl.class, byTxn.get("UUFI:ps-bad-json").getValue());
    assertNotNull(badJsonCtrl.control);
    assertEquals(Level.ERROR.value(), badJsonCtrl.control.status.level);

    UufiControl nullHsCtrl =
        convertTo(UufiControl.class, byTxn.get("UUFI:ps-null-hs").getValue());
    assertNotNull(nullHsCtrl.control);
    assertEquals(Level.ERROR.value(), nullHsCtrl.control.status.level);
  }

  private UufiProcessor getProcessor() {
    return (UufiProcessor) UdmiServicePod.getComponent(UufiProcessor.class);
  }
}
