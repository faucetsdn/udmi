package com.google.daq.mqtt.validator;

import static com.google.daq.mqtt.util.ConfigUtil.UDMI_VERSION;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.download.URIDownloader;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.bos.iot.core.proxy.MessagePublisher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.CloudIotConfig;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.ExceptionMap;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import com.google.daq.mqtt.util.FileDataSink;
import com.google.daq.mqtt.util.PubSubClient;
import com.google.daq.mqtt.util.ValidationException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import udmi.schema.DeviceValidationEvent;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.Metadata;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;
import udmi.schema.ValidationEvent;
import udmi.schema.ValidationSummary;

/**
 * Core class for running site-level validations of data streams.
 */
public class Validator {

  public static final String STATE_QUERY_TOPIC = "query/state";
  public static final String TIMESTAMP_ATTRIBUTE = "timestamp";
  public static final String NO_SITE = "--";
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(Feature.ALLOW_COMMENTS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);
  private static final String ERROR_FORMAT_INDENT = "  ";
  private static final String JSON_SUFFIX = ".json";
  private static final String SCHEMA_VALIDATION_FORMAT = "Validating %d schemas";
  private static final String TARGET_VALIDATION_FORMAT = "Validating %d files against %s";
  private static final String DEVICE_FILE_FORMAT = "devices/%s";
  private static final String ATTRIBUTE_FILE_FORMAT = "%s.attr";
  private static final String MESSAGE_FILE_FORMAT = "%s.json";
  private static final String ERROR_FILE_FORMAT = "%s.out";
  private static final String SCHEMA_SKIP_FORMAT = "Unknown schema subFolder '%s' for %s";
  private static final String ENVELOPE_SCHEMA_ID = "envelope";
  private static final String METADATA_JSON = "metadata.json";
  private static final String DEVICES_SUBDIR = "devices";
  private static final String DEVICE_REGISTRY_ID_KEY = "deviceRegistryId";
  private static final String UNKNOWN_FOLDER_DEFAULT = "unknown";
  private static final String EVENT_POINTSET = "event_pointset";
  private static final String STATE_POINTSET = "state_pointset";
  private static final String GCP_REFLECT_KEY_PKCS8 = "validator/rsa_private.pkcs8";
  private static final String EMPTY_MESSAGE = "{}";
  private static final String CONFIG_PREFIX = "config_";
  private static final String STATE_PREFIX = "state_";
  private static final String UNKNOWN_TYPE_DEFAULT = "event";
  private static final String CONFIG_CATEGORY = "config";
  private static final Set<String> INTERESTING_TYPES = ImmutableSet.of(
      SubType.EVENT.value(),
      SubType.STATE.value());
  private static final Map<String, Class<?>> CONTENT_VALIDATORS = ImmutableMap.of(
      EVENT_POINTSET, PointsetEvent.class,
      STATE_POINTSET, PointsetState.class
  );
  private static final long REPORT_INTERVAL_SEC = 15;
  private static final String EXCLUDE_DEVICE_PREFIX = "_";
  private static final String VALIDATION_REPORT_DEVICE = "_validator";
  private static final String VALIDATION_EVENT_TOPIC = "validation/event";
  private final Map<String, ReportingDevice> expectedDevices = new TreeMap<>();
  private final Set<String> extraDevices = new TreeSet<>();
  private final Set<String> processedDevices = new TreeSet<>();
  private final Set<String> base64Devices = new TreeSet<>();
  private final Set<String> ignoredRegistries = new HashSet();
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  private final Map<String, AtomicInteger> deviceMessageIndex = new HashMap<>();
  private final List<MessagePublisher> dataSinks = new ArrayList<>();
  private final List<String> deviceIds;
  private String projectId;
  private File outBaseDir;
  private File schemaRoot;
  private String schemaSpec;
  private CloudIotConfig cloudIotConfig;
  private CloudIotManager cloudIotManager;
  private String siteDir;
  private MessagePublisher client;
  private Map<String, JsonSchema> schemaMap;
  private File traceDir;
  private boolean reportAfterEveryMessage;

  /**
   * Create validator with the given args.
   *
   * @param argList Argument list
   */
  public Validator(List<String> argList) {
    List<String> listCopy = new ArrayList<>(argList);
    parseArgs(listCopy);
    deviceIds = listCopy;
  }

  /**
   * Let's go.
   *
   * @param args Arguments for program execution
   */
  public static void main(String[] args) {
    try {
      Validator validator = new Validator(Arrays.asList(args));
      validator.messageLoop();
    } catch (ExceptionMap processingException) {
      System.exit(2);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.flush();
      System.exit(-1);
    }
    System.exit(0);
  }

  private void parseArgs(List<String> argList) {
    boolean srcDefined = false;
    boolean schemaDefined = false;
    while (argList.size() > 0) {
      String option = removeNextArg(argList);
      try {
        switch (option) {
          case "-p":
            projectId = removeNextArg(argList);
            break;
          case "-s":
            setSiteDir(removeNextArg(argList));
            break;
          case "-a":
            setSchemaSpec(removeNextArg(argList));
            schemaDefined = true;
            break;
          case "-t":
            initializeCloudIoT();
            validatePubSub(removeNextArg(argList));
            srcDefined = true;
            break;
          case "-f":
            validateFilesOutput(removeNextArg(argList));
            srcDefined = true;
            break;
          case "-r":
            validateMessageTrace(removeNextArg(argList));
            srcDefined = true;
            break;
          case "-n":
            srcDefined = true;
            break;
          case "-w":
            setMessageTraceDir(removeNextArg(argList));
            break;
          case "--":
            // All remaining arguments remain in the return list.
            return;
          default:
            throw new RuntimeException("Unknown cmdline option " + option);
        }
      } catch (MissingFormatArgumentException e) {
        throw new RuntimeException("For command line option " + option, e);
      }
    }

    if (!schemaDefined) {
      setSchemaSpec("schema");
    }
    if (!srcDefined) {
      validateReflector();
    }
  }

  MessageReadingClient getMessageReadingClient() {
    return (MessageReadingClient) client;
  }

  private void validateMessageTrace(String messageDir) {
    client = new MessageReadingClient(cloudIotConfig.registry_id, messageDir);
    dataSinks.add(client);
    reportAfterEveryMessage = true;
  }

  private String removeNextArg(List<String> argList) {
    if (argList.isEmpty()) {
      throw new MissingFormatArgumentException("Missing argument");
    }
    return argList.remove(0);
  }

  /**
   * Set the site directory to use for this validation run.
   *
   * @param siteDir site model directory
   */
  public void setSiteDir(String siteDir) {
    final File baseDir;
    if (NO_SITE.equals(siteDir)) {
      this.siteDir = null;
      baseDir = new File(".");
    } else {
      this.siteDir = siteDir;
      baseDir = new File(siteDir);
      File cloudConfig = new File(siteDir, "cloud_iot_config.json");
      cloudIotConfig = CloudIotManager.validate(ConfigUtil.readCloudIotConfig(cloudConfig),
          projectId);
      initializeExpectedDevices(siteDir);
    }

    outBaseDir = new File(baseDir, "out");
    outBaseDir.mkdirs();
    dataSinks.add(new FileDataSink(outBaseDir, VALIDATION_REPORT_DEVICE));
  }

  private void setMessageTraceDir(String writeDirArg) {
    traceDir = new File(writeDirArg);
    if (traceDir.exists()) {
      throw new RuntimeException("Trace directory already exists " + traceDir.getAbsolutePath());
    }
    traceDir.mkdirs();
  }

  private void initializeExpectedDevices(String siteDir) {
    File devicesDir = new File(siteDir, DEVICES_SUBDIR);
    if (!devicesDir.exists()) {
      System.err.println(
          "Directory not found, assuming no devices: " + devicesDir.getAbsolutePath());
      return;
    }
    try {
      for (String device : Objects.requireNonNull(devicesDir.list())) {
        ReportingDevice reportingDevice = new ReportingDevice(device);
        try {
          File deviceDir = new File(devicesDir, device);
          File metadataFile = new File(deviceDir, METADATA_JSON);
          reportingDevice.setMetadata(OBJECT_MAPPER.readValue(metadataFile, Metadata.class));
        } catch (Exception e) {
          System.err.printf("Error while loading device %s: %s%n", device, e);
          reportingDevice.addError(e);
        }
        expectedDevices.put(device, reportingDevice);
      }
      System.err.println("Loaded " + expectedDevices.size() + " expected devices");
    } catch (Exception e) {
      throw new RuntimeException(
          "While loading devices directory " + devicesDir.getAbsolutePath(), e);
    }
  }

  private void initializeCloudIoT() {
    File cloudConfig = new File(siteDir, "cloud_iot_config.json");
    try {
      this.cloudIotManager = new CloudIotManager(projectId, cloudConfig, "foobar");
    } catch (Exception e) {
      throw new RuntimeException("While initializing cloud IoT for project " + projectId, e);
    }
  }

  /**
   * Set the schema specification directory.
   *
   * @param schemaPath schema specification directory
   */
  public void setSchemaSpec(String schemaPath) {
    File schemaFile = new File(schemaPath).getAbsoluteFile();
    if (schemaFile.isFile()) {
      schemaRoot = schemaFile.getParentFile();
      schemaSpec = schemaFile.getName();
    } else if (schemaFile.isDirectory()) {
      schemaRoot = schemaFile;
      schemaSpec = null;
    } else {
      throw new RuntimeException("Schema directory/file not found: " + schemaFile);
    }
    schemaMap = getSchemaMap();
  }

  private Map<String, JsonSchema> getSchemaMap() {
    Map<String, JsonSchema> schemaMap = new TreeMap<>();
    for (File schemaFile : makeFileList(null, schemaRoot)) {
      JsonSchema schema = getSchema(schemaFile);
      String fullName = schemaFile.getName();
      String schemaName =
          schemaFile.getName().substring(0, fullName.length() - JSON_SUFFIX.length());
      schemaMap.put(schemaName, schema);
    }
    if (!schemaMap.containsKey(ENVELOPE_SCHEMA_ID)) {
      throw new RuntimeException("Missing schema for attribute validation: " + ENVELOPE_SCHEMA_ID);
    }
    return schemaMap;
  }

  private BiConsumer<Map<String, Object>, Map<String, String>> messageValidator() {
    processValidationReport();
    return (message, attributes) -> validateMessage(message, attributes);
  }

  private void validatePubSub(String instName) {
    String registryId = getRegistryId();
    client = new PubSubClient(projectId, registryId, instName);
    if (cloudIotManager.getUpdateTopic() != null) {
      // TODO: Figure out if the sink topic can/should be the same as the instName topic.
      dataSinks.add(client);
    }
  }

  private String getRegistryId() {
    String registryId = cloudIotConfig.registry_id;
    return registryId;
  }

  private void validateReflector() {
    String keyFile = new File(siteDir, GCP_REFLECT_KEY_PKCS8).getAbsolutePath();
    System.err.println("Loading reflector key file from " + keyFile);
    client = new IotReflectorClient(projectId, cloudIotConfig, keyFile);
  }

  void messageLoop() {
    if (client == null) {
      return;
    }
    sendInitializationQuery();
    System.err.println("Entering message loop on " + client.getSubscriptionId());
    BiConsumer<Map<String, Object>, Map<String, String>> validator = messageValidator();
    ScheduledFuture<?> reportSender =
        reportAfterEveryMessage ? null : executor.scheduleAtFixedRate(this::processValidationReport,
            REPORT_INTERVAL_SEC, REPORT_INTERVAL_SEC, TimeUnit.SECONDS);
    try {
      while (client.isActive()) {
        try {
          client.processMessage(validator);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    } finally {
      System.err.println("Message loop complete");
      if (reportSender != null) {
        reportSender.cancel(true);
      }
    }
  }

  private void sendInitializationQuery() {
    if (!deviceIds.isEmpty()) {
      System.err.println("Sending initialization query messages for device " + deviceIds);
    }
    for (String deviceId : deviceIds) {
      client.publish(deviceId, STATE_QUERY_TOPIC, EMPTY_MESSAGE);
    }
  }

  protected void validateMessage(MessageBundle bundle) {
    validateMessage(bundle.message, bundle.attributes);
  }

  private void validateMessage(
      Map<String, Object> message,
      Map<String, String> attributes) {

    String deviceId = attributes.get("deviceId");
    if (deviceId != null && expectedDevices.containsKey(deviceId)) {
      processedDevices.add(deviceId);
    }

    if (!shouldConsiderMessage(attributes)) {
      return;
    }

    if (traceDir != null) {
      writeMessageCapture(message, attributes);
    }

    ReportingDevice reportingDevice = validateUpdate(message, attributes);
    if (reportingDevice != null) {
      sendValidationResult(attributes, message, reportingDevice);
    }
    processValidationReport();
  }

  private void validateMessage(JsonSchema schema, Object message) {
    try {
      validateJsonNode(schema, OBJECT_MAPPER.valueToTree(message));
    } catch (Exception e) {
      throw new RuntimeException("While converting to json node: " + e.getMessage(), e);
    }
  }

  private void sanitizeMessage(String schemaName, Map<String, Object> message) {
    if (schemaName.startsWith(CONFIG_PREFIX) || schemaName.startsWith(STATE_PREFIX)) {
      message.remove(TIMESTAMP_ATTRIBUTE);
    }
  }

  private ReportingDevice validateUpdate(
      Map<String, Object> message,
      Map<String, String> attributes) {

    String deviceId = attributes.get("deviceId");
    final ReportingDevice reportingDevice = getReportingDevice(deviceId);
    try {
      String schemaName = messageSchema(attributes);
      if (!reportingDevice.markMessageType(schemaName)) {
        return null;
      }

      System.err.printf(
          "Processing device #%d/%d: %s/%s%n",
          processedDevices.size(), expectedDevices.size(), deviceId, schemaName);

      if ("true".equals(attributes.get("wasBase64"))) {
        base64Devices.add(deviceId);
      }

      sanitizeMessage(schemaName, message);
      upgradeMessage(schemaName, message);
      File errorFile = prepareDeviceOutDir(message, attributes, deviceId, schemaName);
      errorFile.delete();
      ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
      PrintStream errorOut = new PrintStream(errorStream);

      try {
        if (!schemaMap.containsKey(schemaName)) {
          throw new IllegalArgumentException(
              String.format(SCHEMA_SKIP_FORMAT, schemaName, deviceId));
        }
      } catch (Exception e) {
        System.err.println(e.getMessage());
        errorOut.println(e.getMessage());
        reportingDevice.addError(e);
      }

      try {
        validateMessage(schemaMap.get(ENVELOPE_SCHEMA_ID), attributes);
      } catch (Exception e) {
        System.err.println("Error validating attributes: " + e.getMessage());
        reportingDevice.addError(e);
      }

      if (schemaMap.containsKey(schemaName)) {
        try {
          validateMessage(schemaMap.get(schemaName), message);
        } catch (Exception e) {
          System.err.println("Error validating schema: " + e.getMessage());
          reportingDevice.addError(e);
        }
      }

      if (expectedDevices.isEmpty()) {
        // No devices configured, so don't check metadata.
      } else if (expectedDevices.containsKey(deviceId)) {
        try {
          if (CONTENT_VALIDATORS.containsKey(schemaName)) {
            Class<?> targetClass = CONTENT_VALIDATORS.get(schemaName);
            Object messageObject = OBJECT_MAPPER.convertValue(message, targetClass);
            reportingDevice.validateMessage(messageObject);
          }
        } catch (Exception e) {
          System.err.println("Error validating contents: " + e.getMessage());
          reportingDevice.addError(e);
        }
      } else {
        extraDevices.add(deviceId);
      }

      errorOut.flush();
      if (errorStream.size() > 0) {
        System.err.println("Writing errors to " + errorFile.getAbsolutePath());
        try (OutputStream output = new FileOutputStream(errorFile)) {
          output.write(errorStream.toByteArray());
        }
      }

      if (!reportingDevice.hasError()) {
        System.err.printf("Validation complete %s/%s%n", deviceId, schemaName);
      }
    } catch (Exception e) {
      reportingDevice.addError(e);
    }
    return reportingDevice;
  }


  private void sendValidationResult(Map<String, String> origAttributes, Object message,
      ReportingDevice reportingDevice) {
    try {
      ValidationEvent validationEvent = makeValidationEvent();
      sendValidationEvent(reportingDevice.getDeviceId(), validationEvent);
    } catch (Exception e) {
      throw new RuntimeException("While sending validation result", e);
    }
  }

  private void sendValidationReport(ValidationEvent report) {
    try {
      sendValidationEvent(VALIDATION_REPORT_DEVICE, report);
    } catch (Exception e) {
      throw new RuntimeException("While sending validation report", e);
    }
  }

  private void sendValidationEvent(String deviceId, ValidationEvent validationEvent) {
    try {
      String messageString = OBJECT_MAPPER.writeValueAsString(validationEvent);
      dataSinks.forEach(sink -> sink.publish(deviceId, VALIDATION_EVENT_TOPIC, messageString));
    } catch (Exception e) {
      throw new RuntimeException("While sending validation event for " + deviceId, e);
    }
  }

  private ValidationEvent makeValidationEvent() {
    ValidationEvent validationEvent = new ValidationEvent();
    validationEvent.version = UDMI_VERSION;
    validationEvent.timestamp = new Date();
    return validationEvent;
  }

  private void writeMessageCapture(Map<String, Object> message, Map<String, String> attributes) {
    String deviceId = attributes.get("deviceId");
    String type = attributes.get("subType");
    String folder = attributes.get("subFolder");
    AtomicInteger messageIndex = deviceMessageIndex.computeIfAbsent(deviceId,
        key -> new AtomicInteger());
    int index = messageIndex.incrementAndGet();
    String filename = String.format("%03d_%s_%s.json", index, type, folder);
    File deviceDir = new File(traceDir, deviceId);
    File messageFile = new File(deviceDir, filename);
    try {
      deviceDir.mkdir();
      String timestamp = (String) message.get("timestamp");
      System.out.printf("Capture %s for %s%n", timestamp, deviceId);
      OBJECT_MAPPER.writeValue(messageFile, message);
    } catch (Exception e) {
      throw new RuntimeException("While writing message file " + messageFile.getAbsolutePath());
    }
  }

  private boolean shouldConsiderMessage(Map<String, String> attributes) {
    String registryId = attributes.get(DEVICE_REGISTRY_ID_KEY);

    if (cloudIotConfig != null && !cloudIotConfig.registry_id.equals(registryId)) {
      if (ignoredRegistries.add(registryId)) {
        System.err.println("Ignoring data for not-configured registry " + registryId);
      }
      return false;
    }

    String deviceId = attributes.get("deviceId");
    if (deviceId != null && deviceId.startsWith(EXCLUDE_DEVICE_PREFIX)) {
      return false;
    }

    if (!deviceIds.isEmpty() && !deviceIds.contains(deviceId)) {
      return false;
    }

    String subType = attributes.get("subType");
    String subFolder = attributes.get("subFolder");
    String category = attributes.get("category");
    boolean isInteresting = subType == null
        || INTERESTING_TYPES.contains(subType)
        || SubFolder.UPDATE.value().equals(subFolder);
    return !CONFIG_CATEGORY.equals(category) && isInteresting;
  }

  private File prepareDeviceOutDir(
      Map<String, Object> message,
      Map<String, String> attributes,
      String deviceId,
      String schemaName)
      throws IOException {

    File deviceDir = makeDeviceDir(deviceId);

    File messageFile = new File(deviceDir, String.format(MESSAGE_FILE_FORMAT, schemaName));
    OBJECT_MAPPER.writeValue(messageFile, message);

    File attributesFile = new File(deviceDir, String.format(ATTRIBUTE_FILE_FORMAT, schemaName));
    OBJECT_MAPPER.writeValue(attributesFile, attributes);

    File errorFile = prepareErrorFile(schemaName, deviceDir);

    return errorFile;
  }

  private File prepareErrorFile(String schemaName, File deviceDir) {
    File errorFile = new File(deviceDir, String.format(ERROR_FILE_FORMAT, schemaName));
    errorFile.delete();
    return errorFile;
  }

  private File makeDeviceDir(String deviceId) {
    File deviceDir = new File(outBaseDir, String.format(DEVICE_FILE_FORMAT, deviceId));
    deviceDir.mkdirs();
    return deviceDir;
  }

  private String messageSchema(Map<String, String> attributes) {
    String subFolder = attributes.get("subFolder");
    String subType = attributes.get("subType");

    if (SubFolder.UPDATE.value().equals(subFolder)) {
      return subType;
    }

    if (Strings.isNullOrEmpty(subFolder)) {
      subFolder = UNKNOWN_FOLDER_DEFAULT;
    }

    if (Strings.isNullOrEmpty(subType)) {
      subType = UNKNOWN_TYPE_DEFAULT;
    }

    return String.format("%s_%s", subType, subFolder);
  }

  private ReportingDevice getReportingDevice(String deviceId) {
    if (expectedDevices.containsKey(deviceId)) {
      return expectedDevices.get(deviceId);
    } else {
      return new ReportingDevice(deviceId);
    }
  }

  private void processValidationReport() {
    ValidationSummary summary = new ValidationSummary();
    summary.extra_devices = new ArrayList<>(extraDevices);

    summary.missing_devices = new ArrayList<>();
    summary.correct_devices = new ArrayList<>();
    summary.error_devices = new ArrayList<>();

    Map<String, DeviceValidationEvent> devices = new TreeMap<>();
    Collection<String> summarizeDevices =
        deviceIds.isEmpty() ? expectedDevices.keySet() : deviceIds;
    for (String deviceId : summarizeDevices) {
      ReportingDevice deviceInfo = expectedDevices.get(deviceId);
      if (deviceInfo == null) {
        summary.missing_devices.add(deviceId);
      } else if (deviceInfo.hasMetadataDiff() || deviceInfo.hasError()) {
        summary.error_devices.add(deviceId);
        DeviceValidationEvent deviceValidationEvent = devices.computeIfAbsent(deviceId,
            key -> new DeviceValidationEvent());
        deviceValidationEvent.status = deviceInfo.getErrorStatus();
        deviceValidationEvent.missing_points = arrayIfNotEmpty(
            deviceInfo.getMetadataDiff().missingPoints);
      } else if (deviceInfo.hasBeenValidated()) {
        summary.correct_devices.add(deviceId);
      } else {
        summary.missing_devices.add(deviceId);
      }
    }

    ValidationEvent report = new ValidationEvent();
    report.summary = summary;
    report.devices = devices;
    sendValidationReport(report);
  }

  private <T> ArrayList<T> arrayIfNotEmpty(Set<T> items) {
    if (items == null || items.isEmpty()) {
      return null;
    }
    return new ArrayList<>(items);
  }

  private void validateFiles(String schemaSpec, String prefix, String targetSpec) {
    List<File> schemaFiles = makeFileList(null, schemaSpec);
    if (schemaFiles.size() == 0) {
      throw new RuntimeException("Cowardly refusing to validate against zero schemas");
    }
    List<File> targetFiles = makeFileList(prefix, targetSpec);
    if (targetFiles.size() == 0) {
      throw new RuntimeException("Cowardly refusing to validate against zero targets");
    }
    ExceptionMap schemaExceptions =
        new ExceptionMap(String.format(SCHEMA_VALIDATION_FORMAT, schemaFiles.size()));
    for (File schemaFile : schemaFiles) {
      try {
        JsonSchema schema = getSchema(schemaFile);
        String fileName = schemaFile.getName();
        ExceptionMap validateExceptions =
            new ExceptionMap(String.format(TARGET_VALIDATION_FORMAT, targetFiles.size(), fileName));
        for (File targetFile : targetFiles) {
          try {
            System.out.println(
                "Validating " + targetFile.getName() + " against " + schemaFile.getName());
            String schemaName = fileName.substring(0, fileName.length() - JSON_SUFFIX.length());
            validateFile(prefix, targetSpec, schemaName, schema);
          } catch (Exception e) {
            validateExceptions.put(targetFile.getName(), e);
          }
        }
        validateExceptions.throwIfNotEmpty();
      } catch (Exception e) {
        schemaExceptions.put(schemaFile.getName(), e);
      }
    }
    schemaExceptions.throwIfNotEmpty();
  }

  private void validateFilesOutput(String targetSpec) {
    try {
      String[] parts = targetSpec.split(":");
      String prefix = parts.length == 1 ? null : parts[0];
      String file = parts[parts.length - 1];
      validateFiles(schemaSpec, prefix, file);
    } catch (ExceptionMap processingException) {
      ErrorTree errorTree = ExceptionMap.format(processingException, ERROR_FORMAT_INDENT);
      errorTree.write(System.err);
      throw processingException;
    }
  }

  private JsonSchema getSchema(File schemaFile) {
    try (InputStream schemaStream = new FileInputStream(schemaFile)) {
      return JsonSchemaFactory.newBuilder()
          .setLoadingConfiguration(
              LoadingConfiguration.newBuilder()
                  .addScheme("file", new RelativeDownloader())
                  .freeze())
          .freeze()
          .getJsonSchema(OBJECT_MAPPER.readTree(schemaStream));
    } catch (Exception e) {
      throw new RuntimeException("While loading schema " + schemaFile.getAbsolutePath(), e);
    }
  }

  private List<File> makeFileList(String prefix, String spec) {
    return makeFileList(prefix, new File(spec));
  }

  private List<File> makeFileList(String prefix, File partialTarget) {
    File target = getFullPath(prefix, partialTarget);
    if (target.isFile()) {
      return ImmutableList.of(target);
    }
    boolean isDir = target.isDirectory();
    String leading = isDir ? "" : target.getName();
    File parent = isDir ? target : target.getAbsoluteFile().getParentFile();
    if (!parent.isDirectory()) {
      throw new RuntimeException("Parent directory not found " + parent.getAbsolutePath());
    }

    FilenameFilter filter = (dir, file) -> file.endsWith(JSON_SUFFIX);
    String[] fileNames = parent.list(filter);

    return Arrays.stream(fileNames)
        .map(name -> new File(parent, name))
        .collect(Collectors.toList());
  }

  private void validateFile(
      String prefix, String targetFile, String schemaName, JsonSchema schema) {
    final File targetOut = getTargetPath(prefix, targetFile.replace(".json", ".out"));
    try {
      File fullPath = getFullPath(prefix, new File(targetFile));
      Map<String, Object> message = OBJECT_MAPPER.readValue(fullPath, Map.class);
      sanitizeMessage(schemaName, message);
      JsonNode jsonNode = OBJECT_MAPPER.valueToTree(message);
      upgradeMessage(schemaName, jsonNode);
      OBJECT_MAPPER.writeValue(getTargetPath(prefix, targetFile), jsonNode);
      validateJsonNode(schema, jsonNode);
      writeExceptionOutput(targetOut, null);
    } catch (Exception e) {
      writeExceptionOutput(targetOut, e);
      throw new RuntimeException("Against input " + targetFile, e);
    }
  }

  private void writeExceptionOutput(File targetOut, Exception e) {
    try (OutputStream outputStream = new FileOutputStream(targetOut)) {
      if (e != null) {
        ExceptionMap.format(e, ERROR_FORMAT_INDENT).write(outputStream);
      }
    } catch (Exception e2) {
      throw new RuntimeException("While writing " + targetOut, e2);
    }
  }

  private File getTargetPath(String baseFile, String addedFile) {
    File prefix = new File(baseFile);
    File outBase = new File(prefix.getParentFile(), "out/tests");
    File full = new File(outBase, addedFile);
    full.getParentFile().mkdirs();
    return full;
  }

  private void upgradeMessage(String schemaName, Map<String, Object> message) {
    JsonNode jsonNode = OBJECT_MAPPER.convertValue(message, JsonNode.class);
    upgradeMessage(schemaName, jsonNode);
    Map<String, Object> objectMap = OBJECT_MAPPER.convertValue(jsonNode,
        new TypeReference<>() {
        });
    message.clear();
    message.putAll(objectMap);
  }

  private void upgradeMessage(String schemaName, JsonNode jsonNode) {
    new MessageUpgrader(schemaName, jsonNode).upgrade();
  }

  private void validateJsonNode(JsonSchema schema, JsonNode jsonNode) throws ProcessingException {
    ProcessingReport report = schema.validate(jsonNode, true);
    if (!report.isSuccess()) {
      throw ValidationException.fromProcessingReport(report);
    }
  }

  private File getFullPath(String prefix, File targetFile) {
    return prefix == null ? targetFile : new File(new File(prefix), targetFile.getPath());
  }

  static class MessageBundle {

    public Map<String, Object> message;
    public Map<String, String> attributes;
  }

  class RelativeDownloader implements URIDownloader {

    private static final String FILE_URL_PREFIX = "file:";

    @Override
    public InputStream fetch(URI source) {
      String url = source.toString();
      try {
        if (!url.startsWith(FILE_URL_PREFIX)) {
          throw new IllegalStateException("Expected path to start with " + FILE_URL_PREFIX);
        }
        String newUrl =
            FILE_URL_PREFIX + new File(schemaRoot, url.substring(FILE_URL_PREFIX.length()));
        return (InputStream) (new URL(newUrl)).getContent();
      } catch (Exception e) {
        throw new RuntimeException("While loading URL " + url, e);
      }
    }
  }
}
