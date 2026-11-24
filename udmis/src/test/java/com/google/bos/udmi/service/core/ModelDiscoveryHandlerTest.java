package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_REGISTRY;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.udmi.util.MetadataMapKeys;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import udmi.lib.ProtocolFamily;
import udmi.schema.Category;
import udmi.schema.CloudModel;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.FamilyDiscoveryState;
import udmi.schema.FamilyDiscoveryState.Phase;
import udmi.schema.GatewayModel;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.SystemModel;

class ModelDiscoveryHandlerTest implements MessageContinuation {

  private static final String TEST_PROJECT = "daq-test-suite";
  private static final Date GENERATION = Date.from(Instant.parse("2026-03-01T12:00:00Z"));
  private static final String EXPECTED_TXN = isoConvert(GENERATION);

  private ControlProcessor controlProcessor;
  private IotAccessBase iotAccess;
  private Envelope envelope;
  private DiscoveryConfig config;
  private ArgumentCaptor<Envelope> publishEnvelopeCapture;
  private ArgumentCaptor<Object> publishMessageCapture;
  private ArgumentCaptor<Envelope> sideEnvelopeCapture;
  private ArgumentCaptor<Object> sideMessageCapture;

  @Override
  public Envelope getEnvelope() {
    return envelope;
  }

  @Override
  public void publish(Object message) {
    throw new RuntimeException("Should be mocked");
  }

  @BeforeEach
  void setupMocks() {
    controlProcessor = mock(ControlProcessor.class);
    iotAccess = mock(IotAccessBase.class);
    controlProcessor.iotAccess = iotAccess;

    envelope = new Envelope();
    envelope.projectId = TEST_PROJECT;
    envelope.deviceRegistryId = TEST_REGISTRY;
    envelope.subFolder = SubFolder.DISCOVERY;
    envelope.subType = SubType.CONFIG;
    envelope.subSetKey = ModelDiscoveryHandler.MODEL_SUBSET_KEY;

    FamilyDiscoveryConfig iotConfig = new FamilyDiscoveryConfig();
    iotConfig.generation = GENERATION;
    config = new DiscoveryConfig();
    config.families = new HashMap<>();
    config.families.put(ProtocolFamily.IOT, iotConfig);

    publishEnvelopeCapture = ArgumentCaptor.forClass(Envelope.class);
    publishMessageCapture = ArgumentCaptor.forClass(Object.class);
    sideEnvelopeCapture = ArgumentCaptor.forClass(Envelope.class);
    sideMessageCapture = ArgumentCaptor.forClass(Object.class);

    doReturn(this).when(controlProcessor).getContinuation(eq(config));
    doNothing().when(controlProcessor)
        .publish(publishEnvelopeCapture.capture(), publishMessageCapture.capture());
    doNothing().when(controlProcessor)
        .sideProcess(sideEnvelopeCapture.capture(), sideMessageCapture.capture());
  }

  @Test
  void projectWideFanOut() {
    envelope.deviceRegistryId = null;
    doReturn(Set.of("REG-B", "UDMI-REFLECT", "REG-A")).when(iotAccess).getRegistries();

    ModelDiscoveryHandler.processDiscoveryConfig(controlProcessor, config);

    List<Envelope> fannedEnvelopes = sideEnvelopeCapture.getAllValues();
    assertEquals(2, fannedEnvelopes.size(), "fan-out excludes UDMI-REFLECT");
    assertEquals("REG-A", fannedEnvelopes.get(0).deviceRegistryId, "first registry sorted");
    assertEquals("REG-B", fannedEnvelopes.get(1).deviceRegistryId, "second registry sorted");
    assertNull(fannedEnvelopes.get(0).deviceId, "deviceId remains null");
    assertEquals(ModelDiscoveryHandler.MODEL_SUBSET_KEY, fannedEnvelopes.get(0).subSetKey);
    List<Object> fannedMessages = sideMessageCapture.getAllValues();
    assertEquals(config, fannedMessages.get(0));
    assertEquals(config, fannedMessages.get(1));
    assertNull(envelope.deviceRegistryId, "original envelope not mutated");
    verify(controlProcessor, never()).publish(any(Envelope.class), any());
  }

  @Test
  void emptyRegistryIdTriggersFanOut() {
    envelope.deviceRegistryId = "";
    doReturn(Set.of("REG-A", "UDMI-REFLECT")).when(iotAccess).getRegistries();

    ModelDiscoveryHandler.processDiscoveryConfig(controlProcessor, config);

    List<Envelope> fannedEnvelopes = sideEnvelopeCapture.getAllValues();
    assertEquals(1, fannedEnvelopes.size(), "empty registryId fans out and skips UDMI-REFLECT");
    assertEquals("REG-A", fannedEnvelopes.get(0).deviceRegistryId);
    verify(iotAccess, never()).listDevices(any(), any());
  }

  @Test
  void emptySiteExport() {
    CloudModel listResult = new CloudModel();
    listResult.device_ids = new HashMap<>();
    doReturn(listResult).when(iotAccess).listDevices(eq(TEST_REGISTRY), eq(null));

    ModelDiscoveryHandler.processDiscoveryConfig(controlProcessor, config);

    List<Envelope> outEnvelopes = publishEnvelopeCapture.getAllValues();
    List<Object> outMessages = publishMessageCapture.getAllValues();
    assertEquals(4, outMessages.size(), "ACTIVE state + start + end + STOPPED state");

    DiscoveryState activeState = (DiscoveryState) outMessages.get(0);
    assertEquals(Phase.ACTIVE, activeState.families.get(ProtocolFamily.IOT).phase);
    assertEnvelope(outEnvelopes.get(0), SubType.STATE);

    DiscoveryEvents startEvent = (DiscoveryEvents) outMessages.get(1);
    assertEquals(0, startEvent.event_no);
    assertNull(startEvent.addr);
    assertEquals(ProtocolFamily.IOT, startEvent.family);
    assertEquals(GENERATION, startEvent.generation);
    assertEnvelope(outEnvelopes.get(1), SubType.EVENTS);

    DiscoveryEvents endEvent = (DiscoveryEvents) outMessages.get(2);
    assertEquals(-1, endEvent.event_no);
    assertNull(endEvent.addr);
    assertEnvelope(outEnvelopes.get(2), SubType.EVENTS);

    DiscoveryState stoppedState = (DiscoveryState) outMessages.get(3);
    FamilyDiscoveryState iotState = stoppedState.families.get(ProtocolFamily.IOT);
    assertEquals(Phase.STOPPED, iotState.phase);
    assertEquals(0, iotState.active_count);
    assertNull(iotState.status);
    assertEnvelope(outEnvelopes.get(3), SubType.STATE);
  }

  @Test
  void fullSiteExportFiltersBlockedAndSortsDevices() {
    CloudModel listResult = new CloudModel();
    listResult.device_ids = new HashMap<>();
    listResult.device_ids.put("VAV-2", makeDeviceListEntry(false));
    listResult.device_ids.put("BLK-1", makeDeviceListEntry(true));
    listResult.device_ids.put("AHU-1", makeDeviceListEntry(false));
    doReturn(listResult).when(iotAccess).listDevices(eq(TEST_REGISTRY), eq(null));

    doReturn(makeCloudDevice("AHU-1", null)).when(iotAccess).fetchDevice(TEST_REGISTRY, "AHU-1");
    doReturn(makeCloudDevice("VAV-2", null)).when(iotAccess).fetchDevice(TEST_REGISTRY, "VAV-2");

    ModelDiscoveryHandler.processDiscoveryConfig(controlProcessor, config);

    verify(iotAccess, never()).fetchDevice(TEST_REGISTRY, "BLK-1");

    List<Object> outMessages = publishMessageCapture.getAllValues();
    assertEquals(6, outMessages.size(), "ACTIVE + start + 2 devices + end + STOPPED");

    DiscoveryEvents startEvent = (DiscoveryEvents) outMessages.get(1);
    assertEquals(0, startEvent.event_no);

    DiscoveryEvents firstDevice = (DiscoveryEvents) outMessages.get(2);
    assertEquals(1, firstDevice.event_no);
    assertEquals("AHU-1", firstDevice.addr);
    assertNotNull(firstDevice.model);
    assertEquals("AHU-1", firstDevice.model.system.serial_no);
    List<Envelope> outEnvelopes = publishEnvelopeCapture.getAllValues();
    assertEnvelope(outEnvelopes.get(2), SubType.EVENTS);

    DiscoveryEvents secondDevice = (DiscoveryEvents) outMessages.get(3);
    assertEquals(2, secondDevice.event_no);
    assertEquals("VAV-2", secondDevice.addr);
    assertNotNull(secondDevice.model);
    assertEquals("VAV-2", secondDevice.model.system.serial_no);
    assertEnvelope(outEnvelopes.get(3), SubType.EVENTS);

    DiscoveryEvents endEvent = (DiscoveryEvents) outMessages.get(4);
    assertEquals(-3, endEvent.event_no);
    assertEnvelope(outEnvelopes.get(4), SubType.EVENTS);

    DiscoveryState stoppedState = (DiscoveryState) outMessages.get(5);
    assertEquals(Phase.STOPPED, stoppedState.families.get(ProtocolFamily.IOT).phase);
    assertEquals(2, stoppedState.families.get(ProtocolFamily.IOT).active_count);
    assertEnvelope(outEnvelopes.get(5), SubType.STATE);
  }

  @Test
  void gatewayProxyIdsHydration() {
    CloudModel listResult = new CloudModel();
    listResult.device_ids = new HashMap<>();
    listResult.device_ids.put("GAT-1", makeDeviceListEntry(false));
    listResult.device_ids.put("GAT-2", makeDeviceListEntry(false));
    listResult.device_ids.put("GAT-3", makeDeviceListEntry(false));
    doReturn(listResult).when(iotAccess).listDevices(eq(TEST_REGISTRY), eq(null));

    CloudModel gwWithMeta = makeCloudDevice("GAT-1", List.of("SNS-OLD", "SNS-2"));
    gwWithMeta.gateway = new GatewayModel();
    gwWithMeta.gateway.proxy_ids = List.of("SNS-3", "SNS-1");
    doReturn(gwWithMeta).when(iotAccess).fetchDevice(TEST_REGISTRY, "GAT-1");

    CloudModel gwWithoutMeta = new CloudModel();
    gwWithoutMeta.gateway = new GatewayModel();
    gwWithoutMeta.gateway.proxy_ids = List.of("SNS-3");
    doReturn(gwWithoutMeta).when(iotAccess).fetchDevice(TEST_REGISTRY, "GAT-2");

    CloudModel gwUnboundAll = makeCloudDevice("GAT-3", List.of("SNS-OLD"));
    gwUnboundAll.gateway = new GatewayModel();
    gwUnboundAll.gateway.proxy_ids = List.of();
    doReturn(gwUnboundAll).when(iotAccess).fetchDevice(TEST_REGISTRY, "GAT-3");

    ModelDiscoveryHandler.processDiscoveryConfig(controlProcessor, config);

    List<Object> outMessages = publishMessageCapture.getAllValues();
    DiscoveryEvents gat1Event = (DiscoveryEvents) outMessages.get(2);
    assertEquals("GAT-1", gat1Event.addr);
    assertEquals(List.of("SNS-1", "SNS-3"), gat1Event.model.gateway.proxy_ids,
        "live cloud bindings replace stale stored proxy_ids in sorted order");

    DiscoveryEvents gat2Event = (DiscoveryEvents) outMessages.get(3);
    assertEquals("GAT-2", gat2Event.addr);
    assertNotNull(gat2Event.model);
    assertNotNull(gat2Event.model.timestamp, "synthesized metadata has timestamp");
    assertNotNull(gat2Event.model.version, "synthesized metadata has version");
    assertNotNull(gat2Event.model.system, "synthesized metadata has system");
    assertEquals(List.of("SNS-3"), gat2Event.model.gateway.proxy_ids);

    DiscoveryEvents gat3Event = (DiscoveryEvents) outMessages.get(4);
    assertEquals("GAT-3", gat3Event.addr);
    assertEquals(List.of(), gat3Event.model.gateway.proxy_ids,
        "empty live bindings clear stale stored proxy_ids");
  }

  @Test
  void errorStateReporting() {
    doThrow(new RuntimeException("Registry read failed"))
        .when(iotAccess).listDevices(eq(TEST_REGISTRY), eq(null));

    ModelDiscoveryHandler.processDiscoveryConfig(controlProcessor, config);

    List<Object> outMessages = publishMessageCapture.getAllValues();
    assertEquals(2, outMessages.size(), "ACTIVE state + ERROR STOPPED state");

    DiscoveryState errorState = (DiscoveryState) outMessages.get(1);
    FamilyDiscoveryState iotState = errorState.families.get(ProtocolFamily.IOT);
    assertEquals(Phase.STOPPED, iotState.phase);
    assertEquals(0, iotState.active_count);
    assertNotNull(iotState.status);
    assertEquals(Level.ERROR.value(), iotState.status.level);
    assertEquals(Category.DISCOVERY_FAMILY_SCAN, iotState.status.category);
    assertEquals("Registry read failed", iotState.status.message);
    List<Envelope> outEnvelopes = publishEnvelopeCapture.getAllValues();
    assertEnvelope(outEnvelopes.get(1), SubType.STATE);
  }

  @Test
  void midExportFailureReportsPartialActiveCountAndDeviceContext() {
    CloudModel listResult = new CloudModel();
    listResult.device_ids = new HashMap<>();
    listResult.device_ids.put("AHU-1", makeDeviceListEntry(false));
    listResult.device_ids.put("AHU-2", makeDeviceListEntry(false));
    listResult.device_ids.put("AHU-3", makeDeviceListEntry(false));
    doReturn(listResult).when(iotAccess).listDevices(eq(TEST_REGISTRY), eq(null));

    doReturn(makeCloudDevice("AHU-1", null)).when(iotAccess).fetchDevice(TEST_REGISTRY, "AHU-1");
    doThrow(new RuntimeException("Corrupt JSON"))
        .when(iotAccess).fetchDevice(TEST_REGISTRY, "AHU-2");

    ModelDiscoveryHandler.processDiscoveryConfig(controlProcessor, config);

    List<Object> outMessages = publishMessageCapture.getAllValues();
    // ACTIVE state + start(0) + AHU-1(1) + ERROR STOPPED state (no end marker)
    assertEquals(4, outMessages.size(), "expected partial stream without end marker");
    DiscoveryState errorState = (DiscoveryState) outMessages.get(3);
    FamilyDiscoveryState iotState = errorState.families.get(ProtocolFamily.IOT);
    assertEquals(Phase.STOPPED, iotState.phase);
    assertEquals(1, iotState.active_count, "active_count reflects emitted devices before failure");
    assertEquals(Level.ERROR.value(), iotState.status.level);
    assertTrue(iotState.status.message.contains(TEST_REGISTRY + "/AHU-2"),
        "error message should identify failing device: " + iotState.status.message);
  }

  @Test
  void generationFallbackPrecedence() {
    CloudModel listResult = new CloudModel();
    listResult.device_ids = new HashMap<>();
    doReturn(listResult).when(iotAccess).listDevices(eq(TEST_REGISTRY), eq(null));

    Date configGen = Date.from(Instant.parse("2026-04-02T08:30:00Z"));
    config.families.get(ProtocolFamily.IOT).generation = null;
    config.generation = configGen;

    ModelDiscoveryHandler.processDiscoveryConfig(controlProcessor, config);

    List<Envelope> outEnvelopes = publishEnvelopeCapture.getAllValues();
    String expectedConfigTxn = isoConvert(configGen);
    for (Envelope out : outEnvelopes) {
      assertEquals(expectedConfigTxn, out.transactionId, "falls back to config.generation");
    }

    // Both null: falls back to a consistent generated timestamp across all messages
    config.generation = null;
    int priorCount = outEnvelopes.size();
    ModelDiscoveryHandler.processDiscoveryConfig(controlProcessor, config);

    List<Envelope> allEnvelopes = publishEnvelopeCapture.getAllValues();
    List<Envelope> secondRun = allEnvelopes.subList(priorCount, allEnvelopes.size());
    assertEquals(4, secondRun.size());
    String generatedTxn = secondRun.get(0).transactionId;
    assertNotNull(generatedTxn);
    for (Envelope out : secondRun) {
      assertEquals(generatedTxn, out.transactionId, "consistent transactionId within scan");
    }
  }

  @Test
  void ignoresNonModelDiscoveryConfig() {
    envelope.subSetKey = "other";
    ModelDiscoveryHandler.processDiscoveryConfig(controlProcessor, config);

    envelope.subSetKey = ModelDiscoveryHandler.MODEL_SUBSET_KEY;
    envelope.deviceId = "AHU-1";
    ModelDiscoveryHandler.processDiscoveryConfig(controlProcessor, config);

    envelope.deviceId = null;
    config.families.clear();
    ModelDiscoveryHandler.processDiscoveryConfig(controlProcessor, config);

    verify(iotAccess, never()).getRegistries();
    verify(iotAccess, never()).listDevices(any(), any());
    verify(controlProcessor, never()).publish(any(Envelope.class), any());
    verify(controlProcessor, never()).sideProcess(any(), any());
  }

  private void assertEnvelope(Envelope out, SubType expectedSubType) {
    assertEquals(TEST_PROJECT, out.projectId);
    assertEquals(TEST_REGISTRY, out.deviceRegistryId);
    assertNull(out.deviceId, "deviceId must be null for provider messages");
    assertEquals(ModelDiscoveryHandler.PROVIDER_ID, out.providerId);
    assertEquals(ModelDiscoveryHandler.MODEL_SUBSET_KEY, out.subSetKey);
    assertEquals(SubFolder.DISCOVERY, out.subFolder);
    assertEquals(expectedSubType, out.subType);
    assertEquals(EXPECTED_TXN, out.transactionId);
  }

  private CloudModel makeDeviceListEntry(boolean blocked) {
    CloudModel entry = new CloudModel();
    entry.blocked = blocked;
    return entry;
  }

  private CloudModel makeCloudDevice(String serialNo, List<String> proxyIds) {
    Metadata metadata = new Metadata();
    metadata.system = new SystemModel();
    metadata.system.serial_no = serialNo;
    if (proxyIds != null) {
      metadata.gateway = new GatewayModel();
      metadata.gateway.proxy_ids = proxyIds;
    }
    CloudModel cloudModel = new CloudModel();
    cloudModel.metadata = Map.of(MetadataMapKeys.UDMI_METADATA, stringify(metadata));
    return cloudModel;
  }

  @Nested
  class ControlProcessorWiringTest extends ProcessorTestBase {

    @Override
    protected ProcessorBase getProcessor(EndpointConfiguration config,
        Class<? extends ProcessorBase> clazz) {
      UdmiServicePod.putComponent("target", () -> mock(TargetProcessor.class));
      return super.getProcessor(config, clazz);
    }

    @Test
    void dispatchesModelDiscoveryThroughControlProcessor() {
      initializeTestInstance(ControlProcessor.class);

      CloudModel listResult = new CloudModel();
      listResult.device_ids = new HashMap<>();
      listResult.device_ids.put("AHU-1", makeDeviceListEntry(false));
      doReturn(listResult).when(provider).listDevices(eq(TEST_REGISTRY), eq(null));
      doReturn(makeCloudDevice("AHU-1", null)).when(provider).fetchDevice(TEST_REGISTRY, "AHU-1");

      FamilyDiscoveryConfig iotConfig = new FamilyDiscoveryConfig();
      iotConfig.generation = GENERATION;
      DiscoveryConfig discoveryConfig = new DiscoveryConfig();
      discoveryConfig.families = new HashMap<>();
      discoveryConfig.families.put(ProtocolFamily.IOT, iotConfig);

      Bundle bundle = new Bundle(discoveryConfig);
      bundle.envelope.deviceRegistryId = TEST_REGISTRY;
      bundle.envelope.deviceId = null;
      bundle.envelope.subFolder = SubFolder.DISCOVERY;
      bundle.envelope.subType = SubType.CONFIG;
      bundle.envelope.subSetKey = ModelDiscoveryHandler.MODEL_SUBSET_KEY;

      getReverseDispatcher().publish(bundle);
      terminateAndWait();

      assertEquals(0, getExceptionCount(), "expected zero processor exceptions");
      assertEquals(5, captured.size(), "ACTIVE + start + AHU-1 + end + STOPPED");

      List<DiscoveryState> states = captured.stream()
          .filter(DiscoveryState.class::isInstance)
          .map(DiscoveryState.class::cast)
          .toList();
      List<DiscoveryEvents> events = captured.stream()
          .filter(DiscoveryEvents.class::isInstance)
          .map(DiscoveryEvents.class::cast)
          .toList();
      assertEquals(2, states.size(), "expected ACTIVE and STOPPED states");
      assertEquals(3, events.size(), "expected start, device, and end events");
      assertTrue(states.stream()
          .anyMatch(s -> Phase.ACTIVE == s.families.get(ProtocolFamily.IOT).phase));
      assertTrue(states.stream()
          .anyMatch(s -> Phase.STOPPED == s.families.get(ProtocolFamily.IOT).phase
              && Integer.valueOf(1).equals(s.families.get(ProtocolFamily.IOT).active_count)));
      assertTrue(events.stream().anyMatch(e -> Integer.valueOf(0).equals(e.event_no)));
      assertTrue(events.stream()
          .anyMatch(e -> Integer.valueOf(1).equals(e.event_no) && "AHU-1".equals(e.addr)));
      assertTrue(events.stream().anyMatch(e -> Integer.valueOf(-2).equals(e.event_no)));

      assertEquals(5, capturedEnvelopes.size());
      for (Envelope out : capturedEnvelopes) {
        assertNull(out.deviceId, "deviceId must remain null across dispatcher");
        assertEquals(ModelDiscoveryHandler.PROVIDER_ID, out.providerId);
        assertEquals(ModelDiscoveryHandler.MODEL_SUBSET_KEY, out.subSetKey);
        assertEquals(EXPECTED_TXN, out.transactionId);
      }
    }
  }
}
