package com.google.bos.udmi.service.pod;

import static com.google.bos.udmi.service.messaging.impl.MessageBase.combineConfig;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.loadFileStrictRequired;
import static com.google.udmi.util.JsonUtil.toMap;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.access.IotAccessProvider;
import com.google.bos.udmi.service.core.BridgeProcessor;
import com.google.bos.udmi.service.core.ReflectProcessor;
import com.google.bos.udmi.service.core.StateProcessor;
import com.google.bos.udmi.service.core.TargetProcessor;
import com.google.bos.udmi.service.core.UdmisComponent;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import udmi.schema.BridgePodConfiguration;
import udmi.schema.EndpointConfiguration;
import udmi.schema.PodConfiguration;

/**
 * Main entrypoint wrapper for a UDMI service pod.
 */
public class UdmiServicePod {

  public static final String DEFAULT_PROVIDER_KEY = "default";
  private static final Map<String, BridgePodConfiguration> NO_BRIDGES = ImmutableMap.of();
  private static final Map<String, EndpointConfiguration> NO_FLOWS = ImmutableMap.of();
  private static final Map<String, Class<? extends UdmisComponent>> PROCESSORS = ImmutableMap.of(
      "target", TargetProcessor.class,
      "reflect", ReflectProcessor.class,
      "state", StateProcessor.class
  );
  private final PodConfiguration podConfiguration;
  private final Map<Class<?>, UdmisComponent> components;
  private final List<BridgeProcessor> bridges;
  private IotAccessProvider iotAccessProvider;

  /**
   * Core pod to instantiate all the other components as necessary based on configuration.
   */
  public UdmiServicePod(String[] args) {
    try {
      checkState(args.length == 1, "expected exactly one argument: configuration_file");

      podConfiguration = loadFileStrictRequired(PodConfiguration.class, args[0]);

      Map<String, EndpointConfiguration> flowEntries = podConfiguration.flows;
      components = ofNullable(flowEntries).orElse(NO_FLOWS).entrySet().stream()
          .map(this::makeComponentFor).collect(Collectors.toMap(UdmisComponent::getClass,
              thing -> thing));

      Map<String, BridgePodConfiguration> bridgeEntries = podConfiguration.bridges;
      bridges = ofNullable(bridgeEntries).orElse(NO_BRIDGES).entrySet().stream()
          .map(this::makeBridgeFor).collect(Collectors.toList());

      Map<String, IotAccessProvider> providerMap =
          podConfiguration.iot_access.entrySet().stream()
              .collect(Collectors.toMap(Entry::getKey, IotAccessProvider::from));
      IotAccessProvider provider = providerMap.get(DEFAULT_PROVIDER_KEY);
      provider.setProviders(providerMap);
      setIotAccessProvider(provider);
    } catch (Exception e) {
      throw new RuntimeException("While instantiating pod " + CSV_JOINER.join(args), e);
    }
  }

  public static void main(String[] args) {
    UdmiServicePod udmiServicePod = new UdmiServicePod(args);
    udmiServicePod.activate();
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

  private UdmisComponent makeComponentFor(Entry<String, EndpointConfiguration> entry) {
    checkState(PROCESSORS.containsKey(entry.getKey()), "registered flow key " + entry.getKey());
    return createComponent(PROCESSORS.get(entry.getKey()), makeConfig(entry.getValue()));
  }

  private EndpointConfiguration makeConfig(EndpointConfiguration defined) {
    return combineConfig(podConfiguration.flow_defaults, defined);
  }

  /**
   * Activate all processors and components in the pod.
   */
  public void activate() {
    ifNotNullThen(iotAccessProvider, IotAccessProvider::activate);
    components.values().forEach(UdmisComponent::activate);
    bridges.forEach(BridgeProcessor::activate);
  }

  public PodConfiguration getPodConfiguration() {
    return podConfiguration;
  }

  public void setIotAccessProvider(IotAccessProvider iotAccessProvider) {
    this.iotAccessProvider = iotAccessProvider;
    components.values().forEach(target -> target.setIotAccessProvider(iotAccessProvider));
  }

  /**
   * Shutdown all processors and bridges in the pod.
   */
  public void shutdown() {
    bridges.forEach(BridgeProcessor::shutdown);
    components.values().forEach(UdmisComponent::shutdown);
    ifNotNullThen(iotAccessProvider, IotAccessProvider::shutdown);
  }
}
