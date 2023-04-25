package com.google.bos.udmi.service.pod;

import static com.google.bos.udmi.service.messaging.impl.MessageBase.combineConfig;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;

import com.google.bos.udmi.service.core.BridgeProcessor;
import com.google.bos.udmi.service.core.StateProcessor;
import com.google.bos.udmi.service.core.TargetProcessor;
import com.google.bos.udmi.service.core.UdmisComponent;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.JsonUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import udmi.schema.BridgePodConfiguration;
import udmi.schema.EndpointConfiguration;
import udmi.schema.PodConfiguration;

/**
 * Main entrypoint wrapper for a UDMI service pod.
 */
public class UdmiServicePod {

  private final PodConfiguration podConfiguration;
  private final StateProcessor stateProcessor;
  private final TargetProcessor targetProcessor;
  private final List<BridgeProcessor> bridges;

  /**
   * Core pod to instantiate all the other components as necessary based on configuration.
   */
  public UdmiServicePod(String[] args) {
    try {
      checkState(args.length == 1, "expected exactly one argument: configuration_file");

      podConfiguration = JsonUtil.loadFileRequired(PodConfiguration.class, args[0]);

      Supplier<ImmutableMap<String, EndpointConfiguration>> noFlows = ImmutableMap::of;
      Map<String, EndpointConfiguration> flowEntries = podConfiguration.flows;
      Map<String, EndpointConfiguration> flows =
          new HashMap<>(Optional.ofNullable(flowEntries).orElseGet(noFlows));
      targetProcessor = createComponent(TargetProcessor.class, makeConfig(flows.remove("target")));
      stateProcessor = createComponent(StateProcessor.class, makeConfig(flows.remove("state")));
      if (!flows.isEmpty()) {
        throw new IllegalStateException(
            "Unrecognized pod flows: " + CSV_JOINER.join(flows.keySet()));
      }

      Supplier<ImmutableMap<String, BridgePodConfiguration>> noBridges = ImmutableMap::of;
      Map<String, BridgePodConfiguration> bridgeEntries = podConfiguration.bridges;
      bridges = Optional.ofNullable(bridgeEntries).orElseGet(noBridges).entrySet().stream()
          .map(this::makeBridgeFor).collect(Collectors.toList());
    } catch (Exception e) {
      throw new RuntimeException("While instantiating pod " + CSV_JOINER.join(args), e);
    }
  }

  public static void main(String[] args) {
    new UdmiServicePod(args);
  }

  private <T extends UdmisComponent> T createComponent(Class<T> clazz,
      EndpointConfiguration config) {
    return ifNotNullGet(config, () -> UdmisComponent.create(clazz, config));
  }

  private BridgeProcessor makeBridgeFor(Entry<String, BridgePodConfiguration> entry) {
    try {
      EndpointConfiguration from = makeConfig(entry.getValue().from);
      EndpointConfiguration to = makeConfig(entry.getValue().to);
      return new BridgeProcessor(from, to);
    } catch (Exception e) {
      throw new RuntimeException("While making bridge " + entry.getKey(), e);
    }
  }

  private EndpointConfiguration makeConfig(EndpointConfiguration defined) {
    return combineConfig(podConfiguration.flow_defaults, defined);
  }

  public PodConfiguration getPodConfiguration() {
    return podConfiguration;
  }
}
