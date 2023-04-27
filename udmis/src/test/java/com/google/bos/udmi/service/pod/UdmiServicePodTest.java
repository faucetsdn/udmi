package com.google.bos.udmi.service.pod;

import static com.google.bos.udmi.service.messaging.impl.MessageBase.combineConfig;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.GeneralUtils.arrayOf;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.core.ProcessorTestBase;
import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.messaging.impl.LocalMessagePipe;
import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.messaging.impl.MessageTestBase;
import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import udmi.schema.DiscoveryState;
import udmi.schema.EndpointConfiguration;
import udmi.schema.LocalnetModel;
import udmi.schema.PodConfiguration;
import udmi.schema.PointsetState;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

/**
 * Unit tests for a service pod.
 */
public class UdmiServicePodTest {

  private static final String CONFIG_FILE = "src/test/configs/base_pod.json";
  private static final String BRIDGE_FILE = "src/test/configs/bridge_pod.json";
  private static final String TRACE_FILE = "src/test/configs/trace_pod.json";
  private static final String TARGET_FILE = "null/null/devices/null/001_event_pointset.json";
  private static final long RECEIVE_TIMEOUT_SEC = 2;
  private static final long RECEIVE_TIMEOUT_MS = RECEIVE_TIMEOUT_SEC * 1000;

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
    UdmiServicePod pod = new UdmiServicePod(arrayOf(CONFIG_FILE));

    PodConfiguration podConfig = pod.getPodConfiguration();

    EndpointConfiguration reversedState =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.flows.get("state")));
    final MessageDispatcherImpl stateDispatcher = MessageTestBase.getDispatcherFor(reversedState);

    EndpointConfiguration reversedTarget =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.flows.get("target")));
    final MessageDispatcherImpl targetDispatcher = MessageTestBase.getDispatcherFor(reversedTarget);

    pod.activate();

    CompletableFuture<DiscoveryState> received = new CompletableFuture<>();
    targetDispatcher.registerHandler(DiscoveryState.class, received::complete);
    BlockingQueue<Object> defaulted = new LinkedBlockingQueue<>();
    targetDispatcher.registerHandler(Object.class, defaulted::add);
    targetDispatcher.activate();

    StateUpdate stateUpdate = new StateUpdate();
    stateUpdate.discovery = new DiscoveryState();
    stateUpdate.pointset = new PointsetState();
    stateDispatcher.publish(stateUpdate);

    DiscoveryState discoveryState = received.get(RECEIVE_TIMEOUT_SEC, TimeUnit.SECONDS);
    assertNotNull(discoveryState, "no received message");

    Object polled = defaulted.poll(RECEIVE_TIMEOUT_SEC, TimeUnit.SECONDS);
    assertTrue(polled instanceof PointsetState, "expected pointset state in default");

    assertNull(defaulted.poll(RECEIVE_TIMEOUT_SEC, TimeUnit.SECONDS));
  }

  @Test
  public void bridgeTest() throws Exception {
    UdmiServicePod pod = new UdmiServicePod(arrayOf(BRIDGE_FILE));

    PodConfiguration podConfig = pod.getPodConfiguration();

    EndpointConfiguration reversedFrom =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.bridges.get("test").from));
    final MessageDispatcherImpl fromDispatcher = MessageTestBase.getDispatcherFor(reversedFrom);
    EndpointConfiguration reversedTo =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.bridges.get("test").to));
    final MessageDispatcherImpl toDispatcher = MessageTestBase.getDispatcherFor(reversedTo);

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
  public void podTraceTest() throws Exception {
    UdmiServicePod pod = new UdmiServicePod(arrayOf(TRACE_FILE));
    PodConfiguration podConfiguration = pod.getPodConfiguration();
    File outDir = new File(podConfiguration.bridges.get("trace").from.send_id);
    deleteDirectory(outDir);
    File targetFile = new File(outDir, TARGET_FILE);
    System.err.println("Target out " + targetFile.getAbsolutePath());
    assertFalse(targetFile.exists(), "target file exists and should not");
    pod.activate();
    Thread.sleep(RECEIVE_TIMEOUT_MS);
    pod.shutdown();
    assertTrue(targetFile.exists(), "missing target output file");
  }

  @Test
  public void reflectPodTest() throws Exception {
    ProcessorTestBase.writeVersionDeployFile();
    UdmiServicePod pod = new UdmiServicePod(arrayOf(CONFIG_FILE));

    PodConfiguration podConfig = pod.getPodConfiguration();
    EndpointConfiguration reversedTarget =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.flows.get("target")));
    final MessageDispatcherImpl targetDispatcher = MessageTestBase.getDispatcherFor(reversedTarget);

    EndpointConfiguration reversedReflect =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.flows.get("reflect")));
    final MessageDispatcherImpl reflectDispatcher =
        MessageTestBase.getDispatcherFor(reversedReflect);

    pod.activate();

    BlockingQueue<Object> defaulted = new LinkedBlockingQueue<>();
    targetDispatcher.registerHandler(Object.class, defaulted::add);
    targetDispatcher.activate();

    UdmiState stateUpdate = new UdmiState();
    reflectDispatcher.publish(stateUpdate);

    pod.shutdown();
    targetDispatcher.shutdown();
    reflectDispatcher.shutdown();

    Object polled = defaulted.poll(RECEIVE_TIMEOUT_SEC, TimeUnit.SECONDS);
    assertTrue(polled instanceof UdmiConfig, "expected one UdmiConfig message");

    assertNull(defaulted.poll(RECEIVE_TIMEOUT_SEC, TimeUnit.SECONDS),
        "expected no other messages");
  }

  @AfterEach
  public void resetForTest() {
    LocalMessagePipe.resetForTestStatic();
  }
}