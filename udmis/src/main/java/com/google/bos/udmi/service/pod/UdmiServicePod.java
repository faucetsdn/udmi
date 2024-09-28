package com.google.bos.udmi.service.pod;

import static com.google.bos.udmi.service.messaging.impl.MessageBase.combineConfig;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.copyFields;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotNullThrow;
import static com.google.udmi.util.GeneralUtils.mergeObject;
import static com.google.udmi.util.JsonUtil.loadFileStrictRequired;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.access.IotAccessProvider;
import com.google.bos.udmi.service.core.BitboxAdapter;
import com.google.bos.udmi.service.core.BridgeProcessor;
import com.google.bos.udmi.service.core.ControlProcessor;
import com.google.bos.udmi.service.core.CronProcessor;
import com.google.bos.udmi.service.core.DistributorPipe;
import com.google.bos.udmi.service.core.ProcessorBase;
import com.google.bos.udmi.service.core.ProvisioningEngine;
import com.google.bos.udmi.service.core.ReflectProcessor;
import com.google.bos.udmi.service.core.StateProcessor;
import com.google.bos.udmi.service.core.TargetProcessor;
import com.google.bos.udmi.service.support.IotDataProvider;
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
import udmi.schema.Level;
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
  private static final Map<String, UdmiComponent> COMPONENT_MAP = new ConcurrentHashMap<>();
  private static final Set<Class<? extends ProcessorBase>> PROCESSOR_CLASSES = ImmutableSet.of(
      TargetProcessor.class, ReflectProcessor.class, StateProcessor.class, ControlProcessor.class,
      ProvisioningEngine.class, BitboxAdapter.class, DistributorPipe.class);
  private static final Map<String, Class<? extends ProcessorBase>> PROCESSORS =
      PROCESSOR_CLASSES.stream().collect(Collectors.toMap(ContainerBase::getName, clazz -> clazz));

  /**
   * Core pod to instantiate all the other components as necessary based on configuration.
   */
  public UdmiServicePod(String[] args) {
    super(makePodConfiguration(args));
    try {
      ifNotNullThen(podConfiguration.iot_data, db -> db.forEach(this::createIotData));
      ifNotNullThen(podConfiguration.iot_access, access -> access.forEach(this::createAccess));
      ifNotNullThen(podConfiguration.flows, flows -> flows.forEach(this::createFlow));
      ifNotNullThen(podConfiguration.bridges, bridges -> bridges.forEach(this::createBridge));
      ifNotNullThen(podConfiguration.crons, cron -> cron.forEach(this::createCron));
    } catch (Exception e) {
      throw new RuntimeException("Fatal error instantiating pod " + CSV_JOINER.join(args), e);
    }
  }

  /**
   * Loop through all the registered components and apply the given action.
   */
  public static void forAllComponents(Consumer<UdmiComponent> action) {
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
    udmiConfig.setup.functions_min = ContainerBase.FUNCTIONS_VERSION_MIN;
    udmiConfig.setup.functions_max = ContainerBase.FUNCTIONS_VERSION_MAX;
    udmiConfig.setup.transaction_id = catchToNull(() -> toolState.setup.transaction_id);
    udmiConfig.setup.msg_source = ifNotNullGet(toolState, t -> t.source);
    return udmiConfig;
  }

  private static PodConfiguration loadRecursive(File loadFile) {
    System.err.println("Loading config file " + loadFile.getAbsolutePath());
    PodConfiguration loaded = loadFileStrictRequired(PodConfiguration.class, loadFile);
    return ifNotNullGet(loaded.include, include -> {
      File includeRaw = new File(include);
      File includeFile =
          includeRaw.isAbsolute() ? includeRaw : new File(loadFile.getParentFile(), include);
      PodConfiguration underConfig = loadRecursive(includeFile);
      loaded.include = null;
      return mergeObject(underConfig, loaded);
    }, loaded);
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
      e.printStackTrace();
      System.exit(FATAL_ERROR_CODE);
    }
  }

  private static PodConfiguration makePodConfiguration(String[] args) {
    if (args.length != 1) {
      throw new RuntimeException("Exactly one argument expected: pod_config.json");
    }
    PodConfiguration config = loadRecursive(new File(args[0]));
    System.err.println(stringify(config));
    ifNotNullThrow(config.include, "unresolved config include directive");
    return config;
  }

  public static <T> T maybeGetComponent(Class<T> clazz) {
    return maybeGetComponent(ContainerBase.getName(clazz));
  }

  @SuppressWarnings("unchecked")
  public static <T> T maybeGetComponent(String name) {
    return ifNotNullGet(name, x -> (T) COMPONENT_MAP.get(name));
  }

  /**
   * Put this component into the central component registry.
   */
  public static void putComponent(String componentName, Supplier<UdmiComponent> component) {
    try {
      UdmiComponent container = component.get();
      ifNotNullThen(COMPONENT_MAP.put(componentName, container),
          replaced -> {
            throw new IllegalStateException(
                format("Conflicting objects for component %s: %s replacing %s",
                    componentName, component.getClass(), replaced.getClass()));
          });
      container.output(Level.DEBUG, format("Added component %s of type %s",
          componentName, container.getClass().getSimpleName()));
    } catch (Exception e) {
      throw new RuntimeException("While creating component " + componentName, e);
    }
  }

  public static void resetForTest() {
    COMPONENT_MAP.clear();
    READY_INDICATOR.delete();
  }

  private static void setConfigName(EndpointConfiguration config, String name) {
    boolean notSetOrEqual = config.name == null || config.name.equals(name);
    checkState(notSetOrEqual, "config name already set, was " + config.name);
    config.name = name;
  }

  private void createAccess(String name, IotAccess config) {
    config.name = name;
    putComponent(name, () -> IotAccessProvider.from(config));
  }

  private void createBridge(String name, BridgePodConfiguration config) {
    String enabled = variableSubstitution(config.enabled);
    if (enabled != null && enabled.isEmpty()) {
      warn("Skipping not-enabled bridge " + name);
      return;
    }
    info(format("Creating bridge %s with enabled %s", name, config.enabled));
    EndpointConfiguration from = makeConfig(config.from);
    EndpointConfiguration morf = makeConfig(config.morf);
    setConfigName(from, name);
    putComponent(name, () -> new BridgeProcessor(from, morf));
  }

  private void createCron(String name, EndpointConfiguration config) {
    setConfigName(config, name);
    putComponent(name, () -> ProcessorBase.create(CronProcessor.class, makeConfig(config)));
  }

  private void createFlow(String name, EndpointConfiguration config) {
    checkState(PROCESSORS.containsKey(name), "unknown flow key " + name);
    Class<? extends ProcessorBase> clazz = PROCESSORS.get(name);
    setConfigName(config, name);
    putComponent(name, () -> ProcessorBase.create(clazz, makeConfig(config)));
  }

  private void createIotData(String name, IotAccess config) {
    config.name = name;
    putComponent(name, () -> IotDataProvider.from(config));
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
      forAllComponents(UdmiComponent::activate);
      checkState(READY_INDICATOR.createNewFile(), "ready file already exists: " + absolutePath);
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
    forAllComponents(UdmiComponent::shutdown);
    notice("Finished shutdown of container components");
    super.shutdown();
  }
}
