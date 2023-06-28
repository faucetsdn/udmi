package com.google.bos.udmi.service.pod;

import static com.google.bos.udmi.service.messaging.impl.MessageBase.combineConfig;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.loadFileStrictRequired;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.core.BridgeProcessor;
import com.google.bos.udmi.service.core.ProcessorBase;
import com.google.bos.udmi.service.core.ReflectProcessor;
import com.google.bos.udmi.service.core.StateProcessor;
import com.google.bos.udmi.service.core.TargetProcessor;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import udmi.schema.BridgePodConfiguration;
import udmi.schema.EndpointConfiguration;
import udmi.schema.IotAccess;
import udmi.schema.PodConfiguration;

/**
 * Main entrypoint wrapper for a UDMI service pod.
 */
public class UdmiServicePod {

  private static final Map<String, ContainerBase> COMPONENT_MAP = new ConcurrentHashMap<>();
  public static final String DEFAULT_PROVIDER_KEY = "default";
  private static final Map<String, BridgePodConfiguration> NO_BRIDGES = ImmutableMap.of();
  private static final Map<String, EndpointConfiguration> NO_FLOWS = ImmutableMap.of();
  private static final Map<String, IotAccess> NO_ACCESS = ImmutableMap.of();
  private static final Map<String, Class<? extends ProcessorBase>> PROCESSORS = ImmutableMap.of(
      "target", TargetProcessor.class,
      "reflect", ReflectProcessor.class,
      "state", StateProcessor.class
  );
  private final PodConfiguration podConfiguration;

  /**
   * Core pod to instantiate all the other components as necessary based on configuration.
   */
  public UdmiServicePod(String[] args) {
    try {
      checkState(args.length == 1, "expected exactly one argument: configuration_file");

      podConfiguration = loadFileStrictRequired(PodConfiguration.class, args[0]);

      ifNotNullThen(podConfiguration.flows, flows -> flows.forEach(this::createFlow));
      ifNotNullThen(podConfiguration.bridges, bridges -> bridges.forEach(this::createBridge));
      ifNotNullThen(podConfiguration.iot_access, access -> access.forEach(this::createAccess));
    } catch (Exception e) {
      throw new RuntimeException("While instantiating pod " + CSV_JOINER.join(args), e);
    }
  }

  public static void main(String[] args) {
    UdmiServicePod udmiServicePod = new UdmiServicePod(args);
    udmiServicePod.activate();
  }

  private void createFlow(String name, EndpointConfiguration config) {
    checkState(PROCESSORS.containsKey(name), "registered flow key " + name);
    Class<? extends ProcessorBase> clazz = PROCESSORS.get(name);
    putComponent(name, ProcessorBase.create(clazz, makeConfig(config)));
  }

  private void createBridge(String name, BridgePodConfiguration config) {
    try {
      EndpointConfiguration from = makeConfig(config.from);
      EndpointConfiguration to = makeConfig(config.to);
      putComponent(name, new BridgeProcessor(from, to));
    } catch (Exception e) {
      throw new RuntimeException("While making bridge " + name, e);
    }
  }

  private void createAccess(String name, IotAccess config) {
    putComponent(name, IotAccessBase.from(config));
  }

  private EndpointConfiguration makeConfig(EndpointConfiguration defined) {
    return combineConfig(podConfiguration.flow_defaults, defined);
  }

  /**
   * Activate all processors and components in the pod.
   */
  public void activate() {
    forAllComponents(ContainerBase::activate);
  }

  public PodConfiguration getPodConfiguration() {
    return podConfiguration;
  }

  /**
   * Shutdown all processors and bridges in the pod.
   */
  public void shutdown() {
    forAllComponents(ContainerBase::shutdown);
  }

  public static void resetForTest() {
    COMPONENT_MAP.clear();
  }

  @SuppressWarnings("unchecked")
  public static <T> T getComponent(String name) {
    return (T) requireNonNull(COMPONENT_MAP.get(name), "missing component " + name);
  }

  @SuppressWarnings("unchecked")
  public static <T> T getComponent(String name, Class<T> clazz) {
    return (T) requireNonNull(COMPONENT_MAP.get(name), "missing component " + name);
  }

  /**
   * Put this component into the central component registry.
   */
  public static void putComponent(String componentName, ContainerBase component) {
    ifNotNullThen(COMPONENT_MAP.put(componentName, component),
        replaced -> {
          throw new IllegalStateException(
              format("Conflicting objects for component %s: %s replacing %s",
                  componentName, component.getClass(), replaced.getClass()));
        });
  }

  public static void forAllComponents(Consumer<ContainerBase> action) {
    COMPONENT_MAP.values().forEach(action);
  }
}
