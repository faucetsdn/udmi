package com.google.bos.udmi.service.pod;

import static com.google.bos.udmi.service.messaging.impl.MessageBase.combineConfig;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.copyFields;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.loadFileStrictRequired;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.core.BridgeProcessor;
import com.google.bos.udmi.service.core.DistributorPipe;
import com.google.bos.udmi.service.core.ProcessorBase;
import com.google.bos.udmi.service.core.ReflectProcessor;
import com.google.bos.udmi.service.core.StateProcessor;
import com.google.bos.udmi.service.core.TargetProcessor;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import udmi.schema.BridgePodConfiguration;
import udmi.schema.EndpointConfiguration;
import udmi.schema.IotAccess;
import udmi.schema.PodConfiguration;
import udmi.schema.SetupUdmiConfig;
import udmi.schema.UdmiConfig;
import udmi.schema.UdmiState;

/**
 * Main entrypoint wrapper for a UDMI service pod.
 */
public class UdmiServicePod extends ContainerBase {

  public static final String HOSTNAME = System.getenv("HOSTNAME");
  public static final String DEPLOY_FILE = "var/deployed_version.json";
  public static final String UDMI_VERSION = requireNonNull(getDeployedConfig().udmi_version);
  public static final int FATAL_ERROR_CODE = -1;
  static final File READY_INDICATOR = new File("/tmp/pod_ready.txt");
  private static final Map<String, ContainerBase> COMPONENT_MAP = new ConcurrentHashMap<>();
  private static final Set<Class<? extends ProcessorBase>> PROCESSOR_CLASSES = ImmutableSet.of(
      TargetProcessor.class, ReflectProcessor.class, StateProcessor.class);
  private static final Map<String, Class<? extends ProcessorBase>> PROCESSORS =
      PROCESSOR_CLASSES.stream().collect(Collectors.toMap(ContainerBase::getName, clazz -> clazz));
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
      ifNotNullThen(podConfiguration.distributors, dist -> dist.forEach(this::createDistributor));
    } catch (Exception e) {
      throw new RuntimeException("Fatal error instantiating pod " + CSV_JOINER.join(args), e);
    }
  }

  /**
   * Loop through all the registered components and apply the given action.
   */
  public static void forAllComponents(Consumer<ContainerBase> action) {
    COMPONENT_MAP.forEach((key, value) -> {
      try {
        action.accept(value);
      } catch (Exception e) {
        throw new RuntimeException("While processing component " + key, e);
      }
    });
  }

  public static <T> T getComponent(String name) {
    return requireNonNull(maybeGetComponent(name), "missing component " + name);
  }

  public static <T> T getComponent(Class<T> clazz) {
    String name = ContainerBase.getName(clazz);
    return requireNonNull(maybeGetComponent(name), "missing component " + name);
  }

  public static SetupUdmiConfig getDeployedConfig() {
    return loadFileStrictRequired(SetupUdmiConfig.class, new File(DEPLOY_FILE));
  }

  /**
   * Create a new UdmiConfig object for reporting system setup.
   */
  @NotNull
  public static UdmiConfig getUdmiConfig(UdmiState toolState) {
    UdmiConfig udmiConfig = new UdmiConfig();
    udmiConfig.last_state = ifNotNullGet(toolState, state -> state.timestamp);
    udmiConfig.setup = new SetupUdmiConfig();
    copyFields(getDeployedConfig(), udmiConfig.setup, false);
    udmiConfig.setup.hostname = HOSTNAME;
    udmiConfig.setup.udmi_version = UDMI_VERSION;
    udmiConfig.setup.functions_min = ProcessorBase.FUNCTIONS_VERSION_MIN;
    udmiConfig.setup.functions_max = ProcessorBase.FUNCTIONS_VERSION_MAX;
    return udmiConfig;
  }

  /**
   * Instantiate and activate the service pod.
   */
  public static void main(String[] args) {
    try {
      UdmiServicePod udmiServicePod = new UdmiServicePod(args);
      Runtime.getRuntime().addShutdownHook(new Thread(udmiServicePod::shutdown));
      udmiServicePod.activate();
    } catch (Exception e) {
      System.err.println("Exception activating pod: " + friendlyStackTrace(e));
      System.exit(FATAL_ERROR_CODE);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T maybeGetComponent(String name) {
    return ifNotNullGet(name, x -> (T) COMPONENT_MAP.get(name));
  }

  /**
   * Put this component into the central component registry.
   */
  public static void putComponent(String componentName, Supplier<ContainerBase> component) {
    try {
      ContainerBase container = component.get();
      ifNotNullThen(COMPONENT_MAP.put(componentName, container),
          replaced -> {
            throw new IllegalStateException(
                format("Conflicting objects for component %s: %s replacing %s",
                    componentName, component.getClass(), replaced.getClass()));
          });
      container.debug("Added component %s of type %s", componentName,
          container.getClass().getSimpleName());
    } catch (Exception e) {
      throw new RuntimeException("While creating component " + componentName, e);
    }
  }

  public static void resetForTest() {
    COMPONENT_MAP.clear();
    READY_INDICATOR.delete();
  }

  private void createAccess(String name, IotAccess config) {
    putComponent(name, () -> IotAccessBase.from(config));
  }

  private void createBridge(String name, BridgePodConfiguration config) {
    EndpointConfiguration from = makeConfig(config.from);
    EndpointConfiguration to = makeConfig(config.to);
    putComponent(name, () -> new BridgeProcessor(from, to));
  }

  private void createDistributor(String name, EndpointConfiguration config) {
    putComponent(name, () -> DistributorPipe.from(config));
  }

  private void createFlow(String name, EndpointConfiguration config) {
    checkState(PROCESSORS.containsKey(name), "unknown flow key " + name);
    Class<? extends ProcessorBase> clazz = PROCESSORS.get(name);
    putComponent(name, () -> ProcessorBase.create(clazz, makeConfig(config)));
  }

  private EndpointConfiguration makeConfig(EndpointConfiguration defined) {
    return combineConfig(podConfiguration.flow_defaults, defined);
  }

  /**
   * Activate all processors and components in the pod.
   */
  @Override
  public void activate() {
    super.activate();
    notice("Starting activation of container components");
    String absolutePath = READY_INDICATOR.getAbsolutePath();
    try {
      forAllComponents(ContainerBase::activate);
      checkState(READY_INDICATOR.createNewFile(), "ready file already exists");
      READY_INDICATOR.deleteOnExit();
    } catch (Exception e) {
      throw new RuntimeException("While activating pod", e);
    }
    notice("Finished activation of container components, created " + absolutePath);
  }

  public PodConfiguration getPodConfiguration() {
    return podConfiguration;
  }

  /**
   * Shutdown all processors and bridges in the pod.
   */
  @Override
  public void shutdown() {
    notice("Starting shutdown of container components");
    forAllComponents(ContainerBase::shutdown);
    notice("Finished shutdown of container components");
    super.shutdown();
  }
}
