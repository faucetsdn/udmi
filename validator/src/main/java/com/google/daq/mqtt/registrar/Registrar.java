
package com.google.daq.mqtt.registrar;

import static com.google.daq.mqtt.registrar.LocalDevice.GATEWAY_SUBFOLDER;
import static com.google.daq.mqtt.registrar.LocalDevice.LOCALNET_SUBFOLDER;
import static com.google.daq.mqtt.registrar.LocalDevice.POINTSET_SUBFOLDER;
import static com.google.daq.mqtt.registrar.LocalDevice.SYSTEM_SUBFOLDER;
import static com.google.daq.mqtt.validator.Validator.NO_SITE;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.download.URIDownloader;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.DeviceCredential;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.daq.mqtt.util.CloudDeviceSettings;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.ExceptionMap;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import com.google.daq.mqtt.util.PubSubPusher;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import udmi.schema.Config;

public class Registrar {

  static final String METADATA_JSON = "metadata.json";
  static final String ENVELOPE_JSON = "envelope.json";

  static final String NORMALIZED_JSON = "metadata_norm.json";
  static final String DEVICE_ERRORS_JSON = "errors.json";
  static final String GENERATED_CONFIG_JSON = "generated_config.json";

  private static final String DEVICES_DIR = "devices";
  private static final String ERROR_FORMAT_INDENT = "  ";

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
  public static final String SCHEMA_BASE_PATH = "schema";
  private static final String UDMI_VERSION_KEY = "UDMI_VERSION";
  private static final String VERSION_KEY = "Version";
  public static final String VERSION_MAIN_KEY = "main";
  private static final String SCHEMA_SUFFIX = ".json";
  public static final String REGISTRATION_SUMMARY_JSON = "registration_summary.json";
  private static final String SCHEMA_NAME = "UDMI";

  private CloudIotManager cloudIotManager;
  private File siteDir;
  private final Map<String, JsonSchema> schemas = new HashMap<>();
  private File schemaBase;
  private PubSubPusher pubSubPusher;
  private Map<String, LocalDevice> localDevices;
  private File summaryFile;
  private ExceptionMap blockErrors;
  private String projectId;
  private final String generation = getGenerationString();

  public static void main(String[] args) {
    Registrar registrar = new Registrar();
    try {
      if (args.length < 2) {
        throw new IllegalArgumentException("Args: tool_root site_dir [project_id] [devices...]");
      }
      registrar.setSchemaBase(new File(args[0], SCHEMA_BASE_PATH).getPath());
      registrar.setSitePath(args[1]);
      if (args.length > 2) {
        registrar.setProjectId(args[2]);
      }
      if (args.length > 3) {
        String[] devices = new String[args.length - 3];
        System.arraycopy(args, 3, devices, 0, args.length - 3);
        registrar.processDevices(devices);
      } else {
        registrar.processDevices();
      }

      registrar.writeErrors();
      registrar.shutdown();
    } catch (ExceptionMap em) {
      ErrorTree errorTree = ExceptionMap.format(em, ERROR_FORMAT_INDENT);
      errorTree.write(System.err);
      System.exit(2);
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(-1);
    }
    System.exit(0);
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
  }

  private void setSitePath(String sitePath) {
    Preconditions.checkNotNull(SCHEMA_NAME, "schemaName not set yet");
    siteDir = new File(sitePath);
    summaryFile = new File(siteDir, REGISTRATION_SUMMARY_JSON);
    summaryFile.delete();
  }

  private void initializeCloudProject() {
    File cloudIotConfig = new File(siteDir, ConfigUtil.CLOUD_IOT_CONFIG_JSON);
    System.err.println("Reading Cloud IoT config from " + cloudIotConfig.getAbsolutePath());
    cloudIotManager = new CloudIotManager(projectId, cloudIotConfig, SCHEMA_NAME);
    String configTopic = cloudIotManager.cloudIotConfig.config_topic;
    System.err.println("Sending updates to config topic " + configTopic);
    if (configTopic != null) {
      pubSubPusher = new PubSubPusher(projectId, configTopic);
    }
    System.err.println(
        String.format(
            "Working with project %s registry %s/%s",
            cloudIotManager.getProjectId(),
            cloudIotManager.getCloudRegion(),
            cloudIotManager.getRegistryId()));
  }

  private void processDevices() {
    processDevices(null);
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

  private void processDevices(String[] devices) {
    Set<String> deviceSet = calculateDevices(devices);
    try {
      final Set<String> extraDevices;
      localDevices = loadLocalDevices();
      if (localOnly()) {
        extraDevices = new HashSet<>();
      } else {
        List<Device> cloudDevices = fetchDeviceList();
        extraDevices = cloudDevices.stream().map(Device::getId).collect(toSet());
      }
      if (deviceSet != null) {
        Set<String> unknowns = Sets.difference(deviceSet, localDevices.keySet());
        if (!unknowns.isEmpty()) {
          throw new RuntimeException(
              "Unknown specified devices: " + Joiner.on(", ").join(unknowns));
        }
      }
      for (String localName : localDevices.keySet()) {
        LocalDevice localDevice = localDevices.get(localName);
        if (!localDevice.isValid()) {
          System.err.println("Skipping (invalid) " + localName);
          continue;
        }
        extraDevices.remove(localName);
        try {
          localDevice.writeConfigFile();
          if ((deviceSet == null || deviceSet.contains(localName)) && !localOnly()) {
            updateCloudIoT(localDevice);
            Device device =
                Preconditions.checkNotNull(fetchDevice(localName), "missing device " + localName);
            BigInteger numId =
                Preconditions.checkNotNull(
                    device.getNumId(), "missing deviceNumId for " + localName);
            localDevice.setDeviceNumId(numId.toString());
            sendConfigMessages(localDevice);
          }
        } catch (Exception e) {
          System.err.println("Deferring exception: " + e.toString());
          localDevice.captureError(LocalDevice.EXCEPTION_REGISTERING, e);
        }
      }
      if (!localOnly()) {
        bindGatewayDevices(localDevices, deviceSet);
        blockErrors = blockExtraDevices(extraDevices);
      }
      System.err.println(String.format("Processed %d devices", localDevices.size()));
    } catch (Exception e) {
      throw new RuntimeException("While processing devices", e);
    }
  }

  private Set<String> calculateDevices(String[] devices) {
    if (devices == null) {
      return null;
    }
    return Arrays.stream(devices).map(this::deviceNameFromPath).collect(Collectors.toSet());
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

  private void sendConfigMessages(LocalDevice localDevice) {
    System.err.println("Sending config messages for " + localDevice.getDeviceId());

    Config deviceConfig = localDevice.deviceConfigObject();
    sendConfigMessage(localDevice, SYSTEM_SUBFOLDER, deviceConfig.system);
    sendConfigMessage(localDevice, POINTSET_SUBFOLDER, deviceConfig.pointset);
    sendConfigMessage(localDevice, GATEWAY_SUBFOLDER, deviceConfig.gateway);
    sendConfigMessage(localDevice, LOCALNET_SUBFOLDER, deviceConfig.localnet);
  }

  private void sendConfigMessage(LocalDevice localDevice, String subFolder, Object subConfig) {
    try {
      Map<String, String> attributes = new HashMap<>();
      attributes.put("deviceId", localDevice.getDeviceId());
      attributes.put("deviceNumId", localDevice.getDeviceNumId());
      attributes.put("deviceRegistryId", cloudIotManager.getRegistryId());
      attributes.put("projectId", cloudIotManager.getProjectId());
      attributes.put("subFolder", subFolder);
      String messageString = OBJECT_MAPPER.writeValueAsString(subConfig);
      if (pubSubPusher != null) {
        pubSubPusher.sendMessage(attributes, messageString);
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException("While sending config " + subFolder, e);
    }
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
    if (pubSubPusher != null) {
      pubSubPusher.shutdown();
    }
  }

  private List<Device> fetchDeviceList() {
    if (localOnly()) {
      System.err.println("Skipping remote registry fetch");
      return ImmutableList.of();
    } else {
      System.err.println("Fetching remote registry " + cloudIotManager.getRegistryPath());
      return cloudIotManager.fetchDeviceList();
    }
  }

  private boolean localOnly() {
    return projectId == null;
  }

  private Map<String, LocalDevice> loadLocalDevices() {
    File devicesDir = new File(siteDir, DEVICES_DIR);
    String[] devices = devicesDir.list();
    Preconditions.checkNotNull(devices, "No devices found in " + devicesDir.getAbsolutePath());
    Map<String, LocalDevice> localDevices = loadDevices(siteDir, devicesDir, devices);
    writeNormalized(localDevices);
    validateKeys(localDevices);
    validateExpected(localDevices);
    validateSamples(localDevices);
    return localDevices;
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
    for (String deviceName : devices) {
      if (LocalDevice.deviceExists(devicesDir, deviceName)) {
        System.err.println("Loading local device " + deviceName);
        LocalDevice localDevice =
            localDevices.computeIfAbsent(
                deviceName,
                keyName -> new LocalDevice(siteDir, devicesDir, deviceName, schemas, generation));
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
    return localDevices;
  }

  private void setProjectId(String projectId) {
    if (NO_SITE.equals(projectId) || projectId == null) {
      return;
    }
    this.projectId = projectId;
    initializeCloudProject();
  }

  private void setSchemaBase(String schemaBasePath) {
    schemaBase = new File(schemaBasePath);
    File[] schemaFiles = schemaBase.listFiles(file -> file.getName().endsWith(SCHEMA_SUFFIX));
    for (File schemaFile : Objects.requireNonNull(schemaFiles)) {
      loadSchema(schemaFile.getName());
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
