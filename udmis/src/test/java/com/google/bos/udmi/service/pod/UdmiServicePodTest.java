package com.google.bos.udmi.service.pod;

import static com.google.bos.udmi.service.core.StateProcessor.IOT_ACCESS_COMPONENT;
import static com.google.bos.udmi.service.messaging.impl.MessageBase.combineConfig;
import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_DEVICE;
import static com.google.bos.udmi.service.messaging.impl.MessageTestCore.TEST_REGISTRY;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.GeneralUtils.arrayOf;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.access.LocalIotAccessProvider;
import com.google.bos.udmi.service.core.ProcessorTestBase;
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
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.LocalnetModel;
import udmi.schema.PodConfiguration;
import udmi.schema.PointsetState;
import udmi.schema.UdmiState;

/**
 * Unit tests for a service pod.
 */
public class UdmiServicePodTest {

  private static final String BASE_CONFIG = "src/test/configs/base_pod.json";
  private static final String BRIDGE_CONFIG = "src/test/configs/bridge_pod.json";
  private static final String FILE_CONFIG = "src/test/configs/trace_pod.json";
  private static final String TARGET_FILE = "null/null/devices/null/003_event_pointset.json";
  private static final long RECEIVE_TIMEOUT_SEC = 2;
  private static final long RECEIVE_TIMEOUT_MS = RECEIVE_TIMEOUT_SEC * 1000;

  private Bundle getReflectorStateBundle() {
    HashMap<Object, Object> messageMap = new HashMap<>();
    UdmiState state = new UdmiState();
    messageMap.put(SubFolder.UDMI.value(), state);
    Bundle bundle = new Bundle(messageMap);
    bundle.envelope.deviceRegistryId = TEST_REGISTRY;
    bundle.envelope.deviceId = TEST_DEVICE;
    return bundle;
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
  public void basicPodTest() throws Exception {
    ProcessorTestBase.writeVersionDeployFile();
    UdmiServicePod pod = new UdmiServicePod(arrayOf(BASE_CONFIG));

    PodConfiguration podConfig = pod.getPodConfiguration();

    EndpointConfiguration reversedState =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.flows.get("state")));
    final MessageDispatcherImpl stateDispatcher =
        MessagePipeTestBase.getDispatcherFor(reversedState);
    stateDispatcher.prototypeEnvelope.deviceId = TEST_DEVICE;
    stateDispatcher.prototypeEnvelope.deviceRegistryId = TEST_REGISTRY;

    pod.activate();

    StateUpdate stateUpdate = new StateUpdate();
    stateUpdate.discovery = new DiscoveryState();
    stateUpdate.pointset = new PointsetState();
    stateDispatcher.publish(stateUpdate);

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
    EndpointConfiguration reversedTo =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.bridges.get("test").to));
    final MessageDispatcherImpl toDispatcher = MessagePipeTestBase.getDispatcherFor(reversedTo);

    CompletableFuture<LocalnetModel> received = new CompletableFuture<>();
    fromDispatcher.registerHandler(LocalnetModel.class, received::complete);
    BlockingQueue<Object> defaulted = new LinkedBlockingQueue<>();
    toDispatcher.registerHandler(Object.class, defaulted::add);

    pod.activate();
    fromDispatcher.activate();
    toDispatcher.activate();

    fromDispatcher.publish(new StateUpdate());
    toDispatcher.publish(new LocalnetModel());

    Object polled = defaulted.poll(RECEIVE_TIMEOUT_SEC, TimeUnit.SECONDS);
    assertTrue(polled instanceof StateUpdate, "expected pointset state in default");

    LocalnetModel discoveryState = received.get(RECEIVE_TIMEOUT_SEC, TimeUnit.SECONDS);
    assertNotNull(discoveryState, "no received message");

    assertNull(defaulted.poll(RECEIVE_TIMEOUT_SEC, TimeUnit.SECONDS));
  }

  @Test
  public void podFileTest() throws Exception {
    UdmiServicePod pod = new UdmiServicePod(arrayOf(FILE_CONFIG));
    PodConfiguration podConfiguration = pod.getPodConfiguration();
    File outDir = new File(podConfiguration.bridges.get("trace").from.send_id);
    deleteDirectory(outDir);
    File targetFile = new File(outDir, TARGET_FILE);
    assertFalse(targetFile.exists(), "file should not exist " + targetFile.getAbsolutePath());
    pod.activate();
    Thread.sleep(RECEIVE_TIMEOUT_MS);
    pod.shutdown();
    assertTrue(targetFile.exists(), "missing target output file " + targetFile.getAbsolutePath());
  }

  @Test
  public void reflectPodTest() throws Exception {
    ProcessorTestBase.writeVersionDeployFile();
    UdmiServicePod pod = new UdmiServicePod(arrayOf(BASE_CONFIG));

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
    assertEquals(1, commands.size(), "expected sent device commands");
  }

  /**
   * Reset everything to a clean slate for unit tests.
   */
  @AfterEach
  public void resetForTest() {
    UdmiServicePod.resetForTest();
    LocalMessagePipe.resetForTestStatic();
  }
}
