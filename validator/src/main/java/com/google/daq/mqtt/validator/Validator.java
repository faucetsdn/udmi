package com.google.daq.mqtt.validator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.daq.mqtt.sequencer.SequenceBase.EMPTY_MESSAGE;
import static com.google.daq.mqtt.util.ConfigUtil.UDMI_TOOLS;
import static com.google.daq.mqtt.util.ConfigUtil.UDMI_VERSION;
import static com.google.daq.mqtt.util.ConfigUtil.readExecutionConfiguration;
import static com.google.daq.mqtt.validator.ReportingDevice.typeFolderPairKey;
import static com.google.udmi.util.Common.ERROR_KEY;
import static com.google.udmi.util.Common.EXCEPTION_KEY;
import static com.google.udmi.util.Common.GCP_REFLECT_KEY_PKCS8;
import static com.google.udmi.util.Common.MESSAGE_KEY;
import static com.google.udmi.util.Common.NO_SITE;
import static com.google.udmi.util.Common.STATE_QUERY_TOPIC;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.Common.VERSION_KEY;
import static com.google.udmi.util.Common.removeNextArg;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.JSON_SUFFIX;
import static com.google.udmi.util.JsonUtil.OBJECT_MAPPER;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.download.URIDownloader;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.bos.iot.core.proxy.NullPublisher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.ExceptionMap;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import com.google.daq.mqtt.util.FileDataSink;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.util.MessageUpgrader;
import com.google.daq.mqtt.util.PubSubClient;
import com.google.daq.mqtt.util.ValidationException;
import com.google.udmi.util.Common;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import udmi.schema.Category;
import udmi.schema.DeviceValidationEvent;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.IotAccess.IotProvider;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointsetEvent;
import udmi.schema.PointsetState;
import udmi.schema.PointsetSummary;
import udmi.schema.SetupUdmiConfig;
import udmi.schema.State;
import udmi.schema.ValidationEvent;
import udmi.schema.ValidationState;
import udmi.schema.ValidationSummary;

/**
 * Core class for running site-level validations of data streams.
 */
public class Validator {

  public static final int REQUIRED_FUNCTION_VER = 9;
  public static final String CONFIG_PREFIX = "config_";
  public static final String STATE_PREFIX = "state_";
  public static final String PROJECT_PROVIDER_PREFIX = "//";
  private static final String ERROR_FORMAT_INDENT = "  ";
  private static final String SCHEMA_VALIDATION_FORMAT = "Validating %d schemas";
  private static final String TARGET_VALIDATION_FORMAT = "Validating %d files against %s";
  private static final String DEVICE_FILE_FORMAT = "devices/%s";
  private static final String ATTRIBUTE_FILE_FORMAT = "%s.attr";
  private static final String MESSAGE_FILE_FORMAT = "%s.json";
  private static final String SCHEMA_SKIP_FORMAT = "Unknown schema subFolder '%s' for %s";
  private static final String ENVELOPE_SCHEMA_ID = "envelope";
  private static final String DEVICES_SUBDIR = "devices";
  private static final String DEVICE_REGISTRY_ID_KEY = "deviceRegistryId";
  private static final String UNKNOWN_FOLDER_DEFAULT = "unknown";
  private static final String STATE_UPDATE_SCHEMA = "state";
  private static final String EVENT_POINTSET_SCHEMA = "event_pointset";
  private static final String STATE_POINTSET_SCHEMA = "state_pointset";
  private static final String UNKNOWN_TYPE_DEFAULT = "event";
  private static final String CONFIG_CATEGORY = "config";
  private static final Set<String> INTERESTING_TYPES = ImmutableSet.of(
      SubType.EVENT.value(),
      SubType.STATE.value());
  private static final Map<String, Class<?>> CONTENT_VALIDATORS = ImmutableMap.of(
      STATE_UPDATE_SCHEMA, State.class,
      EVENT_POINTSET_SCHEMA, PointsetEvent.class,
      STATE_POINTSET_SCHEMA, PointsetState.class
  );
  private static final Set<SubType> LAST_SEEN_SUBTYPES = ImmutableSet.of(SubType.EVENT,
      SubType.STATE);
  private static final long REPORT_INTERVAL_SEC = 15;
  private static final String EXCLUDE_DEVICE_PREFIX = "_";
  private static final String VALIDATION_REPORT_DEVICE = "_validator";
  private static final String VALIDATION_EVENT_TOPIC = "validation/event";
  private static final String VALIDATION_STATE_TOPIC = "validation/state";
  private static final String POINTSET_SUBFOLDER = "pointset";
  private static final Date START_TIME = new Date();
  private final Map<String, ReportingDevice> reportingDevices = new TreeMap<>();
  private final Set<String> extraDevices = new TreeSet<>();
  private final Set<String> processedDevices = new TreeSet<>();
  private final Set<String> base64Devices = new TreeSet<>();
  private final Set<String> ignoredRegistries = new HashSet();
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  private final Map<String, AtomicInteger> deviceMessageIndex = new HashMap<>();
  private final List<MessagePublisher> dataSinks = new ArrayList<>();
  private final Set<String> targetDevices;
  private ImmutableSet<String> expectedDevices;
  private File outBaseDir;
  private File schemaRoot;
  private String schemaSpec;
  private ExecutionConfiguration config;
  private MessagePublisher client;
  private Map<String, JsonSchema> schemaMap;
  private File traceDir;
  private boolean simulatedMessages;
  private Instant mockNow = null;
  private boolean forceUpgrade;

  /**
   * Create a simplistic validator for encapsulated use.
   */
  public Validator(ExecutionConfiguration validatorConfig) {
    config = validatorConfig;
    setSchemaSpec("schema");
    client = new NullPublisher();
    targetDevices = ImmutableSet.of();
  }

  /**
   * Create validator with the given args.
   *
   * @param argList Argument list
   */
  public Validator(List<String> argList) {
    List<String> listCopy = new ArrayList<>(argList);
    parseArgs(listCopy);

    if (schemaMap == null) {
      setSchemaSpec("schema");
    }
    if (client == null) {
      validateReflector();
    }
    targetDevices = Set.copyOf(listCopy);
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

  private static void sanitizeMessageException(Map<String, Object> message) {
    if (message.get(EXCEPTION_KEY) instanceof Exception exception) {
      message.put(EXCEPTION_KEY, friendlyStackTrace(exception));
    }
  }

  private List<String> parseArgs(List<String> argList) {
    if (!argList.isEmpty() && !argList.get(0).startsWith("-")) {
      processProfile(new File(argList.remove(0)));
    } else {
      config = new ExecutionConfiguration();
    }
    while (!argList.isEmpty()) {
      String option = removeNextArg(argList);
      try {
        switch (option) {
          case "-p":
            setProjectId(removeNextArg(argList));
            break;
          case "-s":
            setSiteDir(removeNextArg(argList));
            break;
          case "-a":
            setSchemaSpec(removeNextArg(argList));
            break;
          case "-t":
            validatePubSub(removeNextArg(argList));
            break;
          case "-f":
            validateFilesOutput(removeNextArg(argList));
            break;
          case "-u":
            forceUpgrade = true;
            break;
          case "-r":
            validateMessageTrace(removeNextArg(argList));
            break;
          case "-n":
            client = new NullPublisher();
            break;
          case "-w":
            setMessageTraceDir(removeNextArg(argList));
            break;
          case "--":
            // All remaining arguments remain in the return list.
            return argList;
          default:
            throw new RuntimeException("Unknown cmdline option " + option);
        }
      } catch (MissingFormatArgumentException e) {
        throw new RuntimeException("For command line option " + option, e);
      }
    }
    return argList;
  }

  private void setProjectId(String projectId) {
    if (!projectId.startsWith(PROJECT_PROVIDER_PREFIX)) {
      config.project_id = projectId;
      return;
    }
    String[] parts = projectId.substring(PROJECT_PROVIDER_PREFIX.length()).split("/", 2);
    config.iot_provider = IotProvider.fromValue(parts[0]);
    config.project_id = parts[1];
  }

  private void validatePubSub(String pubSubCombo) {
    String[] parts = pubSubCombo.split("/");
    Preconditions.checkArgument(parts.length <= 2, "Too many parts in pubsub path " + pubSubCombo);
    String instName = parts[0];
    CloudIotManager cloudIotManager = new CloudIotManager(config.project_id,
        new File(config.site_model), null, config.registry_suffix, IotProvider.GCP_NATIVE);
    String registryId = getRegistryId();
    String updateTopic = parts.length > 1 ? parts[1] : cloudIotManager.getUpdateTopic();
    client = new PubSubClient(config.project_id, registryId, instName, updateTopic);
    if (updateTopic == null) {
      System.err.println("Not sending to update topic because PubSub update_topic not defined");
    } else {
      dataSinks.add(client);
    }
  }

  private void processProfile(File profilePath) {
    config = ConfigUtil.readExecutionConfiguration(profilePath);
    setSiteDir(config.site_model);
    if (!Strings.isNullOrEmpty(config.feed_name)) {
      validatePubSub(config.feed_name);
    }
  }

  MessageReadingClient getMessageReadingClient() {
    return (MessageReadingClient) client;
  }

  private void validateMessageTrace(String messageDir) {
    client = new MessageReadingClient(config.registry_id, messageDir);
    dataSinks.add(client);
    prepForMock();
  }

  Validator prepForMock() {
    simulatedMessages = true;
    return this;
  }

  /**
   * Set the site directory to use for this validation run.
   *
   * @param siteDir site model directory
   */
  public void setSiteDir(String siteDir) {
    config.site_model = siteDir;
    final File baseDir;
    if (NO_SITE.equals(siteDir)) {
      baseDir = new File(".");
    } else {
      baseDir = new File(siteDir);
      config = CloudIotManager.validate(resolveSiteConfig(config, siteDir), config.project_id);
      initializeExpectedDevices(siteDir);
    }

    outBaseDir = new File(baseDir, "out");
    outBaseDir.mkdirs();
    dataSinks.add(new FileDataSink(outBaseDir));
  }

  private ExecutionConfiguration resolveSiteConfig(ExecutionConfiguration config, String siteDir) {
    File cloudConfig = new File(siteDir, "cloud_iot_config.json");
    if (config == null) {
      return readExecutionConfiguration(cloudConfig);
    }
    checkArgument(siteDir.equals(config.site_model), "siteDir mismatch");
    ExecutionConfiguration siteConfig = readExecutionConfiguration(cloudConfig);
    return GeneralUtils.mergeObject(siteConfig, config);
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
    List<String> siteDevices = SiteModel.listDevices(devicesDir);
    try {
      expectedDevices = ImmutableSet.copyOf(siteDevices);
      for (String device : siteDevices) {
        ReportingDevice reportingDevice = new ReportingDevice(device);
        try {
          Metadata metadata = SiteModel.loadDeviceMetadata(siteDir, device, Validator.class);
          reportingDevice.setMetadata(metadata);
        } catch (Exception e) {
          System.err.printf("Error while loading device %s: %s%n", device, e);
          reportingDevice.addError(e, Category.VALIDATION_DEVICE_SCHEMA, "loading device");
        }
        reportingDevices.put(device, reportingDevice);
      }
      System.err.println("Loaded " + reportingDevices.size() + " expected devices");
    } catch (Exception e) {
      throw new RuntimeException(
          "While loading devices directory " + devicesDir.getAbsolutePath(), e);
    }
  }

  /**
   * Set the schema specification directory.
   *
   * @param schemaPath schema specification directory
   */
  public void setSchemaSpec(String schemaPath) {
    File schemaPart = new File(schemaPath);
    boolean rawPath = schemaPart.isAbsolute() || Strings.isNullOrEmpty(config.udmi_root);
    File schemaFile = rawPath ? schemaPart : new File(new File(config.udmi_root), schemaPath);
    if (schemaFile.isFile()) {
      schemaRoot = ofNullable(schemaFile.getParentFile()).orElse(new File("."));
      schemaSpec = schemaFile.getName();
    } else if (schemaFile.isDirectory()) {
      schemaRoot = schemaFile;
      schemaSpec = null;
    } else {
      throw new RuntimeException(
          "Schema directory/file not found: " + schemaFile.getAbsolutePath());
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

  private String getRegistryId() {
    return config.registry_id;
  }

  private void validateReflector() {
    String keyFile = new File(config.site_model, GCP_REFLECT_KEY_PKCS8).getAbsolutePath();
    System.err.println("Loading reflector key file from " + keyFile);
    config.key_file = keyFile;
    client = new IotReflectorClient(config, REQUIRED_FUNCTION_VER);
  }

  void messageLoop() {
    if (client == null || client.getSubscriptionId() == null) {
      return;
    }
    sendInitializationQuery();
    System.err.println("Running udmi tools version " + UDMI_TOOLS);
    System.err.println("Entering message loop on " + client.getSubscriptionId());
    processValidationReport();
    ScheduledFuture<?> reportSender =
        simulatedMessages ? null : executor.scheduleAtFixedRate(this::processValidationReport,
            REPORT_INTERVAL_SEC, REPORT_INTERVAL_SEC, TimeUnit.SECONDS);
    try {
      while (client.isActive()) {
        try {
          validateMessage(client.takeNextMessage(false));
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
    for (String deviceId : targetDevices) {
      System.err.println("Sending initialization query messages for device " + deviceId);
      client.publish(deviceId, STATE_QUERY_TOPIC, EMPTY_MESSAGE);
    }
  }

  protected synchronized void validateMessage(MessageBundle nullable) {
    ifNotNullThen(nullable, bundle -> validateMessage(bundle.message, bundle.attributes));
  }

  private void validateMessage(
      Map<String, Object> message,
      Map<String, String> attributes) {

    String deviceId = attributes.get("deviceId");
    if (deviceId != null && reportingDevices.containsKey(deviceId)) {
      processedDevices.add(deviceId);
    }

    if (!shouldConsiderMessage(attributes)) {
      return;
    }

    if (traceDir != null) {
      writeMessageCapture(message, attributes);
    }

    if (simulatedMessages) {
      mockNow = Instant.parse((String) message.get(TIMESTAMP_KEY));
      ReportingDevice.setMockNow(mockNow);
    }
    ReportingDevice reportingDevice = validateMessageCore(message, attributes);
    if (reportingDevice != null) {
      Date now = simulatedMessages ? Date.from(mockNow) : new Date();
      sendValidationResult(attributes, reportingDevice, now);
    }
    if (simulatedMessages) {
      processValidationReport();
    }
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
      message.remove(VERSION_KEY);
      message.remove(TIMESTAMP_KEY);
    }
  }

  private ReportingDevice validateMessageCore(
      Map<String, Object> message,
      Map<String, String> attributes) {

    String deviceId = attributes.get("deviceId");
    ReportingDevice device = reportingDevices.computeIfAbsent(deviceId, ReportingDevice::new);

    try {
      String schemaName = messageSchema(attributes);
      if (!device.markMessageType(schemaName, getNow())) {
        return null;
      }

      System.err.printf(
          "Processing device #%d/%d: %s/%s%n",
          processedDevices.size(), reportingDevices.size(), deviceId, schemaName);

      if ("true".equals(attributes.get("wasBase64"))) {
        base64Devices.add(deviceId);
      }

      writeDeviceOutDir(message, attributes, deviceId, schemaName);
      validateDeviceMessage(device, message, attributes);

      if (!device.hasErrors()) {
        System.err.printf("Validation clean %s/%s%n", deviceId, schemaName);
      }
    } catch (Exception e) {
      System.err.printf("Error processing %s: %s%n", deviceId, friendlyStackTrace(e));
      device.addError(e, attributes, Category.VALIDATION_DEVICE_RECEIVE);
    }
    return device;
  }

  /**
   * Validate a device message against the core schema.
   */
  public void validateDeviceMessage(ReportingDevice device, Map<String, Object> message,
      Map<String, String> attributes) {
    String deviceId = attributes.get("deviceId");
    device.clearMessageEntries();
    String schemaName = messageSchema(attributes);

    if (message.get(EXCEPTION_KEY) instanceof Exception exception) {
      System.err.println(
          "Pipeline exception " + deviceId + ": " + Common.getExceptionMessage(exception));
      device.addError(exception, attributes, Category.VALIDATION_DEVICE_RECEIVE);
      return;
    }

    if (message.containsKey(ERROR_KEY)) {
      String error = (String) message.get(ERROR_KEY);
      System.err.println("Pipeline error " + deviceId + ": " + error);
      IllegalArgumentException exception = new IllegalArgumentException(
          "Error in message pipeline: " + error);
      device.addError(exception, attributes, Category.VALIDATION_DEVICE_RECEIVE);
      return;
    }

    upgradeMessage(schemaName, message);
    sanitizeMessage(schemaName, message);

    String timeString = (String) message.get(TIMESTAMP_KEY);
    String subTypeRaw = ofNullable(attributes.get(SUBTYPE_PROPERTY_KEY))
        .orElse(UNKNOWN_TYPE_DEFAULT);
    if (timeString != null && LAST_SEEN_SUBTYPES.contains(SubType.fromValue(subTypeRaw))) {
      device.updateLastSeen(Date.from(Instant.parse(timeString)));
    }

    try {
      if (!schemaMap.containsKey(schemaName)) {
        throw new IllegalArgumentException(
            String.format(SCHEMA_SKIP_FORMAT, schemaName, deviceId));
      }
    } catch (Exception e) {
      System.err.println("Missing schema entry " + schemaName);
      device.addError(e, attributes, Category.VALIDATION_DEVICE_RECEIVE);
    }

    try {
      validateMessage(schemaMap.get(ENVELOPE_SCHEMA_ID), attributes);
    } catch (Exception e) {
      System.err.println("Error validating attributes: " + e);
      device.addError(e, attributes, Category.VALIDATION_DEVICE_RECEIVE);
    }

    if (schemaMap.containsKey(schemaName)) {
      try {
        validateMessage(schemaMap.get(schemaName), message);
      } catch (Exception e) {
        System.err.printf("Error validating schema %s: %s%n", schemaName, e.getMessage());
        device.addError(e, attributes, Category.VALIDATION_DEVICE_SCHEMA);
      }
    }

    if (expectedDevices == null || expectedDevices.isEmpty()) {
      // No devices configured, so don't consider check metadata or consider extra.
    } else if (expectedDevices.contains(deviceId)) {
      try {
        ifNotNullThen(CONTENT_VALIDATORS.get(schemaName), targetClass -> {
          Object messageObject = OBJECT_MAPPER.convertValue(message, targetClass);
          device.validateMessageType(messageObject, JsonUtil.getDate(timeString), attributes);
        });
      } catch (Exception e) {
        System.err.println("Error validating contents: " + e.getMessage());
        device.addError(e, attributes, Category.VALIDATION_DEVICE_CONTENT);
      }
    } else {
      extraDevices.add(deviceId);
    }
  }

  private void sendValidationResult(Map<String, String> origAttributes,
      ReportingDevice reportingDevice, Date now) {
    try {
      ValidationEvent event = new ValidationEvent();
      event.version = UDMI_VERSION;
      event.timestamp = new Date();
      String subFolder = origAttributes.get(SUBFOLDER_PROPERTY_KEY);
      event.sub_folder = subFolder;
      event.sub_type = ofNullable(origAttributes.get(SUBTYPE_PROPERTY_KEY))
          .orElse(UNKNOWN_TYPE_DEFAULT);
      event.status = ReportingDevice.getSummaryEntry(reportingDevice.getMessageEntries());
      String prefix = String.format("%s:",
          typeFolderPairKey(origAttributes.get(SUBTYPE_PROPERTY_KEY), subFolder));
      event.errors = reportingDevice.getErrors(now, prefix);
      if (POINTSET_SUBFOLDER.equals(subFolder)) {
        PointsetSummary pointsSummary = new PointsetSummary();
        pointsSummary.missing = arrayIfNotNull(reportingDevice.getMissingPoints());
        pointsSummary.extra = arrayIfNotNull(reportingDevice.getExtraPoints());
        event.pointset = pointsSummary;
      }
      sendValidationMessage(reportingDevice.getDeviceId(), event, VALIDATION_EVENT_TOPIC);
    } catch (Exception e) {
      throw new RuntimeException("While sending validation result", e);
    }
  }

  private void sendValidationReport(ValidationState report) {
    try {
      sendValidationMessage(VALIDATION_REPORT_DEVICE, report, VALIDATION_STATE_TOPIC);
    } catch (Exception e) {
      throw new RuntimeException("While sending validation report", e);
    }
  }

  private void sendValidationMessage(String deviceId, Object message, String topic) {
    try {
      String messageString = OBJECT_MAPPER.writeValueAsString(message);
      dataSinks.forEach(sink -> sink.publish(deviceId, topic, messageString));
    } catch (Exception e) {
      throw new RuntimeException("While sending validation event for " + deviceId, e);
    }
  }

  private void writeMessageCapture(Map<String, Object> message, Map<String, String> attributes) {
    String deviceId = attributes.get("deviceId");
    String type = attributes.getOrDefault(SUBTYPE_PROPERTY_KEY, UNKNOWN_TYPE_DEFAULT);
    String folder = attributes.get(SUBFOLDER_PROPERTY_KEY);
    AtomicInteger messageIndex = deviceMessageIndex.computeIfAbsent(deviceId,
        key -> new AtomicInteger());
    int index = messageIndex.incrementAndGet();
    String filename = String.format("%03d_%s.json", index, typeFolderPairKey(type, folder));
    File deviceDir = new File(traceDir, deviceId);
    File messageFile = new File(deviceDir, filename);
    try {
      deviceDir.mkdir();
      String timestamp = (String) message.get(TIMESTAMP_KEY);
      System.out.printf("Capture %s for %s%n", timestamp, deviceId);
      OBJECT_MAPPER.writeValue(messageFile, message);
    } catch (Exception e) {
      throw new RuntimeException("While writing message file " + messageFile.getAbsolutePath());
    }
  }

  private boolean shouldConsiderMessage(Map<String, String> attributes) {
    String registryId = attributes.get(DEVICE_REGISTRY_ID_KEY);

    if (config != null && !config.registry_id.equals(registryId)) {
      if (ignoredRegistries.add(registryId)) {
        System.err.println("Ignoring data for not-configured registry " + registryId);
      }
      return false;
    }

    String deviceId = attributes.get("deviceId");
    if (deviceId != null && deviceId.startsWith(EXCLUDE_DEVICE_PREFIX)) {
      return false;
    }

    if (!targetDevices.isEmpty() && !targetDevices.contains(deviceId)) {
      return false;
    }

    String subType = attributes.get(SUBTYPE_PROPERTY_KEY);
    String subFolder = attributes.get(SUBFOLDER_PROPERTY_KEY);
    String category = attributes.get("category");
    boolean isInteresting = subType == null
        || INTERESTING_TYPES.contains(subType)
        || SubFolder.UPDATE.value().equals(subFolder);
    return !CONFIG_CATEGORY.equals(category) && isInteresting;
  }

  private void writeDeviceOutDir(
      Map<String, Object> message,
      Map<String, String> attributes,
      String deviceId,
      String schemaName)
      throws IOException {

    File deviceDir = makeDeviceDir(deviceId);

    File messageFile = new File(deviceDir, String.format(MESSAGE_FILE_FORMAT, schemaName));

    // OBJECT_MAPPER can't handle an Exception class object, so do a swap-and-restore.
    Exception saved = (Exception) message.get(EXCEPTION_KEY);
    message.put(EXCEPTION_KEY, ifNotNullGet(saved, GeneralUtils::friendlyStackTrace));
    OBJECT_MAPPER.writeValue(messageFile, message);
    message.put(EXCEPTION_KEY, saved);

    File attributesFile = new File(deviceDir, String.format(ATTRIBUTE_FILE_FORMAT, schemaName));
    OBJECT_MAPPER.writeValue(attributesFile, attributes);
  }

  private File makeDeviceDir(String deviceId) {
    File deviceDir = new File(outBaseDir, String.format(DEVICE_FILE_FORMAT, deviceId));
    deviceDir.mkdirs();
    return deviceDir;
  }

  private String messageSchema(Map<String, String> attributes) {
    String subFolder = attributes.get(SUBFOLDER_PROPERTY_KEY);
    String subType = attributes.get(SUBTYPE_PROPERTY_KEY);

    if (SubFolder.UPDATE.value().equals(subFolder)) {
      return subType;
    }

    if (Strings.isNullOrEmpty(subFolder)) {
      subFolder = UNKNOWN_FOLDER_DEFAULT;
    }

    if (Strings.isNullOrEmpty(subType)) {
      subType = UNKNOWN_TYPE_DEFAULT;
    }

    return typeFolderPairKey(subType, subFolder);
  }

  private synchronized void processValidationReport() {
    try {
      processValidationReportRaw();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void processValidationReportRaw() {
    ValidationSummary summary = new ValidationSummary();
    summary.extra_devices = new ArrayList<>(extraDevices);

    summary.correct_devices = new ArrayList<>();
    summary.error_devices = new ArrayList<>();

    Map<String, DeviceValidationEvent> devices = new TreeMap<>();
    Collection<String> targets = targetDevices.isEmpty() ? expectedDevices : targetDevices;
    for (String deviceId : reportingDevices.keySet()) {
      ReportingDevice deviceInfo = reportingDevices.get(deviceId);
      deviceInfo.expireEntries(getNow());
      boolean expected = targets.contains(deviceId);
      if (deviceInfo.hasErrors()) {
        DeviceValidationEvent event = getValidationEvent(devices, deviceInfo);
        event.status = ReportingDevice.getSummaryEntry(deviceInfo.getErrors(null, null));
        if (expected) {
          summary.error_devices.add(deviceId);
        } else {
          event.status.category = Category.VALIDATION_DEVICE_EXTRA;
          event.status.level = Level.WARNING.value();
        }
      } else if (deviceInfo.seenRecently(getNow())) {
        DeviceValidationEvent event = getValidationEvent(devices, deviceInfo);
        event.status = ReportingDevice.getSummaryEntry(deviceInfo.getErrors(null, null));
        if (expected) {
          summary.correct_devices.add(deviceId);
        } else {
          event.status.category = Category.VALIDATION_DEVICE_EXTRA;
          event.status.level = Level.WARNING.value();
        }
      }
    }

    summary.missing_devices = new ArrayList(targets);
    summary.missing_devices.removeAll(summary.error_devices);
    summary.missing_devices.removeAll(summary.correct_devices);

    sendValidationReport(makeValidationReport(summary, devices));
  }

  private DeviceValidationEvent getValidationEvent(Map<String, DeviceValidationEvent> devices,
      ReportingDevice deviceInfo) {
    DeviceValidationEvent deviceValidationEvent = devices.computeIfAbsent(deviceInfo.getDeviceId(),
        key -> new DeviceValidationEvent());
    deviceValidationEvent.last_seen = deviceInfo.getLastSeen();
    return deviceValidationEvent;
  }

  private Instant getNow() {
    return mockNow == null ? Instant.now() : mockNow;
  }

  private ValidationState makeValidationReport(ValidationSummary summary,
      Map<String, DeviceValidationEvent> devices) {
    ValidationState report = new ValidationState();
    report.version = UDMI_VERSION;
    report.timestamp = new Date();
    report.summary = summary;
    report.devices = devices;
    report.cloud_version = getUdmiVersion();
    report.udmi_version = Common.getUdmiVersion();
    report.start_time = START_TIME;
    return report;
  }

  private SetupUdmiConfig getUdmiVersion() {
    return client.getVersionInformation();
  }

  private <T> ArrayList<T> arrayIfNotNull(Set<T> items) {
    return items == null ? null : new ArrayList<>(items);
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
            validateFile(prefix, targetFile.getPath(), schemaName, schema);
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
      client = new NullPublisher();
    } catch (ExceptionMap processingException) {
      ErrorTree errorTree = ExceptionMap.format(processingException, ERROR_FORMAT_INDENT);
      errorTree.write(System.err);
      throw processingException;
    }
  }

  private JsonSchema getSchema(File schemaFile) {
    try (InputStream schemaStream = Files.newInputStream(schemaFile.toPath())) {
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
    final File targetOut = getOutputPath(prefix, targetFile.replace(".json", ".out"));
    File outputFile = getOutputPath(prefix, targetFile);
    File inputFile = new File(targetFile);
    try (OutputStream outputStream = Files.newOutputStream(outputFile.toPath())) {
      copyFileHeader(inputFile, outputStream);
      Map<String, Object> message = JsonUtil.loadMap(inputFile);
      sanitizeMessage(schemaName, message);
      JsonNode jsonNode = OBJECT_MAPPER.valueToTree(message);
      if (upgradeMessage(schemaName, jsonNode)) {
        OBJECT_MAPPER.writeValue(outputStream, jsonNode);
      } else {
        // If the message was not upgraded, then copy over unmolested to preserve formatting.
        outputStream.close();
        FileUtils.copyFile(inputFile, outputFile);
      }
      validateJsonNode(schema, jsonNode);
      writeExceptionOutput(targetOut, null);
    } catch (Exception e) {
      writeExceptionOutput(targetOut, e);
      throw new RuntimeException("Generating output " + targetOut.getAbsolutePath(), e);
    }
  }

  private void copyFileHeader(File inputFile, OutputStream outputFile) {
    try (Scanner scanner = new Scanner(inputFile)) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        if (!line.trim().startsWith("//")) {
          break;
        }
        outputFile.write((line + "\n").getBytes());
      }
    } catch (Exception e) {
      throw new RuntimeException("While copying header from " + inputFile.getAbsolutePath(), e);
    }
  }

  private void writeExceptionOutput(File targetOut, Exception e) {
    try (OutputStream outputStream = Files.newOutputStream(targetOut.toPath())) {
      if (e != null) {
        ExceptionMap.format(e, ERROR_FORMAT_INDENT).write(outputStream);
      }
    } catch (Exception e2) {
      throw new RuntimeException("While writing " + targetOut, e2);
    }
  }

  private File getOutputPath(String baseFile, String addedFile) {
    File prefix = new File(baseFile);
    File rootDir = prefix.getParentFile().getParentFile();
    File outBase = new File(rootDir, "out/tests");
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

  private boolean upgradeMessage(String schemaName, JsonNode jsonNode) {
    return new MessageUpgrader(schemaName, jsonNode).upgrade(forceUpgrade);
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

  /**
   * Simple bundle of things associated with a validation message.
   */
  public static class MessageBundle {

    public Map<String, Object> message;
    public Map<String, String> attributes;
    public String timestamp;
  }

  /**
   * Container for validation errors of a message.
   */
  public static class ErrorContainer extends TreeMap<String, Object> {

    /**
     * Create a new instance.
     *
     * @param exception base exception for error
     * @param message   message string that caused the error
     * @param timestamp timestamp of generating message
     */
    public ErrorContainer(Exception exception, String message, String timestamp) {
      put(EXCEPTION_KEY, exception);
      put(MESSAGE_KEY, message);
      put(TIMESTAMP_KEY, timestamp);
    }
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
