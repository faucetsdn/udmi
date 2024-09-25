package com.google.bos.udmi.service.pod;

import static com.google.bos.udmi.service.core.StateProcessor.IOT_ACCESS_COMPONENT;
import static com.google.bos.udmi.service.messaging.impl.MessageBase.combineConfig;
import static com.google.bos.udmi.service.messaging.impl.MessagePipeTestBase.REFLECT_REGISTRY;
import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_DEVICE;
import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_REGISTRY;
import static com.google.bos.udmi.service.pod.ContainerBase.REFLECT_BASE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.GeneralUtils.arrayOf;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static java.lang.String.format;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.access.LocalIotAccessProvider;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.messaging.impl.LocalMessagePipe;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.messaging.impl.MessagePipeTestBase;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import udmi.schema.DiscoveryState;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.LocalnetModel;
import udmi.schema.PodConfiguration;
import udmi.schema.PointsetState;
import udmi.schema.UdmiState;

/**
 * Unit tests for a service pod.
 */
public class UdmiServicePodTest {

  public static final String EMPTY_CONFIG = "{}";
  private static final String BASE_CONFIG = "src/test/configs/base_pod.json";
  private static final String BRIDGE_CONFIG = "src/test/configs/bridge_pod.json";
  private static final String FILE_CONFIG = "src/test/configs/trace_pod.json";
  private static final String TRACE_FILE1 = "traces/simple/devices/AHU-22/002_events_pointset.json";
  private static final String TRACE_FILE2 = "traces/simple/devices/AHU-22/003_events_pointset.json";
  private static final long RECEIVE_TIMEOUT_SEC = 2;
  private static final long RECEIVE_TIMEOUT_MS = RECEIVE_TIMEOUT_SEC * 1000;

  private Bundle getReflectorStateBundle() {
    HashMap<Object, Object> messageMap = new HashMap<>();
    UdmiState state = new UdmiState();
    messageMap.put(SubFolder.UDMI.value(), state);
    Bundle bundle = new Bundle(messageMap);
    bundle.envelope.deviceRegistryId = REFLECT_BASE;
    bundle.envelope.deviceId = TEST_REGISTRY;
    return bundle;
  }

  private Envelope makeTestEnvelope() {
    Envelope envelope = new Envelope();
    envelope.deviceId = TEST_DEVICE;
    envelope.deviceRegistryId = TEST_REGISTRY;
    return envelope;
  }

  private EndpointConfiguration reverseFlow(EndpointConfiguration flow) {
    checkNotNull(flow, "message flow not defined");
    EndpointConfiguration reversed = deepCopy(flow);
    String source = reversed.recv_id;
    reversed.recv_id = reversed.send_id;
    reversed.send_id = source;
    return reversed;
  }

  @Test
  public void basicPodTest() {
    UdmiServicePod pod = new UdmiServicePod(arrayOf(BASE_CONFIG));

    PodConfiguration podConfig = pod.getPodConfiguration();

    EndpointConfiguration reversedState =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.flows.get("state")));
    final MessageDispatcherImpl stateDispatcher =
        MessagePipeTestBase.getDispatcherFor(reversedState);

    pod.activate();

    StateUpdate stateUpdate = new StateUpdate();
    stateUpdate.discovery = new DiscoveryState();
    stateUpdate.pointset = new PointsetState();
    stateDispatcher.withEnvelope(makeTestEnvelope()).publish(stateUpdate);

    pod.shutdown();

    LocalIotAccessProvider iotAccess = UdmiServicePod.getComponent(IOT_ACCESS_COMPONENT);
    List<String> commands = iotAccess.getCommands();
    assertEquals(3, commands.size(), "sent commands");
  }

  @Test
  public void bridgeTest() throws Exception {
    UdmiServicePod pod = new UdmiServicePod(arrayOf(BRIDGE_CONFIG));

    PodConfiguration podConfig = pod.getPodConfiguration();

    EndpointConfiguration reversedFrom =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.bridges.get("test").from));
    final MessageDispatcherImpl fromDispatcher = MessagePipeTestBase.getDispatcherFor(reversedFrom);
    EndpointConfiguration reversedMorf =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.bridges.get("test").morf));
    final MessageDispatcherImpl morfDispatcher = MessagePipeTestBase.getDispatcherFor(reversedMorf);

    CompletableFuture<LocalnetModel> received = new CompletableFuture<>();
    fromDispatcher.registerHandler(LocalnetModel.class, received::complete);
    BlockingQueue<Object> defaulted = new LinkedBlockingQueue<>();
    morfDispatcher.registerHandler(Object.class, defaulted::add);

    pod.activate();
    fromDispatcher.activate();
    morfDispatcher.activate();

    fromDispatcher.publish(new StateUpdate());
    morfDispatcher.publish(new LocalnetModel());

    Object polled = defaulted.poll(RECEIVE_TIMEOUT_SEC, TimeUnit.SECONDS);
    assertTrue(polled instanceof StateUpdate, "expected pointset state in default");

    LocalnetModel discoveryState = received.get(RECEIVE_TIMEOUT_SEC, TimeUnit.SECONDS);
    assertNotNull(discoveryState, "no received message");

    assertNull(defaulted.poll(RECEIVE_TIMEOUT_SEC, TimeUnit.SECONDS));
  }

  @Test
  public void failedActivation() {
    UdmiServicePod pod = new UdmiServicePod(arrayOf(BASE_CONFIG));
    UdmiServicePod.getComponent(LocalIotAccessProvider.class).setFailureForTest();
    boolean success = false;
    try {
      pod.activate();
      success = true;
    } catch (Exception e) {
      // expected exception thrown for test.
    }
    assertFalse(success, "pod activation should not return");
    assertFalse(UdmiServicePod.READY_INDICATOR.exists(), "readiness indicator file exists");
  }

  @Test
  public void podFileTest() throws Exception {
    UdmiServicePod pod = new UdmiServicePod(arrayOf(FILE_CONFIG));
    PodConfiguration podConfiguration = pod.getPodConfiguration();
    File outDir = new File(podConfiguration.bridges.get("trace").from.send_id);
    deleteDirectory(outDir);
    File targetFile1 = new File(outDir, TRACE_FILE1);
    File targetFile2 = new File(outDir, TRACE_FILE2);
    assertFalse(targetFile1.exists(), "file should not exist " + targetFile1.getAbsolutePath());
    assertFalse(targetFile2.exists(), "file should not exist " + targetFile2.getAbsolutePath());
    pod.activate();
    assertTrue(UdmiServicePod.READY_INDICATOR.exists(), "readiness indicator file missing");
    Thread.sleep(RECEIVE_TIMEOUT_MS);
    pod.shutdown();
    boolean exists = targetFile1.exists() || targetFile2.exists();
    assertTrue(exists, format("missing target file %s or %s", targetFile1, targetFile2));
  }

  @Test
  public void reflectPodTest() {
    UdmiServicePod pod = new UdmiServicePod(arrayOf(BASE_CONFIG));

    IotAccessBase iotAccess = UdmiServicePod.getComponent(IOT_ACCESS_COMPONENT);
    iotAccess.modifyConfig(makeTestEnvelope(), oldConfig -> EMPTY_CONFIG);

    PodConfiguration podConfig = pod.getPodConfiguration();

    EndpointConfiguration reversedReflect =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.flows.get("reflect")));
    final MessageDispatcherImpl reflectDispatcher =
        MessagePipeTestBase.getDispatcherFor(reversedReflect);

    pod.activate();
    reflectDispatcher.publishBundle(getReflectorStateBundle());
    pod.shutdown();

    LocalIotAccessProvider iotAccessProvider = UdmiServicePod.getComponent(IOT_ACCESS_COMPONENT);
    List<String> commands = iotAccessProvider.getCommands();
    assertEquals(0, commands.size(), "expected sent device commands");

    iotAccess.modifyConfig(makeReflectEnvelope(), oldConfig -> {
      // TODO: Check that this conforms to the actual expected reflector config bundle.
      assertNotEquals(EMPTY_CONFIG, oldConfig, "updated device config");
      return null;
    });

    LocalMessagePipe distributor =
        new LocalMessagePipe(reverseFlow(podConfig.flows.get("distributor")));
    Bundle distributedBundle = distributor.poll();
    assertEquals(REFLECT_BASE, distributedBundle.envelope.deviceRegistryId, "registry id");
    assertEquals(TEST_REGISTRY, distributedBundle.envelope.deviceId, "site id");
    // TODO: Check bundle message to make sure it conforms.
    assertNull(distributor.poll(), "unexpected distribution message");
  }

  private Envelope makeReflectEnvelope() {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = REFLECT_REGISTRY;
    envelope.deviceId = TEST_REGISTRY;
    return envelope;
  }

  /**
   * Reset everything to a clean slate for unit tests.
   */
  @AfterEach
  public void resetForTest() {
    ContainerBase.resetForTest();
    UdmiServicePod.resetForTest();
    LocalMessagePipe.resetForTestStatic();
  }
}
