package com.google.daq.mqtt.registrar;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.intersection;
import static com.google.daq.mqtt.util.ConfigUtil.readExeConfig;
import static com.google.udmi.util.Common.CLOUD_VERSION_KEY;
import static com.google.udmi.util.Common.NO_SITE;
import static com.google.udmi.util.Common.UDMI_VERSION_KEY;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.OBJECT_MAPPER;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.download.URIDownloader;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.daq.mqtt.util.CloudDeviceSettings;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.DeviceExceptionManager;
import com.google.daq.mqtt.util.ExceptionMap;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import com.google.daq.mqtt.util.PubSubPusher;
import com.google.udmi.util.Common;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import udmi.schema.CloudModel;
import udmi.schema.Credential;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.IotAccess.IotProvider;
import udmi.schema.Metadata;
import udmi.schema.SetupUdmiConfig;

/**
 * Validate devices' static metadata and register them in the cloud.
 */
public class Registrar {

  public static final String SCHEMA_BASE_PATH = "schema";
  public static final Joiner JOIN_CSV = Joiner.on(", ");
  static final String METADATA_JSON = "metadata.json";
  static final String ENVELOPE_JSON = "envelope.json";
  static final String NORMALIZED_JSON = "metadata_norm.json";
  static final String DEVICE_ERRORS_JSON = "errors.json";
  static final String GENERATED_CONFIG_JSON = "generated_config.json";
  private static final String DEVICES_DIR = "devices";
  private static final String SCHEMA_SUFFIX = ".json";
  private static final String REGISTRATION_SUMMARY_JSON = "out/registration_summary.json";
  private static final String SCHEMA_NAME = "UDMI";
  private static final String SITE_METADATA_JSON = "site_metadata.json";
  private static final String SWARM_SUBFOLDER = "swarm";
  private static final long PROCESSING_TIMEOUT_MIN = 60;
  private static final int RUNNER_THREADS = 20;
  private static final String CONFIG_SUB_TYPE = "config";
  private static final String MODEL_SUB_TYPE = "model";
  private static final boolean DEFAULT_BLOCK_UNKNOWN = true;
  private final Map<String, JsonSchema> schemas = new HashMap<>();
  private final String generation = getGenerationString();
  private CloudIotManager cloudIotManager;
  private File schemaBase;
  private PubSubPusher updatePusher;
  private PubSubPusher feedPusher;
  private Map<String, LocalDevice> localDevices;
  private File summaryFile;
  private ExceptionMap blockErrors;
  private String projectId;
  private boolean updateCloudIoT;
  private Duration idleLimit;
  private Set<String> cloudDevices;
  private Metadata siteMetadata;
  private Map<String, Object> lastErrorSummary;
  private boolean validateMetadata = false;
  private List<String> deviceList;
  private Boolean blockUnknown;
  private File siteDir;
  private String registrySuffix;
  private boolean useAltRegistry;
  private String altRegistry;
  private boolean deleteDevices;
  private IotProvider iotProvider;
  private File profile;

  /**
   * Main entry point for registrar.
   *
   * @param args Standard command line arguments
   */
  public static void main(String[] args) {
    ArrayList<String> argList = new ArrayList<>(List.of(args));
    new Registrar().processArgs(argList).execute();
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private static Map<String, String> getErrorKeyMap(Map<String, Object> resultMap,
      String errorKey) {
    return (Map<String, String>) resultMap.computeIfAbsent(errorKey, cat -> new TreeMap<>());
  }

  @SuppressWarnings("unchecked")
  private static int getErrorSummaryDetail(Object value) {
    return ((Map<String, Object>) value).size();
  }

  Registrar processArgs(List<String> argListRaw) {
    List<String> argList = new ArrayList<>(argListRaw);
    if (!argList.isEmpty() && !argList.get(0).startsWith("-")) {
      processProfile(new File(argList.remove(0)));
    }
    while (argList.size() > 0) {
      String option = argList.remove(0);
      switch (option) {
        case "-r":
          setToolRoot(argList.remove(0));
          break;
        case "-p":
          setProjectId(argList.remove(0), null);
          break;
        case "-s":
          setSitePath(argList.remove(0));
          break;
        case "-a":
          setUseAltRegistry(true);
          break;
        case "-f":
          setFeedTopic(argList.remove(0));
          break;
        case "-u":
          setUpdateFlag(true);
          break;
        case "-l":
          setIdleLimit(argList.remove(0));
          break;
        case "-t":
          setValidateMetadata(true);
          break;
        case "-d":
          setDeleteDevices(true);
          break;
        case "--":
          setDeviceList(argList);
          return this;
        default:
          if (option.startsWith("-")) {
            throw new RuntimeException("Unknown cmdline option " + option);
          }
          // Add the current non-option back into the list and use it as device names list.
          argList.add(0, option);
          setDeviceList(argList);
          return this;
      }
    }
    return this;
  }

  private void setDeleteDevices(boolean deleteDevices) {
    this.deleteDevices = deleteDevices;
    checkNotNull(projectId, "delete devices specified with no target project");
    this.updateCloudIoT = deleteDevices;
  }

  private void setUseAltRegistry(boolean useAltRegistry) {
    this.useAltRegistry = useAltRegistry;
  }

  private void processProfile(File profilePath) {
    System.err.println("Reading registrar configuration from " + profilePath.getAbsolutePath());
    if (profilePath.isFile()) {
      profile = profilePath;
    }
    processProfile(readExeConfig(profilePath));
  }

  Registrar processProfile(ExecutionConfiguration config) {
    setSitePath(config.site_model);
    altRegistry = config.alt_registry;
    iotProvider = config.iot_provider;
    setProjectId(config.project_id, config.registry_suffix);
    setValidateMetadata(true);
    if (config.project_id != null) {
      setUpdateFlag(true);
    }
    return this;
  }

  void execute() {
    try {
      if (projectId != null) {
        initializeCloudProject();
      }
      if (schemaBase == null) {
        setToolRoot(null);
      }
      loadSiteMetadata();
      processDevices();
      writeErrors();
    } catch (ExceptionMap em) {
      ExceptionMap.format(em).write(System.err);
      throw new RuntimeException("mapped exceptions", em);
    } catch (Exception ex) {
      ex.printStackTrace();
      throw new RuntimeException("main exception", ex);
    } finally {
      shutdown();
    }
  }

  private void setIdleLimit(String option) {
    idleLimit = Duration.parse("PT" + option);
    System.err.println("Limiting devices to duration " + idleLimit.toSeconds() + "s");
  }

  private void setUpdateFlag(boolean update) {
    updateCloudIoT = update;
  }

  private void setValidateMetadata(boolean validateMetadata) {
    this.validateMetadata = validateMetadata;
  }

  private void setDeviceList(List<String> deviceList) {
    this.deviceList = deviceList;
    blockUnknown = false;
  }

  private void setFeedTopic(String feedTopic) {
    System.err.println("Sending device feed to topic " + feedTopic);
    feedPusher = new PubSubPusher(projectId, feedTopic);
    System.err.println("Checking subscription for existing messages...");
    if (!feedPusher.isEmpty()) {
      throw new RuntimeException("Feed is not empty, do you have enough pullers?");
    }
  }

  protected Map<String, Object> getLastErrorSummary() {
    return lastErrorSummary;
  }

  private void writeErrors() throws Exception {
    if (localDevices == null) {
      return;
    }

    Map<String, Object> errorSummary = new TreeMap<>();
    DeviceExceptionManager dem = new DeviceExceptionManager(siteDir);
    localDevices
        .values()
        .forEach(device -> device.writeErrors(dem.forDevice(device.getDeviceId())));
    localDevices
        .values()
        .forEach(
            device -> {
              Set<Entry<String, ErrorTree>> entries =
                  device.getTreeChildren(dem.forDevice(device.getDeviceId()));
              if (entries.isEmpty()) {
                getErrorKeyMap(errorSummary, "Clean")
                    .put(device.getDeviceId(), device.getNormalizedTimestamp());
              } else {
                entries
                    .forEach(
                        error -> getErrorKeyMap(errorSummary, error.getKey())
                            .put(device.getDeviceId(), error.getValue().message));
              }
            });
    if (blockErrors != null && !blockErrors.isEmpty()) {
      errorSummary.put(
          "Block",
          blockErrors.stream()
              .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString())));
    }
    System.err.println("\nSummary:");
    errorSummary.forEach((key, value) -> System.err.println(
        "  Device " + key + ": " + getErrorSummaryDetail(value)));
    System.err.println("Out of " + localDevices.size() + " total.");
    errorSummary.put(CLOUD_VERSION_KEY, getCloudVersionInfo());
    errorSummary.put(UDMI_VERSION_KEY, Common.getUdmiVersion());
    OBJECT_MAPPER.writeValue(summaryFile, errorSummary);
    lastErrorSummary = errorSummary;
    System.err.println("Registration summary available in " + summaryFile.getAbsolutePath());
  }

  private SetupUdmiConfig getCloudVersionInfo() {
    return ifNotNullGet(cloudIotManager, CloudIotManager::getVersionInformation);
  }

  protected void setSitePath(String sitePath) {
    checkNotNull(SCHEMA_NAME, "schemaName not set yet");
    siteDir = new File(sitePath);
    summaryFile = new File(siteDir, REGISTRATION_SUMMARY_JSON);
    File parentFile = summaryFile.getParentFile();
    if (!parentFile.isDirectory() && !parentFile.mkdirs()) {
      throw new IllegalStateException("Could not create directory " + parentFile.getAbsolutePath());
    }
    summaryFile.delete();
  }

  private void initializeCloudProject() {
    String useRegistry = !useAltRegistry ? null
        : requireNonNull(altRegistry, "No alt_registry supplied with useAltRegistry true");
    if (profile != null) {
      cloudIotManager = new CloudIotManager(profile);
    } else {
      cloudIotManager = new CloudIotManager(projectId, siteDir, useRegistry, registrySuffix,
          iotProvider);
    }
    System.err.printf(
        "Working with project %s registry %s/%s%n",
        cloudIotManager.getProjectId(),
        cloudIotManager.getCloudRegion(),
        cloudIotManager.getRegistryId());

    if (cloudIotManager.getUpdateTopic() != null) {
      updatePusher = new PubSubPusher(projectId, cloudIotManager.getUpdateTopic());
    }
    blockUnknown = ofNullable(blockUnknown)
        .orElse(Objects.requireNonNullElse(cloudIotManager.executionConfiguration.block_unknown,
            DEFAULT_BLOCK_UNKNOWN));
  }

  private String getGenerationString() {
    try {
      Date generationDate = new Date();
      String quotedString = OBJECT_MAPPER.writeValueAsString(generationDate);
      return quotedString.substring(1, quotedString.length() - 1);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("While forming generation timestamp", e);
    }
  }

  private void processDevices() {
    Set<String> deviceSet = calculateDevices();
    AtomicInteger updatedCount = new AtomicInteger();
    AtomicInteger processedCount = new AtomicInteger();
    try {
      localDevices = loadLocalDevices(deviceSet);
      cloudDevices = ifNotNullGet(fetchCloudDevices(), devices -> new HashSet<>(devices));
      if (deleteDevices) {
        deleteCloudDevices(deviceSet);
        return;
      }

      if (deviceSet != null) {
        Set<String> unknowns = Sets.difference(deviceSet, localDevices.keySet());
        if (!unknowns.isEmpty()) {
          throw new RuntimeException(
              "Unknown specified devices: " + JOIN_CSV.join(unknowns));
        }
      }

      processLocalDevices(updatedCount, processedCount);
      System.err.printf("Finished processing %d device records...%n", processedCount.get());

      if (updateCloudIoT) {
        bindGatewayDevices(localDevices, deviceSet);
        Set<String> extraDevices = cloudDevices == null ? ImmutableSet.of()
            : Sets.difference(cloudDevices, localDevices.keySet());
        blockErrors = blockExtraDevices(extraDevices);
      }
      System.err.printf("Updated %d devices (out of %d)%n", updatedCount.get(),
          localDevices.size());
      if (cloudDevices != null) {
        Set<String> unknownDevices = Sets.difference(localDevices.keySet(), cloudDevices);
        System.err.printf("Skipped %d not-in-cloud devices.%n", unknownDevices.size());
      }
    } catch (Exception e) {
      throw new RuntimeException("While processing devices", e);
    }
  }

  private void deleteCloudDevices(Set<String> deviceSet) {
    try {
      if (cloudDevices.isEmpty()) {
        System.err.println("No devices to delete, our work here is done!");
        return;
      }
      Set<String> union = intersection(cloudDevices, ofNullable(deviceSet).orElse(cloudDevices));
      Set<String> gateways = union.stream().filter(id -> ifNotNullGet(localDevices.get(id),
          LocalDevice::isGateway, false)).collect(Collectors.toSet());
      Set<String> others = Sets.difference(union, gateways);

      ExecutorService executor = Executors.newFixedThreadPool(RUNNER_THREADS);
      executor.execute(() -> System.err.println(
          "Deleting devices executing in pool " + Thread.currentThread().getName()));
      final Instant start = Instant.now();

      // Delete gateways first so that they aren't holding the other devices hostage.
      gateways.forEach(id -> executor.execute(() -> deleteDevice(id)));
      Thread.sleep(1000); // Some time to let the gateways get deleted.
      others.forEach(id -> executor.execute(() -> deleteDevice((id))));

      executor.shutdown();
      executor.awaitTermination(PROCESSING_TIMEOUT_MIN, TimeUnit.MINUTES);

      Duration between = Duration.between(start, Instant.now());
      double seconds = between.getSeconds() + between.getNano() / 1e9;
      System.err.printf("Deleted %d devices in %.03fs%n",
          (gateways.size() + others.size()), seconds);

      Set<String> deviceIds = fetchCloudDevices();
      Set<String> remaining = intersection(deviceIds, ofNullable(deviceSet).orElse(deviceIds));
      if (!remaining.isEmpty()) {
        throw new RuntimeException("Did not delete all devices! " + CSV_JOINER.join(remaining));
      }
    } catch (Exception e) {
      throw new RuntimeException("While deleting cloud devices", e);
    }
  }

  private void deleteDevice(String deviceId) {
    try {
      System.err.println("Deleting device " + deviceId);
      cloudIotManager.deleteDevice(deviceId);
    } catch (Exception e) {
      throw new RuntimeException("While deleting device " + deviceId, e);
    }
  }

  private void processLocalDevices(AtomicInteger updatedCount, AtomicInteger processedCount) {
    try {
      ExecutorService executor = Executors.newFixedThreadPool(RUNNER_THREADS);
      executor.execute(() -> System.err.println(
          "Local devices executing in pool " + Thread.currentThread().getName()));
      final Instant start = Instant.now();
      for (String localName : localDevices.keySet()) {
        executor.execute(() -> {
          int count = processedCount.incrementAndGet();
          if (count % 500 == 0) {
            System.err.printf("Processed %d device records...%n", count);
          }
          processLocalDevice(localName, updatedCount);
        });
      }
      executor.shutdown();
      executor.awaitTermination(PROCESSING_TIMEOUT_MIN, TimeUnit.MINUTES);

      Duration between = Duration.between(start, Instant.now());
      double seconds = between.getSeconds() + between.getNano() / 1e9;
      double perDevice = Math.floor(seconds / localDevices.size() * 1000.0) / 1000.0;
      System.err.printf("Finished %d devices in %.03f, %.03fs/d%n",
          localDevices.size(), seconds, perDevice);
    } catch (Exception e) {
      throw new RuntimeException("While processing local devices", e);
    }
  }

  private void processLocalDevice(String localName, AtomicInteger processedDeviceCount) {
    LocalDevice localDevice = localDevices.get(localName);
    if (!localDevice.isValid()) {
      System.err.println("Skipping invalid device " + localName);
      return;
    }
    if (shouldLimitDevice(localDevice)) {
      System.err.println("Skipping active device " + localDevice.getDeviceId());
      return;
    }
    System.err.println("Processing device " + localName);
    Instant start = Instant.now();
    processedDeviceCount.incrementAndGet();
    try {
      localDevice.writeConfigFile();
      if (cloudDevices != null) {
        updateCloudIoT(localName, localDevice);
        sendUpdateMessages(localDevice);
        if (cloudDevices.contains(localName)) {
          sendFeedMessage(localDevice);
        }
      }
    } catch (Exception e) {
      System.err.printf("Deferring exception for %s: %s%n", localDevice.getDeviceId(), e);
      localDevice.captureError(LocalDevice.EXCEPTION_REGISTERING, e);
    }
    Duration between = Duration.between(start, Instant.now());
    double seconds = (between.getSeconds() + between.getNano() / 1e9) / RUNNER_THREADS;
    System.err.printf("Finished processing %s in %.03fs%n", localName, seconds);
  }

  private boolean shouldLimitDevice(LocalDevice localDevice) {
    try {
      if (idleLimit == null) {
        return false;
      }
      CloudModel device = cloudIotManager.fetchDevice(localDevice.getDeviceId());
      if (device == null || device.last_event_time == null || idleLimit == null) {
        return false;
      }
      return Instant.now().minus(idleLimit).isBefore(device.last_event_time.toInstant());
    } catch (Exception e) {
      throw new RuntimeException("While checking device limit " + localDevice.getDeviceId(), e);
    }
  }

  private boolean sendFeedMessage(LocalDevice localDevice) {
    if (feedPusher == null) {
      return false;
    }

    if (!localDevice.isDirectConnect()) {
      System.err.println("Skipping feed message for proxy device " + localDevice.getDeviceId());
      return false;
    }

    System.err.println("Sending feed message for " + localDevice.getDeviceId());

    try {
      Map<String, String> attributes = new HashMap<>();
      attributes.put("subFolder", SWARM_SUBFOLDER);
      attributes.put("deviceId", localDevice.getDeviceId());
      attributes.put("deviceRegistryId", cloudIotManager.executionConfiguration.registry_id);
      attributes.put("deviceRegistryLocation", cloudIotManager.executionConfiguration.cloud_region);
      SwarmMessage swarmMessage = new SwarmMessage();
      swarmMessage.key_base64 = Base64.getEncoder().encodeToString(localDevice.getKeyBytes());
      swarmMessage.device_metadata = localDevice.getMetadata();
      String swarmString = OBJECT_MAPPER.writeValueAsString(swarmMessage);
      feedPusher.sendMessage(attributes, swarmString);
    } catch (Exception e) {
      throw new RuntimeException("While sending swarm feed message", e);
    }
    return true;
  }

  private void updateCloudIoT(String localName, LocalDevice localDevice) {
    if (!updateCloudIoT) {
      return;
    }

    updateCloudIoT(localDevice);
    CloudModel device =
        checkNotNull(fetchDevice(localName), "missing device " + localName);
    String numId =
        checkNotNull(
            device.num_id, "missing deviceNumId for " + localName);
    localDevice.setDeviceNumId(numId);
  }

  private void updateCloudIoT(LocalDevice localDevice) {
    String localName = localDevice.getDeviceId();
    fetchDevice(localName);
    CloudDeviceSettings localDeviceSettings = localDevice.getSettings();
    if (preDeleteDevice(localName)) {
      System.err.println("Deleting to incite recreation " + localName);
      cloudIotManager.deleteDevice(localName);
    }
    if (cloudIotManager.registerDevice(localName, localDeviceSettings)) {
      System.err.println("Created new device entry " + localName);
      cloudDevices.add(localName);
    } else {
      System.err.println("Updated device entry " + localName);
    }
  }

  private boolean preDeleteDevice(String localName) {
    if (!localDevices.get(localName).isGateway()) {
      return false;
    }
    CloudModel registeredDevice = cloudIotManager.getRegisteredDevice(localName);
    return ifNotNullGet(registeredDevice, device -> !isTrue(device.is_gateway), false);
  }

  private Set<String> calculateDevices() {
    if (deviceList == null) {
      return null;
    }
    return deviceList.stream().map(this::deviceNameFromPath).collect(Collectors.toSet());
  }

  private String deviceNameFromPath(String device) {
    while (device.endsWith("/")) {
      device = device.substring(0, device.length() - 1);
    }
    int slashPos = device.lastIndexOf('/');
    if (slashPos >= 0) {
      device = device.substring(slashPos + 1);
    }
    return device;
  }

  private ExceptionMap blockExtraDevices(Set<String> extraDevices) {
    ExceptionMap exceptionMap = new ExceptionMap("Block devices errors");
    if (!blockUnknown) {
      return exceptionMap;
    }
    for (String extraName : extraDevices) {
      try {
        System.err.println("Blocking extra device " + extraName);
        cloudIotManager.blockDevice(extraName, true);
      } catch (Exception e) {
        exceptionMap.put(extraName, e);
      }
    }
    return exceptionMap;
  }

  private CloudModel fetchDevice(String localName) {
    try {
      return cloudDevices.contains(localName) ? cloudIotManager.fetchDevice(localName) : null;
    } catch (Exception e) {
      throw new RuntimeException("Fetching device " + localName, e);
    }
  }

  private void sendUpdateMessages(LocalDevice localDevice) {
    if (updatePusher != null) {
      System.err.println("Sending model/config update for " + localDevice.getDeviceId());
      sendUpdateMessage(localDevice, SubFolder.SYSTEM);
      sendUpdateMessage(localDevice, SubFolder.POINTSET);
      sendUpdateMessage(localDevice, SubFolder.GATEWAY);
      sendUpdateMessage(localDevice, SubFolder.LOCALNET);
    }
  }

  private void sendUpdateMessage(LocalDevice localDevice, SubFolder subFolder) {
    sendUpdateMessage(localDevice, MODEL_SUB_TYPE, subFolder, localDevice.getMetadata());
    sendUpdateMessage(localDevice, CONFIG_SUB_TYPE, subFolder, localDevice.deviceConfigObject());
  }

  private void sendUpdateMessage(LocalDevice localDevice, String subType, SubFolder subfolder,
      Object target) {
    String fieldName = subfolder.toString().toLowerCase();
    try {
      Field declaredField = target.getClass().getDeclaredField(fieldName);
      sendSubMessage(localDevice, subType, subfolder, declaredField.get(target));
    } catch (Exception e) {
      throw new RuntimeException(String.format("Getting field %s from target %s", fieldName,
          target.getClass().getSimpleName()));
    }
  }

  private void sendSubMessage(LocalDevice localDevice, String subType, SubFolder subFolder,
      Object subConfig) {
    try {
      Map<String, String> attributes = new HashMap<>();
      attributes.put("deviceId", localDevice.getDeviceId());
      attributes.put("deviceNumId", localDevice.getDeviceNumId());
      attributes.put("deviceRegistryId", cloudIotManager.getRegistryId());
      attributes.put("projectId", cloudIotManager.getProjectId());
      attributes.put("subType", subType);
      attributes.put("subFolder", subFolder.value());
      String messageString = OBJECT_MAPPER.writeValueAsString(subConfig);
      updatePusher.sendMessage(attributes, messageString);
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("Sending %s/%s messages for %s%n", subType, subFolder,
              localDevice.getDeviceId()), e);
    }
  }

  private void bindGatewayDevices(Map<String, LocalDevice> localDevices, Set<String> deviceSet) {
    try {
      ExecutorService executor = Executors.newFixedThreadPool(RUNNER_THREADS);
      executor.execute(() -> System.err.println(
          "Gateway binding executing in pool " + Thread.currentThread().getName()));
      final Instant start = Instant.now();
      for (LocalDevice localDevice : localDevices.values()) {
        ifTrueThen(localDevice.isGateway(), () -> executor.execute(
            () -> bindGatewayDevice(localDevices, deviceSet, localDevice)));
      }
      executor.shutdown();
      executor.awaitTermination(PROCESSING_TIMEOUT_MIN, TimeUnit.MINUTES);

      Duration between = Duration.between(start, Instant.now());
      double seconds = between.getSeconds() + between.getNano() / 1e9;
      System.err.printf("Finished binding gateways in %.03f%n", seconds);
    } catch (Exception e) {
      throw new RuntimeException("While binding gateways", e);
    }
  }

  private void bindGatewayDevice(Map<String, LocalDevice> localDevices, Set<String> deviceSet,
      LocalDevice localDevice) {
    String gatewayId = localDevice.getDeviceId();
    try {
      Set<String> boundDevices = cloudIotManager.fetchBoundDevices(gatewayId);
      System.err.printf("Binding devices to %s, already bound: %s%n",
          gatewayId, JOIN_CSV.join(boundDevices));
      int total = cloudDevices.size() != 0 ? cloudDevices.size() : localDevices.size();
      Preconditions.checkState(boundDevices.size() != total,
          "all devices including the gateway can't be bound to one gateway!");
      localDevice.getSettings().proxyDevices.stream()
          .filter(proxyDevice -> deviceSet == null || deviceSet.contains(proxyDevice))
          .filter(proxyDeviceId -> !boundDevices.contains(proxyDeviceId))
          .forEach(
              proxyDeviceId -> {
                System.err.println(
                    "Binding " + proxyDeviceId + " to gateway " + gatewayId);
                cloudIotManager.bindDevice(proxyDeviceId, gatewayId);
              }
          );
    } catch (Exception e) {
      localDevice.captureError(LocalDevice.EXCEPTION_BINDING, e);
    }
  }

  private void shutdown() {
    if (updatePusher != null) {
      updatePusher.shutdown();
    }
    if (feedPusher != null) {
      feedPusher.shutdown();
    }
    if (cloudIotManager != null) {
      cloudIotManager.shutdown();
    }
  }

  private Set<String> fetchCloudDevices() {
    try {
      boolean requiresCloud = updateCloudIoT || (idleLimit != null);
      if (requiresCloud) {
        Set<String> devices = cloudIotManager.fetchDeviceIds();
        System.err.printf("Fetched %d devices from cloud registry %s%n", devices.size(),
            cloudIotManager.getRegistryId());
        return devices;
      } else {
        System.err.println("Skipping remote registry fetch");
        return null;
      }
    } catch (Exception e) {
      throw new RuntimeException("While fetching cloud devices", e);
    }
  }

  private Map<String, LocalDevice> loadLocalDevices(Set<String> specifiedDevices) {
    checkNotNull(siteDir, "site directory");
    File devicesDir = new File(siteDir, DEVICES_DIR);
    if (!devicesDir.isDirectory()) {
      throw new RuntimeException("Not a valid directory: " + devicesDir.getAbsolutePath());
    }

    List<String> deviceList = getDeviceList(specifiedDevices, devicesDir);
    Map<String, LocalDevice> localDevices = loadDevices(siteDir, devicesDir, deviceList);
    initializeSettings(localDevices);
    writeNormalized(localDevices);
    validateKeys(localDevices);
    validateExpected(localDevices);
    validateSamples(localDevices);
    return localDevices;
  }

  private List<String> getDeviceList(Set<String> specifiedDevices, File devicesDir) {
    return SiteModel.listDevices(devicesDir).stream()
        .filter(name -> specifiedDevices == null || specifiedDevices.contains(name))
        .collect(Collectors.toList());
  }

  private void initializeSettings(Map<String, LocalDevice> localDevices) {
    localDevices.values().forEach(LocalDevice::initializeSettings);
  }

  private void validateSamples(Map<String, LocalDevice> localDevices) {
    for (LocalDevice device : localDevices.values()) {
      try {
        device.validateSamples();
      } catch (Exception e) {
        device.captureError(LocalDevice.EXCEPTION_SAMPLES, e);
      }
    }
  }

  private void validateExpected(Map<String, LocalDevice> localDevices) {
    for (LocalDevice device : localDevices.values()) {
      try {
        device.validateExpected();
      } catch (Exception e) {
        device.captureError(LocalDevice.EXCEPTION_FILES, e);
      }
    }
  }

  private void writeNormalized(Map<String, LocalDevice> localDevices) {
    for (String deviceName : localDevices.keySet()) {
      try {
        localDevices.get(deviceName).writeNormalized();
      } catch (Exception e) {
        throw new RuntimeException("While writing normalized " + deviceName, e);
      }
    }
  }

  private void validateKeys(Map<String, LocalDevice> localDevices) {
    Map<Credential, String> usedCredentials = new HashMap<>();
    localDevices.values().stream()
        .filter(LocalDevice::isDirectConnect)
        .forEach(
            localDevice -> {
              CloudDeviceSettings settings = localDevice.getSettings();
              String deviceName = localDevice.getDeviceId();
              for (Credential credential : settings.credentials) {
                String previous = usedCredentials.put(credential, deviceName);
                if (previous != null) {
                  RuntimeException exception =
                      new RuntimeException(
                          String.format(
                              "Duplicate credentials found for %s & %s", previous, deviceName));
                  localDevice.captureError(LocalDevice.EXCEPTION_CREDENTIALS, exception);
                }
              }
            });
  }

  private Map<String, LocalDevice> loadDevices(File siteDir, File devicesDir,
      List<String> devices) {
    HashMap<String, LocalDevice> localDevices = new HashMap<>();
    AtomicInteger loaded = new AtomicInteger(0);
    for (String deviceName : devices) {
      int count = loaded.incrementAndGet();
      if (LocalDevice.deviceExists(devicesDir, deviceName)) {
        if (devices.size() < 100) {
          System.err.println("Loading local device " + deviceName);
        } else if (count % 500 == 0) {
          System.err.printf("Loaded %d devices...%n", count);
        }
        LocalDevice localDevice =
            localDevices.computeIfAbsent(
                deviceName,
                keyName -> new LocalDevice(siteDir, devicesDir, deviceName, schemas, generation,
                    siteMetadata, validateMetadata));
        try {
          localDevice.loadCredentials();
        } catch (Exception e) {
          localDevice.captureError(LocalDevice.EXCEPTION_CREDENTIALS, e);
        }
        if (cloudIotManager != null) {
          try {
            localDevice.validateEnvelope(
                cloudIotManager.getRegistryId(), cloudIotManager.getSiteName());
          } catch (Exception e) {
            localDevice.captureError(LocalDevice.EXCEPTION_ENVELOPE,
                new RuntimeException("While validating envelope", e));
          }
        }
      }
    }
    System.err.printf("Finished loading %d local devices.%n", loaded.get());
    return localDevices;
  }

  protected void setProjectId(String projectId, String registrySuffix) {
    if (NO_SITE.equals(projectId) || projectId == null) {
      return;
    }
    if (projectId.startsWith("-")) {
      throw new IllegalArgumentException("Project id starts with dash options flag " + projectId);
    }
    this.projectId = projectId;
    this.registrySuffix = registrySuffix;
  }

  protected void setToolRoot(String toolRoot) {
    schemaBase = new File(toolRoot, SCHEMA_BASE_PATH);
    File[] schemaFiles = schemaBase.listFiles(file -> file.getName().endsWith(SCHEMA_SUFFIX));
    for (File schemaFile : requireNonNull(schemaFiles)) {
      loadSchema(schemaFile.getName());
    }
    if (schemas.isEmpty()) {
      throw new RuntimeException(
          "No schemas successfully loaded from " + schemaBase.getAbsolutePath());
    }
  }

  private void loadSchema(String key) {
    File schemaFile = new File(schemaBase, key);
    try (InputStream schemaStream = Files.newInputStream(schemaFile.toPath())) {
      JsonSchema schema =
          JsonSchemaFactory.newBuilder()
              .setLoadingConfiguration(
                  LoadingConfiguration.newBuilder()
                      .addScheme("file", new RelativeDownloader())
                      .freeze())
              .freeze()
              .getJsonSchema(OBJECT_MAPPER.readTree(schemaStream));
      schemas.put(key, schema);
    } catch (Exception e) {
      throw new RuntimeException("While loading schema " + schemaFile.getAbsolutePath(), e);
    }
  }

  private void loadSiteMetadata() {
    this.siteMetadata = null;

    if (!schemas.containsKey(METADATA_JSON)) {
      return;
    }

    File siteMetadataFile = new File(siteDir, SITE_METADATA_JSON);
    try (InputStream targetStream = new FileInputStream(siteMetadataFile)) {
      // At this time, do not validate the Metadata schema because, by its nature of being
      // a partial overlay on each device Metadata, this Metadata will likely be incomplete
      // and fail validation.
      schemas.get(METADATA_JSON).validate(OBJECT_MAPPER.readTree(targetStream));
    } catch (FileNotFoundException e) {
      return;
    } catch (Exception e) {
      throw new RuntimeException("While validating " + SITE_METADATA_JSON, e);
    }

    try {
      System.err.printf("Loading " + SITE_METADATA_JSON + "\n");
      this.siteMetadata = OBJECT_MAPPER.readValue(siteMetadataFile, Metadata.class);
    } catch (Exception e) {
      throw new RuntimeException("While loading " + SITE_METADATA_JSON, e);
    }
  }

  public List<Object> getMockActions() {
    return cloudIotManager.getMockActions();
  }

  class RelativeDownloader implements URIDownloader {

    @Override
    public InputStream fetch(URI source) {
      try {
        return Files.newInputStream(new File(schemaBase, source.getSchemeSpecificPart()).toPath());
      } catch (Exception e) {
        throw new RuntimeException("While loading sub-schema " + source, e);
      }
    }
  }
}
