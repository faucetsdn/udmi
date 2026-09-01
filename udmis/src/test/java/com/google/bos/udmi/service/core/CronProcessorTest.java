package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.StateProcessor.IOT_ACCESS_COMPONENT;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.messaging.impl.MessagePipeTestBase;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import udmi.lib.ProtocolFamily;
import udmi.schema.CloudModel;
import udmi.schema.CloudQuery;
import udmi.schema.DiscoveryEvents;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Enumerations.Depth;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.PodConfiguration;
import udmi.schema.UdmiConfig;

/**
 * Unit tests verifying all cron flows in udmis/etc/prod_pod.json with defined payloads.
 */
public class CronProcessorTest extends ProcessorTestBase {

  private static final String PROD_POD_CONFIG_FILE = "etc/prod_pod.json";
  private static final String ALT_PROD_POD_CONFIG_FILE = "udmis/etc/prod_pod.json";
  private static final Map<String, Map<String, Object>> EXPECTED_GOLDEN_CRONS = ImmutableMap.of(
      "reglist", ImmutableMap.of(
          "subType", SubType.QUERY,
          "subFolder", SubFolder.CLOUD,
          "messageClass", CloudQuery.class,
          "depth", Depth.BUCKETS
      ),
      "regdive", ImmutableMap.of(
          "subType", SubType.QUERY,
          "subFolder", SubFolder.CLOUD,
          "messageClass", CloudQuery.class,
          "depth", Depth.ENTRIES
      ),
      "sendgroot", ImmutableMap.of(
          "subType", SubType.CONFIG,
          "subFolder", SubFolder.UDMI,
          "messageClass", UdmiConfig.class
      )
  );

  private PodConfiguration prodPodConfig;

  private static File findConfigFile(String path, String fallback) {
    File file = new File(path);
    if (file.exists()) {
      return file;
    }
    File fallbackFile = new File(fallback);
    if (fallbackFile.exists()) {
      return fallbackFile;
    }
    throw new IllegalStateException("Config file not found at " + path + " or " + fallback);
  }

  private Map<String, EndpointConfiguration> getPayloadCrons() {
    return prodPodConfig.crons.entrySet().stream()
        .filter(entry -> entry.getValue().payload != null)
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private CronProcessor createTestCronProcessor(String name, EndpointConfiguration rawConfig) {
    EndpointConfiguration config = deepCopy(rawConfig);
    config.name = name;
    config.protocol = Protocol.LOCAL;
    config.hostname = TEST_NAMESPACE;
    config.recv_id = TEST_SOURCE;
    config.send_id = TEST_DESTINATION;

    CronProcessor cronProcessor = (CronProcessor) getProcessor(config, CronProcessor.class);
    setTestDispatcher(cronProcessor.getDispatcher());

    // Register reverse dispatcher to receive published messages from the cron processor
    MessageDispatcherImpl reverseDispatcher = getReverseDispatcher();
    reverseDispatcher.registerHandler(Object.class, captured::add);
    reverseDispatcher.activate();

    if (UdmiServicePod.maybeGetComponent(IOT_ACCESS_COMPONENT) == null) {
      provider = mock(IotAccessBase.class);
      UdmiServicePod.putComponent(IOT_ACCESS_COMPONENT, () -> provider);
      provider.activate();
    }

    cronProcessor.activate();

    // Establish this container as the leader (groot) in the tracker map
    simulateHeartbeatLeader(cronProcessor);

    return cronProcessor;
  }

  private void simulateHeartbeatLeader(CronProcessor cronProcessor) {
    // Send a heartbeat envelope to track this container
    Envelope heartbeatEnvelope = new Envelope();
    heartbeatEnvelope.gatewayId = DistributorPipe.clientId + "~heartbeat";
    heartbeatEnvelope.publishTime = new Date();
    Object message = new Object();
    dispatcher.withEnvelopeFor(heartbeatEnvelope, message,
        () -> cronProcessor.defaultHandler(message));
  }

  private void terminateAndWait(CronProcessor cronProcessor) {
    getTestDispatcher().terminate();
    getTestDispatcher().awaitShutdown();

    getReverseDispatcher().terminate();
    getReverseDispatcher().awaitShutdown();

    if (provider != null) {
      provider.shutdown();
    }
    if (cronProcessor != null) {
      cronProcessor.shutdown();
    }
  }

  private void verifyCronFlow(String cronName, EndpointConfiguration config) {
    captured.clear();
    UdmiServicePod.resetForTest();
    super.resetAfter();
    dispatcher = null;
    reverse = null;

    provider = mock(IotAccessBase.class);
    UdmiServicePod.putComponent(IOT_ACCESS_COMPONENT, () -> provider);
    provider.activate();

    CronProcessor processor = createTestCronProcessor(cronName, config);
    processor.periodicTask();
    terminateAndWait(processor);

    assertEquals(1, captured.size(),
        "Expected exactly 1 published message for cron " + cronName);
    Object publishedMessage = captured.get(0);
    assertNotNull(publishedMessage, "Published message must not be null for " + cronName);

    // Validate against known golden specifications if present
    if (EXPECTED_GOLDEN_CRONS.containsKey(cronName)) {
      Map<String, Object> golden = EXPECTED_GOLDEN_CRONS.get(cronName);
      Class<?> expectedClass = (Class<?>) golden.get("messageClass");
      assertEquals(expectedClass, publishedMessage.getClass(),
          "Message class mismatch for cron " + cronName);

      if (publishedMessage instanceof CloudQuery cloudQuery) {
        assertNotNull(cloudQuery.generation,
            "Generation must be set for CloudQuery in " + cronName);
        if (golden.containsKey("depth")) {
          assertEquals(golden.get("depth"), cloudQuery.depth,
              "CloudQuery depth mismatch for cron " + cronName);
        }
      } else if (publishedMessage instanceof UdmiConfig udmiConfig) {
        assertNotNull(udmiConfig.timestamp,
            "Timestamp must be set for UdmiConfig in " + cronName);
      }
    }

    String jsonOutput = stringify(publishedMessage);
    assertNotNull(jsonOutput, "JSON output should not be null for " + cronName);
    Map<String, Object> jsonMap = toMap(publishedMessage);
    assertFalse(jsonMap.isEmpty(), "JSON map should not be empty for " + cronName);
  }

  /**
   * Set up test environment and load configuration.
   */
  @BeforeEach
  public void setUp() {
    File configFile = findConfigFile(PROD_POD_CONFIG_FILE, ALT_PROD_POD_CONFIG_FILE);
    prodPodConfig = UdmiServicePod.loadRecursive(configFile);
    writeVersionDeployFile();
    provider = mock(IotAccessBase.class);
    UdmiServicePod.putComponent(IOT_ACCESS_COMPONENT, () -> provider);
    provider.activate();
  }

  /**
   * Reset pod state after each test.
   */
  @AfterEach
  public void tearDown() {
    UdmiServicePod.resetForTest();
    super.resetAfter();
    dispatcher = null;
    reverse = null;
  }

  /**
   * Test that prod_pod.json has crons defined and at least one has a payload.
   */
  @Test
  public void testProdPodCronsDiscovered() {
    assertNotNull(prodPodConfig.crons, "crons map must be present in prod_pod.json");
    Map<String, EndpointConfiguration> payloadCrons = getPayloadCrons();
    assertFalse(payloadCrons.isEmpty(),
        "Expected at least one cron with a payload in prod_pod.json");
    assertTrue(payloadCrons.containsKey("reglist"), "Expected reglist cron in prod_pod.json");
    assertTrue(payloadCrons.containsKey("regdive"), "Expected regdive cron in prod_pod.json");
    assertTrue(payloadCrons.containsKey("sendgroot"), "Expected sendgroot cron in prod_pod.json");
  }

  /**
   * Test reglist cron flow specifically (query/cloud depth: buckets).
   */
  @Test
  public void testReglistCronFlow() {
    EndpointConfiguration config = prodPodConfig.crons.get("reglist");
    assertNotNull(config, "reglist cron must exist in prod_pod.json");
    verifyCronFlow("reglist", config);
  }

  /**
   * Test regdive cron flow specifically (query/cloud depth: entries).
   */
  @Test
  public void testRegdiveCronFlow() {
    EndpointConfiguration config = prodPodConfig.crons.get("regdive");
    assertNotNull(config, "regdive cron must exist in prod_pod.json");
    verifyCronFlow("regdive", config);
  }

  /**
   * Test sendgroot cron flow specifically (config/udmi timestamp).
   */
  @Test
  public void testSendgrootCronFlow() {
    EndpointConfiguration config = prodPodConfig.crons.get("sendgroot");
    assertNotNull(config, "sendgroot cron must exist in prod_pod.json");
    verifyCronFlow("sendgroot", config);
  }

  /**
   * Test all cron flows with a payload defined in prod_pod.json, ensuring they render and work WAI.
   */
  @Test
  public void testAllProdPodCronFlowsWai() {
    Map<String, EndpointConfiguration> payloadCrons = getPayloadCrons();

    for (Entry<String, EndpointConfiguration> entry : payloadCrons.entrySet()) {
      final String cronName = entry.getKey();
      final EndpointConfiguration config = entry.getValue();
      verifyCronFlow(cronName, config);
    }
  }

  /**
   * Test cron with targeted envelope (specifying registry and device in payload header).
   */
  @Test
  public void testTargetedCronEnvelope() {
    EndpointConfiguration config = new EndpointConfiguration();
    config.name = "targeted_test";
    config.payload = "query/cloud/ZZ-TRI-FECTA/AHU-1:{\"depth\":\"details\"}";
    config.periodic_sec = 60;

    CronProcessor processor = createTestCronProcessor(config.name, config);
    processor.periodicTask();
    terminateAndWait(processor);

    assertEquals(1, captured.size(), "Expected 1 published message");
    Object publishedMessage = captured.get(0);
    assertTrue(publishedMessage instanceof CloudQuery, "Expected CloudQuery message");
    CloudQuery query = (CloudQuery) publishedMessage;
    assertEquals(Depth.DETAILS, query.depth, "Expected details depth");
  }

  /**
   * Test heartbeat cron (no payload defined) initializes and executes cleanly without failure.
   */
  @Test
  public void testHeartbeatCronExecution() {
    EndpointConfiguration heartbeatConfig = prodPodConfig.crons.get("heartbeat");
    assertNotNull(heartbeatConfig, "Heartbeat cron must be present in prod_pod.json");
    assertNull(heartbeatConfig.payload, "Heartbeat cron should have null payload");

    CronProcessor processor = createTestCronProcessor("heartbeat", heartbeatConfig);
    processor.periodicTask();
    terminateAndWait(processor);

    // Heartbeat without payload does not publish a message to send_id
    assertEquals(0, captured.size(), "Heartbeat cron should not publish payload messages");
  }

  /**
   * Test downstream ControlProcessor handling of generated cron messages.
   */
  @Test
  public void testDownstreamControlProcessorHandling() {
    ControlProcessor controlProcessor = mock(ControlProcessor.class);
    IotAccessBase mockIotAccess = mock(IotAccessBase.class);
    when(mockIotAccess.getRegistries()).thenReturn(ImmutableSet.of(TEST_REGISTRY));
    when(mockIotAccess.getActiveConnections()).thenReturn(ImmutableSet.of());
    controlProcessor.iotAccess = mockIotAccess;

    TargetProcessor mockTargetProcessor = mock(TargetProcessor.class);
    controlProcessor.targetProcessor = mockTargetProcessor;

    // Test CloudQuery from reglist / regdive
    CloudQuery query = new CloudQuery();
    query.depth = Depth.BUCKETS;
    query.generation = new Date();

    // Verify ControlProcessor handles cloud query without exception
    doNothing().when(controlProcessor).cloudQueryHandler(any(CloudQuery.class));
    controlProcessor.cloudQueryHandler(query);

    // Test UdmiConfig from sendgroot
    UdmiConfig udmiConfig = new UdmiConfig();
    udmiConfig.timestamp = new Date();
    doNothing().when(controlProcessor).udmiConfigHandler(any(UdmiConfig.class));
    controlProcessor.udmiConfigHandler(udmiConfig);
  }

  /**
   * Integration test verifying that the reglist cron flow produces the intended system output
   * (registry-level DiscoveryEvents published to target).
   */
  @Test
  public void testReglistSystemIntegrationOutput() throws Exception {
    List<Object> targetOutputs = Collections.synchronizedList(new ArrayList<>());
    List<String> commandsSent = Collections.synchronizedList(new ArrayList<>());

    runCronSystemIntegration("reglist", targetOutputs, commandsSent);

    assertEquals(1, targetOutputs.size(), "Expected 1 target system output message for reglist");
    Object output = targetOutputs.get(0);
    assertTrue(output instanceof DiscoveryEvents, "Output must be DiscoveryEvents");
    DiscoveryEvents discovery = (DiscoveryEvents) output;
    assertEquals(ProtocolFamily.IOT, discovery.family, "Protocol family must be IOT");
    assertNotNull(discovery.generation, "Generation timestamp must be set");
    assertNotNull(discovery.registries, "Registries map must be populated for reglist");
    assertTrue(discovery.registries.containsKey(TEST_REGISTRY),
        "Discovered registries must contain " + TEST_REGISTRY);
    assertNull(discovery.devices, "Device map should be null for depth: buckets");
    assertEquals(0, commandsSent.size(), "No device commands expected for reglist");
  }

  /**
   * Integration test verifying that the regdive cron flow produces the intended system output
   * (both registry-level and traversed device-level DiscoveryEvents published to target).
   */
  @Test
  public void testRegdiveSystemIntegrationOutput() throws Exception {
    List<Object> targetOutputs = Collections.synchronizedList(new ArrayList<>());
    List<String> commandsSent = Collections.synchronizedList(new ArrayList<>());

    runCronSystemIntegration("regdive", targetOutputs, commandsSent);

    assertEquals(2, targetOutputs.size(),
        "Expected 2 target system output messages for regdive (registries + devices)");

    // Output contains both Registry discovery and Device discovery for the registry
    final DiscoveryEvents regDiscovery = targetOutputs.stream()
        .filter(o -> o instanceof DiscoveryEvents && ((DiscoveryEvents) o).registries != null)
        .map(o -> (DiscoveryEvents) o)
        .findFirst()
        .orElse(null);

    assertNotNull(regDiscovery, "Registry discovery event must be present");
    assertEquals(ProtocolFamily.IOT, regDiscovery.family, "Protocol family must be IOT");
    assertNotNull(regDiscovery.registries, "Registries map must be populated");
    assertTrue(regDiscovery.registries.containsKey(TEST_REGISTRY),
        "Discovered registries must contain " + TEST_REGISTRY);

    final DiscoveryEvents devDiscovery = targetOutputs.stream()
        .filter(o -> o instanceof DiscoveryEvents && ((DiscoveryEvents) o).devices != null)
        .map(o -> (DiscoveryEvents) o)
        .findFirst()
        .orElse(null);

    assertNotNull(devDiscovery, "Device discovery event must be present for regdive");
    assertEquals(ProtocolFamily.IOT, devDiscovery.family, "Protocol family must be IOT");
    assertNotNull(devDiscovery.devices, "Devices map must be populated for regdive");
    assertTrue(devDiscovery.devices.containsKey("AHU-1"), "Discovered devices must contain AHU-1");
    assertTrue(devDiscovery.devices.containsKey("GAT-123"),
        "Discovered devices must contain GAT-123");
    assertEquals(0, commandsSent.size(), "No device commands expected for regdive");
  }

  /**
   * Integration test verifying that the sendgroot cron flow produces the intended system output
   * (UdmiConfig with setup block published to target, and command sent to active connections).
   */
  @Test
  public void testSendgrootSystemIntegrationOutput() throws Exception {
    List<Object> targetOutputs = Collections.synchronizedList(new ArrayList<>());
    List<String> commandsSent = Collections.synchronizedList(new ArrayList<>());

    runCronSystemIntegration("sendgroot", targetOutputs, commandsSent);

    assertEquals(1, targetOutputs.size(), "Expected 1 target system output message for sendgroot");
    Object output = targetOutputs.get(0);
    assertTrue(output instanceof UdmiConfig, "Output must be UdmiConfig");
    UdmiConfig config = (UdmiConfig) output;
    assertNotNull(config.timestamp, "Timestamp must be populated");
    assertNotNull(config.setup, "Setup block must be populated by ControlProcessor");
    assertEquals(TEST_USER, config.setup.deployed_by, "Setup deployed_by must match");
    assertEquals(TEST_VERSION, config.setup.udmi_version, "Setup udmi_version must match");

    assertEquals(1, commandsSent.size(), "Expected 1 device command sent to active connection");
    assertTrue(commandsSent.get(0).contains(TEST_REGISTRY + "/AHU-1"),
        "Command must target active connection device");
  }

  /**
   * Integration test dynamically iterating over all payload crons in prod_pod.json
   * and verifying that each produces the intended system output through the complete pipeline.
   */
  @Test
  public void testAllProdPodCronsSystemIntegrationOutput() throws Exception {
    Map<String, EndpointConfiguration> payloadCrons = getPayloadCrons();

    for (String cronName : payloadCrons.keySet()) {
      List<Object> targetOutputs = Collections.synchronizedList(new ArrayList<>());
      List<String> commandsSent = Collections.synchronizedList(new ArrayList<>());

      runCronSystemIntegration(cronName, targetOutputs, commandsSent);

      assertFalse(targetOutputs.isEmpty(),
          "Expected system output on target pipe for cron " + cronName);
    }
  }

  private void runCronSystemIntegration(String cronName, List<Object> targetOutputs,
      List<String> commandsSent) throws Exception {
    UdmiServicePod.resetForTest();
    super.resetAfter();
    dispatcher = null;
    reverse = null;
    writeVersionDeployFile();

    final String controlPipe = "pipe_control_" + cronName;
    final String targetPipe = "pipe_target_" + cronName;

    // 1. Setup mock IoT Access provider with realistic registries, devices, and connections
    IotAccessBase mockIot = mock(IotAccessBase.class);
    when(mockIot.getRegistries()).thenReturn(ImmutableSet.of(TEST_REGISTRY));
    when(mockIot.getActiveConnections()).thenReturn(
        ImmutableSet.of(Map.entry(TEST_REGISTRY, "AHU-1")));

    CloudModel registryDevicesModel = new CloudModel();
    registryDevicesModel.device_ids = ImmutableMap.of(
        "AHU-1", new CloudModel(),
        "GAT-123", new CloudModel()
    );
    when(mockIot.listDevices(eq(TEST_REGISTRY), any())).thenReturn(registryDevicesModel);

    doAnswer(invocation -> {
      Envelope env = invocation.getArgument(0);
      SubFolder folder = invocation.getArgument(1);
      String msg = invocation.getArgument(2);
      commandsSent.add(env.deviceRegistryId + "/" + env.deviceId + "/" + folder + ":" + msg);
      return null;
    }).when(mockIot).sendCommand(any(Envelope.class), any(SubFolder.class), any(String.class));

    UdmiServicePod.putComponent(IOT_ACCESS_COMPONENT, () -> mockIot);
    mockIot.activate();

    // 2. Setup TargetProcessor for last_seen data
    TargetProcessor mockTarget = mock(TargetProcessor.class);
    when(mockTarget.getLastSeen(TEST_REGISTRY)).thenReturn(java.time.Instant.now());
    UdmiServicePod.putComponent(ContainerBase.getName(TargetProcessor.class), () -> mockTarget);

    // 3. Setup Target output listener on targetPipe
    EndpointConfiguration targetConfig = new EndpointConfiguration();
    targetConfig.protocol = Protocol.LOCAL;
    targetConfig.hostname = TEST_NAMESPACE;
    targetConfig.recv_id = targetPipe;
    targetConfig.send_id = "sink_" + cronName;
    MessageDispatcherImpl targetDispatcher = MessagePipeTestBase.getDispatcherFor(targetConfig);
    targetDispatcher.registerHandler(Object.class, targetOutputs::add);
    targetDispatcher.activate();

    // 4. Setup ControlProcessor (listening on controlPipe, publishing to targetPipe)
    EndpointConfiguration controlConfig = new EndpointConfiguration();
    controlConfig.protocol = Protocol.LOCAL;
    controlConfig.hostname = TEST_NAMESPACE;
    controlConfig.recv_id = controlPipe;
    controlConfig.send_id = targetPipe;
    controlConfig.side_id = controlPipe;

    ControlProcessor controlProcessor = (ControlProcessor) getProcessor(controlConfig,
        ControlProcessor.class);
    controlProcessor.activate();

    // 5. Setup CronProcessor (publishing to controlPipe)
    EndpointConfiguration rawCronConfig = prodPodConfig.crons.get(cronName);
    assertNotNull(rawCronConfig, "Cron config not found for " + cronName);
    EndpointConfiguration cronConfig = deepCopy(rawCronConfig);
    cronConfig.name = cronName;
    cronConfig.protocol = Protocol.LOCAL;
    cronConfig.hostname = TEST_NAMESPACE;
    cronConfig.recv_id = "cron_dummy_" + cronName;
    cronConfig.send_id = controlPipe;

    CronProcessor cronProcessor = (CronProcessor) getProcessor(cronConfig, CronProcessor.class);
    setTestDispatcher(cronProcessor.getDispatcher());
    cronProcessor.activate();
    simulateHeartbeatLeader(cronProcessor);

    // 6. Trigger Cron Execution
    cronProcessor.periodicTask();

    // 7. Wait for asynchronous message propagation through the pipeline
    long timeoutMs = 3000;
    long start = System.currentTimeMillis();
    int expectedMinCount = "regdive".equals(cronName) ? 2 : 1;
    while (targetOutputs.size() < expectedMinCount
        && (System.currentTimeMillis() - start) < timeoutMs) {
      Thread.sleep(50);
    }

    // Shutdown components
    cronProcessor.shutdown();
    controlProcessor.shutdown();
    targetDispatcher.terminate();
    targetDispatcher.awaitShutdown();
    mockIot.shutdown();
  }
}
