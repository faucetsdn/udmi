package com.google.bos.udmi.service.pod;

import static com.google.bos.udmi.service.messaging.impl.MessageBase.combineConfig;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.messaging.StateUpdate;
import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.messaging.impl.MessageTestBase;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import udmi.schema.DiscoveryState;
import udmi.schema.EndpointConfiguration;
import udmi.schema.PodConfiguration;
import udmi.schema.PointsetState;

/**
 * Unit tests for a service pod.
 */
public class UdmiServicePodTest {

  private static final String CONFIG_FILE = "src/test/configs/base_pod.json";
  private static final long RECEIVE_TIMEOUT_SEC = 2;

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
    UdmiServicePod udmiServicePod =
        new UdmiServicePod(ImmutableList.of(CONFIG_FILE).toArray(new String[0]));

    PodConfiguration podConfig = udmiServicePod.getPodConfiguration();

    EndpointConfiguration reversedState =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.state_flow));
    final MessageDispatcherImpl stateDispatcher = MessageTestBase.getDispatcherFor(reversedState);

    EndpointConfiguration reversedTarget =
        combineConfig(podConfig.flow_defaults, reverseFlow(podConfig.target_flow));
    final MessageDispatcherImpl targetDispatcher = MessageTestBase.getDispatcherFor(reversedTarget);

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

    Assertions.assertNull(defaulted.poll(RECEIVE_TIMEOUT_SEC, TimeUnit.SECONDS));
  }
}