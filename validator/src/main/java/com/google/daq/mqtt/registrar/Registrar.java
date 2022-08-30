package com.google.daq.mqtt.registrar;

import static com.google.daq.mqtt.util.Common.NO_SITE;
import static com.google.daq.mqtt.util.JsonUtil.OBJECT_MAPPER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.download.URIDownloader;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.DeviceCredential;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.daq.mqtt.util.CloudDeviceSettings;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ExceptionMap;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import com.google.daq.mqtt.util.PubSubPusher;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Metadata;

/**
 * Validate devices' static metadata and register them in the cloud.
 */
public class Registrar {

  public static final String SCHEMA_BASE_PATH = "schema";
  static final String METADATA_JSON = "metadata.json";
  static final String ENVELOPE_JSON = "envelope.json";
  static final String NORMALIZED_JSON = "metadata_norm.json";
  static final String DEVICE_ERRORS_JSON = "errors.json";
  static final String GENERATED_CONFIG_JSON = "generated_config.json";
  private static final String DEVICES_DIR = "devices";
  private static final String ERROR_FORMAT_INDENT = "  ";
  private static final String UDMI_VERSION_KEY = "UDMI_VERSION";
  private static final String VERSION_KEY = "Version";
  private static final String VERSION_MAIN_KEY = "main";
  private static final String SCHEMA_SUFFIX = ".json";
  private static final String REGISTRATION_SUMMARY_JSON = "registration_summary.json";
  private static final String SCHEMA_NAME = "UDMI";
  private static final String SITE_METADATA_JSON = "site_metadata.json";
  private static final String SWARM_SUBFOLDER = "swarm";
  private static final long PROCESSING_TIMEOUT_MIN = 60;
  private static final int RUNNER_THREADS = 25;
  private static final String CONFIG_SUB_TYPE = "config";
  private static final String MODEL_SUB_TYPE = "model";
  private final Map<String, JsonSchema> schemas = new HashMap<>();
  private final String generation = getGenerationString();
  private CloudIotManager cloudIotManager;
  private File siteDir;
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
  private Map<String, Map<String, String>> lastErrorSummary;
  private boolean validateMetadata = false;
  private List<String> deviceList;

  /**
   * Main entry point for registrar.
   *
   * @param args Standard command line arguments
   */
  public static void main(String[] args) {
    ArrayList<String> argList = new ArrayList<>(List.of(args));
    Registrar registrar = new Registrar();
    processArgs(argList, registrar);
    registrar.execute();
  }

  static void processArgs(List<String> argList, Registrar registrar) {
    while (argList.size() > 0) {
      String option = argList.remove(0);
      switch (option) {
        case "-r":
          registrar.setToolRoot(argList.remove(0));
          break;
        case "-p":
          registrar.setProjectId(argList.remove(0));
          break;
        case "-s":
          registrar.setSitePath(argList.remove(0));
          break;
        case "-f":
          registrar.setFeedTopic(argList.remove(0));
          break;
        case "-u":
          registrar.setUpdateFlag(true);
          break;
        case "-l":
          registrar.setIdleLimit(argList.remove(0));
          break;
        case "-t":
          registrar.setValidateMetadata(true);
          break;
        case "--":
          break;
        default:
          if (option.startsWith("-")) {
            throw new RuntimeException("Unknown cmdline option " + option);
          }
          // Add the current non-option back into the list and use it as device names list.
          argList.add(0, option);
          registrar.setDeviceList(argList);
          return;
      }
    }
  }

  void execute() {
    try {
      if (schemaBase == null) {
        setToolRoot(null);
      }
      loadSiteMetadata();
      processDevices();
      writeErrors();
      shutdown();
    } catch (ExceptionMap em) {
      ExceptionMap.format(em, ERROR_FORMAT_INDENT).write(System.err);
      throw new RuntimeException("mapped exceptions", em);
    } catch (Exception ex) {
      ex.printStackTrace();
      throw new RuntimeException("main exception", ex);
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
  }

  private void setFeedTopic(String feedTopic) {
    System.err.println("Sending device feed to topic " + feedTopic);
    feedPusher = new PubSubPusher(projectId, feedTopic);
    System.err.println("Checking subscription for existing messages...");
    if (!feedPusher.isEmpty()) {
      throw new RuntimeException("Feed is not empty, do you have enough pullers?");
    }
  }

  protected Map<String, Map<String, String>> getLastErrorSummary() {
    return lastErrorSummary;
  }

  private void writeErrors() throws Exception {
    Map<String, Map<String, String>> errorSummary = new TreeMap<>();
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
              entries.stream()
                  .forEach(
                      error ->
                          errorSummary
                              .computeIfAbsent(error.getKey(), cat -> new TreeMap<>())
                              .put(device.getDeviceId(), error.getValue().message));
              if (entries.isEmpty()) {
                errorSummary
                    .computeIfAbsent("Clean", cat -> new TreeMap<>())
                    .put(device.getDeviceId(), device.getNormalizedTimestamp());
              }
            });
    if (blockErrors != null && !blockErrors.isEmpty()) {
      errorSummary.put(
          "Block",
          blockErrors.stream()
              .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString())));
    }
    System.err.println("\nSummary:");
    errorSummary.forEach(
        (key, value) -> System.err.println("  Device " + key + ": " + value.size()));
    System.err.println("Out of " + localDevices.size() + " total.");
    String version = Optional.ofNullable(System.getenv(UDMI_VERSION_KEY)).orElse("unknown");
    errorSummary.put(VERSION_KEY, Map.of(VERSION_MAIN_KEY, version));
    OBJECT_MAPPER.writeValue(summaryFile, errorSummary);
    lastErrorSummary = errorSummary;
  }

  protected void setSitePath(String sitePath) {
    Preconditions.checkNotNull(SCHEMA_NAME, "schemaName not set yet");
    siteDir = new File(sitePath);
    summaryFile = new File(siteDir, REGISTRATION_SUMMARY_JSON);
    summaryFile.delete();
  }

  private void initializeCloudProject() {
    cloudIotManager = new CloudIotManager(projectId, siteDir);
    System.err.println(
        String.format(
            "Working with project %s registry %s/%s",
            cloudIotManager.getProjectId(),
            cloudIotManager.getCloudRegion(),
            cloudIotManager.getRegistryId()));

    if (cloudIotManager.getUpdateTopic() != null) {
      updatePusher = new PubSubPusher(projectId, cloudIotManager.getUpdateTopic());
    }
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
    processDevices(this.deviceList);
  }

  private void processDevices(List<String> devices) {
    Set<String> deviceSet = calculateDevices(devices);
    AtomicInteger updatedCount = new AtomicInteger();
    AtomicInteger processedCount = new AtomicInteger();
    try {
      localDevices = loadLocalDevices(deviceSet);
      cloudDevices = fetchCloudDevices();

      if (deviceSet != null) {
        Set<String> unknowns = Sets.difference(deviceSet, localDevices.keySet());
        if (!unknowns.isEmpty()) {
          throw new RuntimeException(
              "Unknown specified devices: " + Joiner.on(", ").join(unknowns));
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

  private void processLocalDevices(AtomicInteger updatedCount, AtomicInteger processedCount)
      throws InterruptedException {
    ExecutorService executor = Executors.newFixedThreadPool(RUNNER_THREADS);
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
  }

  private void processLocalDevice(String localName, AtomicInteger processedDeviceCount) {
    LocalDevice localDevice = localDevices.get(localName);
    if (!localDevice.isValid()) {
      System.err.println("Skipping (invalid) " + localName);
      return;
    }
    if (shouldLimitDevice(localDevice)) {
      // System.err.println("Skipping active device " + localDevice.getDeviceId());
      return;
    }
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
      System.err.println("Deferring exception: " + e);
      localDevice.captureError(LocalDevice.EXCEPTION_REGISTERING, e);
    }
  }

  private boolean shouldLimitDevice(LocalDevice localDevice) {
    try {
      if (idleLimit == null) {
        return false;
      }
      Device device = cloudIotManager.fetchDevice(localDevice.getDeviceId());
      if (device == null || device.getLastEventTime() == null || idleLimit == null) {
        return false;
      }
      return Instant.now().minus(idleLimit).isBefore(Instant.parse(device.getLastEventTime()));
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
      attributes.put("deviceRegistryId", cloudIotManager.cloudIotConfig.registry_id);
      attributes.put("deviceRegistryLocation", cloudIotManager.cloudIotConfig.cloud_region);
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
    Device device =
        Preconditions.checkNotNull(fetchDevice(localName), "missing device " + localName);
    BigInteger numId =
        Preconditions.checkNotNull(
            device.getNumId(), "missing deviceNumId for " + localName);
    localDevice.setDeviceNumId(numId.toString());
  }

  private void updateCloudIoT(LocalDevice localDevice) {
    String localName = localDevice.getDeviceId();
    fetchDevice(localName);
    CloudDeviceSettings localDeviceSettings = localDevice.getSettings();
    if (cloudIotManager.registerDevice(localName, localDeviceSettings)) {
      System.err.println("Created new device entry " + localName);
    } else {
      System.err.println("Updated device entry " + localName);
    }
  }

  private Set<String> calculateDevices(List<String> devices) {
    if (devices == null) {
      return null;
    }
    return devices.stream().map(this::deviceNameFromPath).collect(Collectors.toSet());
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
    if (!cloudIotManager.cloudIotConfig.block_unknown) {
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

  private Device fetchDevice(String localName) {
    try {
      return cloudIotManager.fetchDevice(localName);
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
    localDevices.values().stream()
        .filter(localDevice -> localDevice.getSettings().proxyDevices != null)
        .forEach(
            localDevice ->
                localDevice.getSettings().proxyDevices.stream()
                    .filter(proxyDevice -> deviceSet == null || deviceSet.contains(proxyDevice))
                    .forEach(
                        proxyDeviceId -> {
                          try {
                            String gatewayId = localDevice.getDeviceId();
                            System.err.println(
                                "Binding " + proxyDeviceId + " to gateway " + gatewayId);
                            cloudIotManager.bindDevice(proxyDeviceId, gatewayId);
                          } catch (Exception e) {
                            throw new RuntimeException("While binding device " + proxyDeviceId, e);
                          }
                        }));
  }

  private void shutdown() {
    if (updatePusher != null) {
      updatePusher.shutdown();
    }
    if (feedPusher != null) {
      feedPusher.shutdown();
    }
  }

  private Set<String> fetchCloudDevices() {
    boolean requiresCloud = updateCloudIoT || (idleLimit != null);
    if (requiresCloud) {
      Set<String> devices = cloudIotManager.fetchDeviceList();
      System.err.printf("Fetched %d devices from cloud registry %s%n",
          devices.size(), cloudIotManager.getRegistryPath());
      return devices;
    } else {
      System.err.println("Skipping remote registry fetch");
      return null;
    }
  }

  private Map<String, LocalDevice> loadLocalDevices(Set<String> specifiedDevices) {
    Preconditions.checkNotNull(siteDir, "site directory");
    File devicesDir = new File(siteDir, DEVICES_DIR);
    if (!devicesDir.isDirectory()) {
      throw new RuntimeException("Not a valid directory: " + devicesDir.getAbsolutePath());
    }

    final String[] devices;
    if (specifiedDevices == null) {
      devices = devicesDir.list();
    } else {
      devices = devicesDir.list(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return specifiedDevices.contains(name);
        }
      });
    }

    Preconditions.checkNotNull(devices, "No devices found in " + devicesDir.getAbsolutePath());
    Map<String, LocalDevice> localDevices = loadDevices(siteDir, devicesDir, devices);
    initializeSettings(localDevices);
    writeNormalized(localDevices);
    validateKeys(localDevices);
    validateExpected(localDevices);
    validateSamples(localDevices);
    return localDevices;
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
    Map<DeviceCredential, String> usedCredentials = new HashMap<>();
    localDevices.values().stream()
        .filter(LocalDevice::isDirectConnect)
        .forEach(
            localDevice -> {
              CloudDeviceSettings settings = localDevice.getSettings();
              String deviceName = localDevice.getDeviceId();
              for (DeviceCredential credential : settings.credentials) {
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
      String[] devices) {
    HashMap<String, LocalDevice> localDevices = new HashMap<>();
    AtomicInteger loaded = new AtomicInteger(0);
    for (String deviceName : devices) {
      int count = loaded.incrementAndGet();
      if (LocalDevice.deviceExists(devicesDir, deviceName)) {
        if (devices.length < 100) {
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

  protected void setProjectId(String projectId) {
    if (NO_SITE.equals(projectId) || projectId == null) {
      return;
    }
    this.projectId = projectId;
    initializeCloudProject();
  }

  protected void setToolRoot(String toolRoot) {
    schemaBase = new File(toolRoot, SCHEMA_BASE_PATH);
    File[] schemaFiles = schemaBase.listFiles(file -> file.getName().endsWith(SCHEMA_SUFFIX));
    for (File schemaFile : Objects.requireNonNull(schemaFiles)) {
      loadSchema(schemaFile.getName());
    }
    if (schemas.isEmpty()) {
      throw new RuntimeException(
          "No schemas successfully loaded from " + schemaBase.getAbsolutePath());
    }
  }

  private void loadSchema(String key) {
    File schemaFile = new File(schemaBase, key);
    try (InputStream schemaStream = new FileInputStream(schemaFile)) {
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

  protected Map<String, JsonSchema> getSchemas() {
    return schemas;
  }

  class RelativeDownloader implements URIDownloader {

    @Override
    public InputStream fetch(URI source) {
      try {
        return new FileInputStream(new File(schemaBase, source.getSchemeSpecificPart()));
      } catch (Exception e) {
        throw new RuntimeException("While loading sub-schema " + source, e);
      }
    }
  }
}
