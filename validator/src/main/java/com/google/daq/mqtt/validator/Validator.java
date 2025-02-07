package com.google.daq.mqtt.validator;

import static com.google.api.client.util.Preconditions.checkState;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.daq.mqtt.registrar.Registrar.BASE_DIR;
import static com.google.daq.mqtt.sequencer.SequenceBase.EMPTY_MESSAGE;
import static com.google.daq.mqtt.util.ConfigUtil.UDMI_ROOT;
import static com.google.daq.mqtt.util.ConfigUtil.UDMI_TOOLS;
import static com.google.daq.mqtt.util.ConfigUtil.UDMI_VERSION;
import static com.google.daq.mqtt.util.ConfigUtil.readExeConfig;
import static com.google.daq.mqtt.util.PubSubClient.getFeedInfo;
import static com.google.daq.mqtt.validator.ReportingDevice.typeFolderPairKey;
import static com.google.udmi.util.Common.ERROR_KEY;
import static com.google.udmi.util.Common.EXCEPTION_KEY;
import static com.google.udmi.util.Common.EXIT_CODE_ERROR;
import static com.google.udmi.util.Common.GCP_REFLECT_KEY_PKCS8;
import static com.google.udmi.util.Common.MESSAGE_KEY;
import static com.google.udmi.util.Common.NO_SITE;
import static com.google.udmi.util.Common.PUBLISH_TIME_KEY;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static com.google.udmi.util.Common.TIMESTAMP_KEY;
import static com.google.udmi.util.Common.UPDATE_QUERY_TOPIC;
import static com.google.udmi.util.Common.UPGRADED_FROM;
import static com.google.udmi.util.Common.getExceptionMessage;
import static com.google.udmi.util.Common.getNamespacePrefix;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotNullThrow;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.JsonUtil.JSON_SUFFIX;
import static com.google.udmi.util.JsonUtil.OBJECT_MAPPER;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.mapCast;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static udmi.schema.IotAccess.IotProvider.PUBSUB;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.download.URIDownloader;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.bos.iot.core.proxy.IotReflectorClient;
import com.google.bos.iot.core.proxy.MqttPublisher;
import com.google.bos.iot.core.proxy.NullPublisher;
import com.google.cloud.Tuple;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ErrorMap;
import com.google.daq.mqtt.util.ErrorMap.ErrorMapException;
import com.google.daq.mqtt.util.ExceptionMap;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import com.google.daq.mqtt.util.FileDataSink;
import com.google.daq.mqtt.util.ImpulseRunningAverage;
import com.google.daq.mqtt.util.MessagePublisher;
import com.google.daq.mqtt.util.MessagePublisher.QuerySpeed;
import com.google.daq.mqtt.util.PubSubClient;
import com.google.daq.mqtt.util.ValidationException;
import com.google.udmi.util.CommandLineOption;
import com.google.udmi.util.CommandLineProcessor;
import com.google.udmi.util.Common;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.MessageUpgrader;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.io.FileUtils;
import org.slf4j.impl.SimpleLogger;
import udmi.schema.Category;
import udmi.schema.DeviceValidationEvents;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.IotAccess.IotProvider;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.PointsetSummary;
import udmi.schema.SetupUdmiConfig;
import udmi.schema.UdmiConfig;
import udmi.schema.ValidationEvents;
import udmi.schema.ValidationState;
import udmi.schema.ValidationSummary;

/**
 * Core class for running site-level validations of data streams.
 */
public class Validator {

  public static final int TOOLS_FUNCTIONS_VERSION = 17;
  public static final String PROJECT_PROVIDER_PREFIX = "//";
  public static final String TIMESTAMP_ZULU_SUFFIX = "Z";
  public static final String TIMESTAMP_UTC_SUFFIX_1 = "+00:00";
  public static final String TIMESTAMP_UTC_SUFFIX_2 = "+0000";
  public static final String ATTRIBUTE_FILE_FORMAT = "%s.attr";
  public static final String MESSAGE_FILE_FORMAT = "%s.json";
  public static final String VIOLATIONS_FILE_FORMAT = "%s.bad";
  public static final String ORIG_FILE_FORMAT = "%s.orig";
  private static final String SCHEMA_VALIDATION_FORMAT = "Validating %d schemas";
  private static final String TARGET_VALIDATION_FORMAT = "Validating %d files against %s";
  private static final String DEVICE_FILE_FORMAT = "devices/%s";
  private static final String SCHEMA_SKIP_FORMAT = "Unknown schema subFolder '%s' for %s";
  private static final String ENVELOPE_SCHEMA_ID = "envelope";
  private static final String DEVICE_REGISTRY_ID_KEY = "deviceRegistryId";
  private static final String UNKNOWN_FOLDER_DEFAULT = "unknown";
  private static final String UNKNOWN_TYPE_DEFAULT = "events";
  private static final String CONFIG_CATEGORY = "config";
  private static final Set<String> INTERESTING_TYPES = ImmutableSet.of(
      SubType.EVENTS.value(),
      SubType.STATE.value());
  private static final Set<String> IGNORE_FOLDERS = ImmutableSet.of(
      SubFolder.ERROR.value());
  private static final Set<SubType> LAST_SEEN_SUBTYPES = ImmutableSet.of(
      SubType.EVENTS,
      SubType.STATE);

  @SuppressWarnings("checkstyle:linelength")
  private static final List<String> IGNORE_LIST = ImmutableList.of(
      "instance type \\(string\\) does not match any allowed primitive type \\(allowed: \\[.*\"number\"\\]\\)");
  private static final List<Pattern> IGNORE_PATTERNS = IGNORE_LIST.stream().map(Pattern::compile)
      .toList();

  private static final long DEFAULT_INTERVAL_SEC = 60;
  private static final long REPORTS_PER_SEC = 10;
  private static final String VALIDATION_SITE_REPORT_DEVICE_ID = null;
  private static final String VALIDATION_EVENT_TOPIC = "validation/events";
  private static final String VALIDATION_STATE_TOPIC = "validation/state";
  private static final String POINTSET_SUBFOLDER = "pointset";
  private static final Date START_TIME = new Date();
  private static final int TIMESTAMP_JITTER_SEC = 60;
  private static final String UDMI_CONFIG_JSON_FILE = "udmi_config.json";
  private static final String TOOL_NAME = "validator";
  private static final String FAUX_DEVICE_ID = "TEST-97";
  private static final String VALIDATOR_TOOL_NAME = "validator";
  private static final String REGISTRY_DEVICE_DEFAULT = "_regsitry";
  private static final String SCHEMA_NAME_KEY = "ignore_envelope";
  private long reportingDelaySec = DEFAULT_INTERVAL_SEC;
  private final CommandLineProcessor commandLineProcessor = new CommandLineProcessor(this);
  private final Map<String, ReportingDevice> reportingDevices = new TreeMap<>();
  private final Set<String> extraDevices = new TreeSet<>();
  private final Set<String> processedDevices = new TreeSet<>();
  private final Set<String> base64Devices = new TreeSet<>();
  private final Set<String> ignoredRegistries = new HashSet<>();
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  private final Map<String, AtomicInteger> deviceMessageIndex = new HashMap<>();
  private final List<MessagePublisher> dataSinks = new ArrayList<>();
  private final Set<String> summaryDevices = new HashSet<>();
  private final ImpulseRunningAverage validationStats = new ImpulseRunningAverage(
      "Message validate");
  private Set<String> targetDevices;
  private final LoggingHandler outputLogger;
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
  private SiteModel siteModel;
  private boolean validateCurrent;

  static {
    System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "info");
    System.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "false");
    System.setProperty(SimpleLogger.SHOW_SHORT_LOG_NAME_KEY, "true");
  }

  /**
   * Create a simplistic validator for encapsulated use.
   */
  public Validator(ExecutionConfiguration validatorConfig, BiConsumer<Level, String> logger) {
    outputLogger = new LoggingHandler(logger);
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
    outputLogger = new LoggingHandler();
    List<String> listCopy = new ArrayList<>(argList);
    parseArgs(listCopy);
    if (client == null) {
      validateReflector();
    }
    targetDevices = Set.copyOf(listCopy);
    siteModel = ifNotNullGet(config.site_model, SiteModel::new);
    ifNotNullThen(siteModel, this::initializeExpectedDevices);
  }

  public Validator() {
    outputLogger = new LoggingHandler();
  }

  Validator processArgs(List<String> argListRaw) {
    List<String> argList = new ArrayList<>(argListRaw);
    try {
      siteModel = new SiteModel(TOOL_NAME, argList);
    } catch (IllegalArgumentException e) {
      commandLineProcessor.showUsage(e.getMessage());
    }
    processProfile(siteModel.getExecutionConfiguration());
    targetDevices = Set.copyOf(postProcessArgs(argList));
    initializeExpectedDevices();
    return this;
  }

  void execute() {
    if (!Strings.isNullOrEmpty(config.feed_name)) {
      validatePubSub(config.feed_name, true);
    }
    if (config.iot_provider == PUBSUB) {
      Tuple<String, String> tuple = getFeedInfo(config);
      validatePubSub(format("%s/%s", tuple.x(), tuple.y()), false);
    }
    if (client == null) {
      validateReflector();
    }
    checkState(client != null, "no validator client specified");
    messageLoop();
  }

  /**
   * Let's go.
   *
   * @param args Arguments for program execution
   */
  public static void main(String[] args) {
    ArrayList<String> argList = new ArrayList<>(List.of(args));
    try {
      new Validator().processArgs(argList).execute();
    } catch (Exception e) {
      System.err.println("Exception in main: " + friendlyStackTrace(e));
      e.printStackTrace();
      System.exit(EXIT_CODE_ERROR);
    }

    // Force exist because PubSub Subscriber in PubSubReflector does not shut down properly.
    safeSleep(2000);
    System.exit(0);
  }

  private static void sanitizeMessageException(Map<String, Object> message) {
    if (message.get(EXCEPTION_KEY) instanceof Exception exception) {
      message.put(EXCEPTION_KEY, friendlyStackTrace(exception));
    }
  }

  /**
   * From an external processing report.
   *
   * @param report Report to convert
   * @return Converted exception.
   */
  public static ValidationException fromProcessingReport(ProcessingReport report) {
    checkArgument(!report.isSuccess(), "Report must not be successful");
    ImmutableList<ValidationException> causingExceptions =
        StreamSupport.stream(report.spliterator(), false)
            .filter(Validator::errorOrWorse)
            .filter(Validator::notOnIgnoreList)
            .map(Validator::convertMessage).collect(toImmutableList());
    return causingExceptions.isEmpty() ? null : new ValidationException(
        format("%d schema violations found", causingExceptions.size()), causingExceptions);
  }

  private static boolean notOnIgnoreList(ProcessingMessage processingMessage) {
    return IGNORE_PATTERNS.stream()
        .noneMatch(p -> p.matcher(processingMessage.getMessage()).matches());
  }

  private static boolean errorOrWorse(ProcessingMessage processingMessage) {
    return processingMessage.getLogLevel().compareTo(LogLevel.ERROR) >= 0;
  }

  private static ValidationException convertMessage(ProcessingMessage processingMessage) {
    String pointer = processingMessage.asJson().get("instance").get("pointer").asText();
    String prefix =
        com.google.api.client.util.Strings.isNullOrEmpty(pointer) ? "" : (pointer + ": ");
    return new ValidationException(prefix + processingMessage.getMessage());
  }

  private List<String> parseArgs(List<String> argList) {
    if (!argList.isEmpty() && !argList.get(0).startsWith("-")) {
      processProfile(new File(argList.remove(0)));
    } else {
      config = new ExecutionConfiguration();
    }
    return postProcessArgs(argList);
  }

  private List<String> postProcessArgs(List<String> argList) {
    try {
      List<String> remainingArgs = commandLineProcessor.processArgs(argList);
      return ofNullable(remainingArgs).orElse(ImmutableList.of());
    } finally {
      if (schemaMap == null) {
        setSchemaSpec(new File(UDMI_ROOT, "schema").getAbsolutePath());
      }
    }
  }

  @CommandLineOption(short_form = "-n", description = "Use null client")
  private void setNullClient() {
    client = new NullPublisher();
  }

  @CommandLineOption(short_form = "-u", description = "Set force message upgrade")
  private void setForceUpgrade() {
    forceUpgrade = true;
  }

  @CommandLineOption(short_form = "-z", description = "Turn on system profiling")
  private void setProfileMode() {
    IotReflectorClient.reportStatistics = true;
    MqttPublisher.reportStatistics = true;
  }

  @CommandLineOption(short_form = "-d", arg_name = "delay", description = "Report delay (sec)")
  private void setReportDelay(String arg) {
    reportingDelaySec = Integer.parseInt(arg);
  }

  @CommandLineOption(short_form = "-c", description = "Validate current device messages")
  private void setValidateCurrent() {
    validateCurrent = true;
  }

  @CommandLineOption(short_form = "-p", arg_name = "project_id", description = "Set project id")
  private void setProjectId(String projectId) {
    if (!projectId.startsWith(PROJECT_PROVIDER_PREFIX)) {
      config.project_id = projectId;
      return;
    }
    String[] parts = projectId.substring(PROJECT_PROVIDER_PREFIX.length()).split("/", 2);
    config.iot_provider = IotProvider.fromValue(parts[0]);
    config.project_id = parts[1];
  }

  @CommandLineOption(short_form = "-t", arg_name = "target",
      description = "Validate pub sub target")
  private void validatePubSub(String pubSubCombo) {
    validatePubSub(pubSubCombo, true);
  }

  private void validatePubSub(String pubSubCombo, boolean reflect) {
    String[] parts = pubSubCombo.split("/");
    Preconditions.checkArgument(parts.length <= 2, "Too many parts in pubsub path " + pubSubCombo);
    String subscriptionId = parts[0];
    CloudIotManager cloudIotManager = new CloudIotManager(config.project_id,
        new File(config.site_model), null, config.registry_suffix, PUBSUB, VALIDATOR_TOOL_NAME);
    String registryId = getRegistryId();
    String updateTopic = parts.length > 1 ? parts[1] : cloudIotManager.getUpdateTopic();
    client = new PubSubClient(config.project_id, registryId, subscriptionId, updateTopic, reflect);
    if (updateTopic == null) {
      outputLogger.warn("Not sending to update topic because PubSub update_topic not defined");
    } else {
      dataSinks.add(client);
    }
  }

  private void processProfile(File profilePath) {
    ExecutionConfiguration exeConfig = readExeConfig(profilePath);
    String siteModel = ofNullable(exeConfig.site_model).orElse(BASE_DIR.getName());
    File model = new File(siteModel);
    File adjustedPath = model.isAbsolute() ? model :
        new File(profilePath.getParentFile(), siteModel);
    exeConfig.site_model = adjustedPath.getAbsolutePath();
    processProfile(exeConfig);
  }

  private void processProfile(ExecutionConfiguration exeConfig) {
    config = exeConfig;
    setSiteDir(exeConfig.site_model);
  }

  MessageReadingClient getMessageReadingClient() {
    return (MessageReadingClient) client;
  }

  @CommandLineOption(short_form = "-r", arg_name = "message_dir",
      description = "Validate message trace")
  private void validateMessageTrace(String messageDir) {
    client = new MessageReadingClient(getRegistryId(), messageDir);
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
  @CommandLineOption(short_form = "-s", arg_name = "site_dir", description = "Set site directory")
  private void setSiteDir(String siteDir) {
    config.site_model = siteDir;
    final File baseDir;
    if (NO_SITE.equals(siteDir)) {
      baseDir = new File(".");
    } else {
      baseDir = new File(siteDir);
      config = CloudIotManager.validate(resolveSiteConfig(config, siteDir), config.project_id);
    }

    outBaseDir = new File(baseDir, "out");
    outBaseDir.mkdirs();
    dataSinks.add(new FileDataSink(outBaseDir));
  }

  private ExecutionConfiguration resolveSiteConfig(ExecutionConfiguration config, String siteDir) {
    File cloudConfig = new File(siteDir, "cloud_iot_config.json");
    if (config == null) {
      return readExeConfig(cloudConfig);
    }
    checkArgument(siteDir.equals(config.site_model), "siteDir mismatch");
    ExecutionConfiguration siteConfig = readExeConfig(cloudConfig);
    return GeneralUtils.mergeObject(siteConfig, config);
  }

  @CommandLineOption(short_form = "-w", arg_name = "trace_dir", description = "Write trace output")
  private void setMessageTraceDir(String writeDirArg) {
    traceDir = new File(writeDirArg);
    outputLogger.info("Tracing message capture to " + traceDir.getAbsolutePath());
    if (traceDir.exists()) {
      throw new RuntimeException("Trace directory already exists " + traceDir.getAbsolutePath());
    }
    traceDir.mkdirs();
  }

  private void initializeExpectedDevices() {
    Set<String> siteDevices = siteModel.getDeviceIds();
    try {
      expectedDevices = ImmutableSet.copyOf(siteDevices);
      for (String device : siteDevices) {
        ReportingDevice reportingDevice = newReportingDevice(device);
        try {
          Metadata metadata = siteModel.loadDeviceMetadata(device);
          reportingDevice.setMetadata(metadata);
        } catch (Exception e) {
          String detail = friendlyStackTrace(e);
          outputLogger.error("Error while loading device %s: %s", device, detail);
          reportingDevice.addError(e, Category.VALIDATION_DEVICE_SCHEMA, detail);
        }
        reportingDevices.put(device, reportingDevice);
      }
      outputLogger.info("Loaded " + reportingDevices.size() + " expected devices");
    } catch (Exception e) {
      throw new RuntimeException(
          "While loading devices directory " + siteModel.getDevicesDir().getAbsolutePath(), e);
    }
  }

  private ReportingDevice newReportingDevice(String device) {
    ReportingDevice reportingDevice = new ReportingDevice(device);
    ifTrueThen(validateCurrent, () -> reportingDevice.setThreshold(reportingDelaySec));
    return reportingDevice;
  }

  /**
   * Set the schema specification directory.
   *
   * @param schemaPath schema specification directory
   */
  @CommandLineOption(short_form = "-a", arg_name = "schema_path", description = "Set schema path")
  private void setSchemaSpec(String schemaPath) {
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

    // Copy the metadata schema to model, since sometimes it's referenced that way.
    schemaMap.put("model", schemaMap.get("metadata"));

    return schemaMap;
  }

  private String getRegistryId() {
    if (config == null) {
      return null;
    }
    String prefix = getNamespacePrefix(config.udmi_namespace);
    String suffix = ofNullable(config.registry_suffix).orElse("");
    return prefix + config.registry_id + suffix;
  }

  private void validateReflector() {
    String keyFile = new File(config.site_model, GCP_REFLECT_KEY_PKCS8).getAbsolutePath();
    outputLogger.info("Loading reflector key file from " + keyFile);
    config.key_file = keyFile;
    client = new ValidatorIotReflectorClient(config, TOOLS_FUNCTIONS_VERSION, VALIDATOR_TOOL_NAME,
        this::messageFilter);
    dataSinks.add(client);
    client.activate();
  }

  private static class ValidatorIotReflectorClient extends IotReflectorClient {

    public ValidatorIotReflectorClient(ExecutionConfiguration iotConfig, int requiredVersion,
        String toolName, Function<Envelope, Boolean> messageFilter) {
      super(iotConfig, requiredVersion, toolName, messageFilter);
    }

    @Override
    protected void errorHandler(Throwable throwable) {
      System.err.printf("Suppressing mqtt client error: %s at %s%n",
          throwable.getMessage(), getTimestamp());
    }
  }

  private boolean messageFilter(Envelope envelope) {
    return true;
  }

  void messageLoop() {
    if (client == null || client.getSubscriptionId() == null) {
      return;
    }
    sendInitializationQuery();
    outputLogger.info("Running udmi tools version " + UDMI_TOOLS);
    outputLogger.notice("Entering message loop on " + client.getSubscriptionId());
    processValidationReport();
    ScheduledFuture<?> reportSender =
        simulatedMessages ? null : executor.scheduleAtFixedRate(this::processValidationReport,
            reportingDelaySec, reportingDelaySec, TimeUnit.SECONDS);
    try {
      while (client.isActive()) {
        try {
          validateMessage(client.takeNextMessage(QuerySpeed.SHORT));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    } finally {
      outputLogger.debug("Message loop complete");
      if (reportSender != null) {
        reportSender.cancel(true);
      }
    }
  }

  private void sendInitializationQuery() {
    for (String deviceId : targetDevices) {
      outputLogger.debug("Sending initialization query messages for device " + deviceId);
      client.publish(deviceId, UPDATE_QUERY_TOPIC, EMPTY_MESSAGE);
    }
  }

  private boolean handleSystemMessage(Map<String, String> attributes, Object object) {

    String subFolderRaw = attributes.get(SUBFOLDER_PROPERTY_KEY);
    String subTypeRaw = attributes.get(SUBTYPE_PROPERTY_KEY);

    if (SubFolder.UDMI.value().equals(subFolderRaw) && SubType.CONFIG.value().equals(subTypeRaw)) {
      handleUdmiConfig(convertTo(UdmiConfig.class, object));
      return true;
    }

    // Don't validate validation messages. Not really a problem, but sometimes validation messages
    // aren't reflected back (when not using PubSub), so for consistency just reject everything.
    return SubFolder.VALIDATION.value().equals(subFolderRaw);
  }

  private void handleUdmiConfig(UdmiConfig udmiConfig) {
    JsonUtil.writeFile(udmiConfig, new File(outBaseDir, UDMI_CONFIG_JSON_FILE));
  }

  protected synchronized void validateMessage(MessageBundle message) {
    ifNotNullThen(message, bundle -> {
      Object object = ofNullable((Object) bundle.message).orElse(bundle.rawMessage);
      if (!handleSystemMessage(bundle.attributes, object)) {
        validateMessage(object, bundle.attributes);
      }
    });
  }

  private void validateMessage(Object msgObject, Map<String, String> attributes) {
    String deviceId = attributes.get("deviceId");
    if (deviceId != null && reportingDevices.containsKey(deviceId)) {
      processedDevices.add(deviceId);
    }

    if (!shouldProcessMessage(attributes)) {
      return;
    }

    if (traceDir != null) {
      writeMessageCapture(msgObject, attributes);
    }

    if (simulatedMessages) {
      mockNow = getInstant(msgObject, attributes);
      ReportingDevice.setMockNow(mockNow);
    }

    ReportingDevice reportingDevice = validateMessageCore(msgObject, attributes);
    if (reportingDevice != null) {
      Date now = simulatedMessages ? Date.from(mockNow) : new Date();
      sendValidationResult(attributes, reportingDevice, now);
    }
    if (simulatedMessages) {
      processValidationReport();
    }
  }

  private void validateMessage(JsonSchema schema, Object message) throws Exception {
    ProcessingReport report = schema.validate(OBJECT_MAPPER.valueToTree(message), true);
    ifTrueThen(!report.isSuccess(), () -> ifNotNullThrow(fromProcessingReport(report)));
  }

  private Instant getInstant(Object msgObject, Map<String, String> attributes) {
    if (msgObject instanceof Map) {
      Map<String, Object> mapped = mapCast(msgObject);
      String timestamp = (String) mapped.get(TIMESTAMP_KEY);
      if (timestamp != null) {
        return JsonUtil.getInstant(timestamp);
      }
    }
    return JsonUtil.getInstant(attributes.get(PUBLISH_TIME_KEY));
  }

  private ReportingDevice validateMessageCore(Object messageObj, Map<String, String> attributes) {

    String deviceId = attributes.get("deviceId");
    if (deviceId == null) {
      return null;
    }

    ReportingDevice device = reportingDevices.computeIfAbsent(deviceId, this::newReportingDevice);
    device.clearMessageEntries();

    String schemaName = messageSchema(attributes);
    String messageTag = format("device %d/%d for %s/%s", processedDevices.size(),
        reportingDevices.size(), deviceId, schemaName);

    try {
      boolean isString = messageObj instanceof String;

      Map<String, Object> message = isString ? null : mapCast(messageObj);
      validateTimestamp(device, message, attributes);

      if (!device.shouldProcessMessage(schemaName, getInstant(messageObj, attributes))) {
        outputLogger.trace("Ignoring device %s/%s (too soon)", deviceId, schemaName);
        return null;
      }

      writeDeviceOutCapture(messageObj, attributes, deviceId, schemaName);

      String subFolder = attributes.get(SUBFOLDER_PROPERTY_KEY);
      boolean processSchema = !IGNORE_FOLDERS.contains(subFolder);

      try {
        if (processSchema && !schemaMap.containsKey(schemaName)) {
          throw new IllegalArgumentException(format(SCHEMA_SKIP_FORMAT, schemaName, deviceId));
        }
      } catch (Exception e) {
        outputLogger.error("Missing schema entry " + schemaName);
        device.addError(e, attributes, Category.VALIDATION_DEVICE_RECEIVE);
      }

      if (isString) {
        String detail = format("Raw string message for %s %s", deviceId, schemaName);
        outputLogger.error(detail);
        IllegalArgumentException exception = new IllegalArgumentException(detail);
        device.addError(exception, attributes, Category.VALIDATION_DEVICE_RECEIVE);
        return device;
      }

      if ("true".equals(attributes.get("wasBase64"))) {
        base64Devices.add(deviceId);
      }

      if (processExceptions(attributes, deviceId, device, message)) {
        return device;
      }

      validateDeviceMessage(device, message, attributes);

      if (message.containsKey(UPGRADED_FROM)) {
        File jsonFile = getDeviceOutCaptureFile(deviceId, schemaName, false);
        File origFile = getDeviceOutCaptureFile(deviceId, schemaName, true);
        jsonFile.renameTo(origFile);
        writeDeviceOutCapture(message, attributes, deviceId, schemaName);
      }

      validationStats.update();

      if (!device.hasErrors()) {
        outputLogger.info("Validation clean %s", messageTag);
      } else {
        outputLogger.info("Validation error %s", messageTag);
      }
    } catch (Exception e) {
      outputLogger.error("Validation exception %s: %s", messageTag, friendlyStackTrace(e));
      device.addError(e, attributes, Category.VALIDATION_DEVICE_RECEIVE);
    }
    return device;
  }

  private boolean processExceptions(Map<String, String> attributes, String deviceId,
      ReportingDevice device, Map<String, Object> message) {
    if (message.get(EXCEPTION_KEY) instanceof Exception exception) {
      outputLogger.error("Pipeline exception " + deviceId + ": " + getExceptionMessage(exception));
      device.addError(exception, attributes, Category.VALIDATION_DEVICE_RECEIVE);
      return true;
    }

    if (message.containsKey(ERROR_KEY)) {
      outputLogger.error("Ignoring pipeline error " + deviceId + ": " + message.get(ERROR_KEY));
      return true;
    }
    return false;
  }

  /**
   * Validate a device message against the core schema.
   */
  public void validateDeviceMessage(ReportingDevice device, Map<String, Object> message,
      Map<String, String> attributes) {
    String schemaName = ofNullable(attributes.get(SCHEMA_NAME_KEY)).orElseGet(
        () -> messageSchema(attributes));
    upgradeMessage(schemaName, message);

    // Assume the attributes know what they're doing when the schema name is provided explicitly.
    if (!attributes.containsKey(SCHEMA_NAME_KEY)) {
      try {
        validateMessage(schemaMap.get(ENVELOPE_SCHEMA_ID), (Object) attributes);
      } catch (Exception e) {
        outputLogger.error("Error validating attributes: " + friendlyStackTrace(e));
        device.addError(e, attributes, Category.VALIDATION_DEVICE_RECEIVE);
      }
    }

    if (schemaMap.containsKey(schemaName)) {
      try {
        validateMessage(schemaMap.get(schemaName), message);
      } catch (Exception e) {
        outputLogger.error("Error validating schema %s: %s", schemaName, friendlyStackTrace(e));
        device.addError(e, attributes, Category.VALIDATION_DEVICE_SCHEMA);
      }
    }

    String deviceId = attributes.get("deviceId");
    if (expectedDevices == null || expectedDevices.isEmpty()) {
      // No devices configured, so don't consider check metadata or consider extra.
    } else if (expectedDevices.contains(deviceId)) {
      try {
        device.validateRawMessage(schemaName, message, attributes);
      } catch (Exception e) {
        outputLogger.error("Error validating contents: " + friendlyStackTrace(e));
        device.addError(e, attributes, Category.VALIDATION_DEVICE_CONTENT);
      }
    } else {
      extraDevices.add(deviceId);
    }
  }

  private void validateTimestamp(ReportingDevice device, Map<String, Object> message,
      Map<String, String> attributes) {
    String timestampRaw = ifNotNullGet(message, m -> (String) m.get("timestamp"));
    Instant timestamp = ifNotNullGet(timestampRaw, JsonUtil::getInstant);
    String publishRaw = attributes.get(PUBLISH_TIME_KEY);
    Instant publishTime = ifNotNullGet(publishRaw, JsonUtil::getInstant);
    try {
      // TODO: Validate message contests to make sure state sub-blocks don't also have timestamp.

      String subTypeRaw = ofNullable(attributes.get(SUBTYPE_PROPERTY_KEY))
          .orElse(UNKNOWN_TYPE_DEFAULT);
      boolean lastSeenValid = LAST_SEEN_SUBTYPES.contains(SubType.fromValue(subTypeRaw));
      if (lastSeenValid) {
        if (publishTime != null) {
          device.updateLastSeen(Date.from(publishTime));
        }
        if (message != null && timestamp == null) {
          throw new RuntimeException("Missing message timestamp");
        }
        if (timestampRaw != null
            && !timestampRaw.endsWith(TIMESTAMP_ZULU_SUFFIX)
            && !timestampRaw.endsWith(TIMESTAMP_UTC_SUFFIX_1)
            && !timestampRaw.endsWith(TIMESTAMP_UTC_SUFFIX_2)) {
          throw new RuntimeException("Invalid timestamp timezone " + timestampRaw);
        }
        if (publishTime != null && timestamp != null) {
          long between = Duration.between(publishTime, timestamp).getSeconds();
          if (between > TIMESTAMP_JITTER_SEC || between < -TIMESTAMP_JITTER_SEC) {
            throw new RuntimeException(format(
                "Timestamp skew %ds (%s to %s) exceeds %ds threshold",
                between, publishRaw, timestampRaw, TIMESTAMP_JITTER_SEC));
          }
        }
      }
    } catch (Exception e) {
      outputLogger.debug(format("Timestamp validation error for %s: %s", device.getDeviceId(),
          friendlyStackTrace(e)));
      device.addError(e, attributes, Category.VALIDATION_DEVICE_CONTENT);
    }
  }

  private void sendValidationResult(Map<String, String> origAttributes,
      ReportingDevice reportingDevice, Date now) {
    try {
      ValidationEvents event = new ValidationEvents();
      event.version = UDMI_VERSION;
      event.timestamp = new Date();
      String subFolder = origAttributes.get(SUBFOLDER_PROPERTY_KEY);
      event.sub_folder = subFolder;
      String subType = origAttributes.get(SUBTYPE_PROPERTY_KEY);
      event.sub_type = ofNullable(subType).orElse(UNKNOWN_TYPE_DEFAULT);
      event.errors = reportingDevice.getMessageEntries();
      event.status = ReportingDevice.getSummaryEntry(event.errors);
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
    sendValidationReport(VALIDATION_SITE_REPORT_DEVICE_ID, report);
  }

  private void sendValidationReport(String deviceId, ValidationState report) {
    try {
      sendValidationMessage(deviceId, report, VALIDATION_STATE_TOPIC);
    } catch (Exception e) {
      throw new RuntimeException("While sending validation report", e);
    }
  }

  private synchronized void sendValidationMessage(String deviceId, Object message, String topic) {
    try {
      String messageString = OBJECT_MAPPER.writeValueAsString(message);
      dataSinks.forEach(sink -> sink.publish(deviceId, topic, messageString));
    } catch (Exception e) {
      throw new RuntimeException("While sending validation event for " + deviceId, e);
    }
  }

  private void writeMessageCapture(Object message, Map<String, String> attributes) {
    String deviceId = attributes.get("deviceId");
    String type = attributes.getOrDefault(SUBTYPE_PROPERTY_KEY, UNKNOWN_TYPE_DEFAULT);
    String folder = attributes.get(SUBFOLDER_PROPERTY_KEY);
    AtomicInteger messageIndex = deviceMessageIndex.computeIfAbsent(deviceId,
        key -> new AtomicInteger());
    int index = messageIndex.incrementAndGet();
    String filename = format("%03d_%s.json", index, typeFolderPairKey(type, folder));
    File deviceDir = new File(traceDir, ofNullable(deviceId).orElse(REGISTRY_DEVICE_DEFAULT));
    File messageFile = new File(deviceDir, filename);
    try {
      deviceDir.mkdir();
      String timestamp = isoConvert(getInstant(message, attributes));
      outputLogger.debug("Capture %s at %s for %s", filename, timestamp, deviceId);
      OBJECT_MAPPER.writeValue(messageFile, message);
    } catch (Exception e) {
      throw new RuntimeException("While writing message file " + messageFile.getAbsolutePath(), e);
    }
  }

  private boolean shouldProcessMessage(Map<String, String> attributes) {
    String registryId = attributes.get(DEVICE_REGISTRY_ID_KEY);

    if (registryId == null || !registryId.equals(getRegistryId())) {
      if (registryId != null && ignoredRegistries.add(registryId)) {
        outputLogger.warn("Ignoring data for not-configured registry " + registryId);
      }
      return false;
    }

    String deviceId = attributes.get("deviceId");
    if (!targetDevices.isEmpty() && deviceId != null && !targetDevices.contains(deviceId)) {
      return false;
    }

    String subType = attributes.get(SUBTYPE_PROPERTY_KEY);
    String subFolder = attributes.get(SUBFOLDER_PROPERTY_KEY);
    String category = attributes.get("category");
    boolean ignore = CONFIG_CATEGORY.equals(category);
    boolean process = subType == null
        || INTERESTING_TYPES.contains(subType)
        || SubFolder.UPDATE.value().equals(subFolder);
    return process && !ignore;
  }

  private void writeDeviceOutCapture(Object message, Map<String, String> attributes,
      String deviceId, String schemaName) throws IOException {

    File messageFile = getDeviceOutCaptureFile(deviceId, schemaName, false);

    if (message instanceof Map) {
      Map<String, Object> messageMap = mapCast(message);
      // OBJECT_MAPPER can't handle an Exception class object, so do a swap-and-restore.
      Exception saved = (Exception) messageMap.get(EXCEPTION_KEY);
      messageMap.put(EXCEPTION_KEY, ifNotNullGet(saved, GeneralUtils::friendlyStackTrace));
      OBJECT_MAPPER.writeValue(messageFile, messageMap);
      messageMap.put(EXCEPTION_KEY, saved);
    } else {
      OBJECT_MAPPER.writeValue(messageFile, message);
    }

    File deviceDir = makeDeviceDir(deviceId);
    File attributesFile = new File(deviceDir, format(ATTRIBUTE_FILE_FORMAT, schemaName));
    OBJECT_MAPPER.writeValue(attributesFile, attributes);
  }

  private File getDeviceOutCaptureFile(String deviceId, String schemaName, boolean orig) {
    File deviceDir = makeDeviceDir(deviceId);
    String fileFormat = orig ? ORIG_FILE_FORMAT : MESSAGE_FILE_FORMAT;
    return new File(deviceDir, format(fileFormat, schemaName));
  }

  private File makeDeviceDir(String deviceId) {
    File deviceDir = new File(outBaseDir, format(DEVICE_FILE_FORMAT, deviceId));
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
      System.err.println(validationStats.getMessage());
      processValidationReportRaw();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void processValidationReportRaw() {
    ValidationSummary summary = new ValidationSummary();
    Map<String, ValidationState> summaries = new HashMap<>();

    summary.extra_devices = new ArrayList<>(extraDevices);

    summary.correct_devices = new ArrayList<>();
    summary.error_devices = new ArrayList<>();

    Map<String, DeviceValidationEvents> devices = new TreeMap<>();
    Collection<String> targets = targetDevices.isEmpty() ? expectedDevices : targetDevices;
    for (String deviceId : reportingDevices.keySet()) {
      ReportingDevice deviceInfo = reportingDevices.get(deviceId);
      ValidationState deviceState = summaries.computeIfAbsent(deviceId,
          id -> makeDeviceValidationState(deviceInfo));
      deviceInfo.expireEntries(getNow());
      boolean expected = targets.contains(deviceId);
      if (deviceInfo.hasErrors()) {
        DeviceValidationEvents event = getValidationEvents(devices, deviceInfo);
        deviceState.last_updated = event.last_seen;
        event.status = ReportingDevice.getSummaryEntry(deviceInfo.getErrors(null, null));
        if (expected) {
          summary.error_devices.add(deviceId);
        } else {
          event.status.category = Category.VALIDATION_DEVICE_EXTRA;
          event.status.level = Level.WARNING.value();
        }
      } else if (deviceInfo.seenRecently(getNow())) {
        DeviceValidationEvents event = getValidationEvents(devices, deviceInfo);
        event.status = ReportingDevice.getSummaryEntry(deviceInfo.getErrors(null, null));
        if (expected) {
          summary.correct_devices.add(deviceId);
        } else {
          event.status.category = Category.VALIDATION_DEVICE_EXTRA;
          event.status.level = Level.WARNING.value();
        }
      }
    }

    summary.missing_devices = new ArrayList<>(targets);
    summary.missing_devices.removeAll(summary.error_devices);
    summary.missing_devices.removeAll(summary.correct_devices);

    System.err.println("Updating validation reports to " + outBaseDir.getAbsolutePath());
    sendValidationReport(makeValidationReport(summary, devices));
    sendDeviceValidationReports(summaries);
  }

  private synchronized void sendDeviceValidationReports(Map<String, ValidationState> summaries) {
    if (summaryDevices.isEmpty()) {
      List<String> keys = summaries.entrySet().stream()
          .filter(entry -> entry.getValue().last_updated.after(START_TIME))
          .map(Entry::getKey).toList();
      summaryDevices.addAll(keys);
    }
    long batchSize = reportingDelaySec * REPORTS_PER_SEC;
    List<String> sendList = summaryDevices.stream().limit(batchSize).toList();
    System.err.printf("Sending %d device validation state updates out of an available %d%n",
        sendList.size(), summaryDevices.size());
    sendList.forEach(id -> {
      sendValidationReport(id, summaries.get(id));
      summaryDevices.remove(id);
    });
  }

  private static ValidationState makeDeviceValidationState(ReportingDevice deviceInfo) {
    ValidationState validationState = new ValidationState();
    validationState.version = UDMI_VERSION;
    validationState.timestamp = GeneralUtils.getNow();
    validationState.last_updated = deviceInfo.getLastSeen();
    return validationState;
  }

  private DeviceValidationEvents getValidationEvents(Map<String, DeviceValidationEvents> devices,
      ReportingDevice deviceInfo) {
    DeviceValidationEvents deviceValidationEvents = devices.computeIfAbsent(
        deviceInfo.getDeviceId(),
        key -> new DeviceValidationEvents());
    deviceValidationEvents.last_seen = deviceInfo.getLastSeen();
    return deviceValidationEvents;
  }

  private Instant getNow() {
    return mockNow == null ? Instant.now() : mockNow;
  }

  private ValidationState makeValidationReport(ValidationSummary summary,
      Map<String, DeviceValidationEvents> devices) {
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
    ErrorMap schemaExceptions =
        new ErrorMap(format(SCHEMA_VALIDATION_FORMAT, schemaFiles.size()));
    for (File schemaFile : schemaFiles) {
      try {
        JsonSchema schema = getSchema(schemaFile);
        String fileName = schemaFile.getName();
        ErrorMap validateExceptions =
            new ErrorMap(format(TARGET_VALIDATION_FORMAT, targetFiles.size(), fileName));
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

  @CommandLineOption(short_form = "-f", arg_name = "target_spec",
      description = "Validate from files")
  private void validateFilesOutput(String targetSpec) {
    try {
      String[] parts = targetSpec.split(":");
      String prefix = parts.length == 1 ? null : parts[0];
      String file = parts[parts.length - 1];
      validateFiles(schemaSpec, prefix, file);
      client = new NullPublisher();
    } catch (ExceptionMap processingException) {
      ErrorTree errorTree = ExceptionMap.format(processingException);
      errorTree.write(System.err);
    } catch (ErrorMapException e) {
      e.getMap().values().forEach(ex -> System.err.println(friendlyStackTrace(ex)));
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
      JsonNode jsonNode = OBJECT_MAPPER.valueToTree(message);
      MessageUpgrader messageUpgrader = new MessageUpgrader(schemaName, jsonNode);
      messageUpgrader.upgrade(forceUpgrade);
      if (messageUpgrader.wasUpgraded()) {
        OBJECT_MAPPER.writeValue(outputStream, jsonNode);
      } else {
        // If the message was not upgraded, then copy over unmolested to preserve formatting.
        outputStream.close();
        FileUtils.copyFile(inputFile, outputFile);
      }
      ReportingDevice reportingDevice = new ReportingDevice(FAUX_DEVICE_ID);
      validateDeviceMessage(reportingDevice, message, makeFileAttributes(schemaName));
      reportingDevice.throwIfFailure();
      writeExceptionOutput(targetOut, null);
    } catch (Exception e) {
      writeExceptionOutput(targetOut, e);
      throw new RuntimeException("Generating output " + targetOut.getAbsolutePath(), e);
    }
  }

  private Map<String, String> makeFileAttributes(String schemaName) {
    HashMap<String, String> attributes = new HashMap<>();
    attributes.put(SCHEMA_NAME_KEY, schemaName);
    return attributes;
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
        ExceptionMap.format(e).write(outputStream);
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
    Object upgraded = new MessageUpgrader(schemaName, jsonNode).upgrade(forceUpgrade);
    Map<String, Object> objectMap = OBJECT_MAPPER.convertValue(upgraded,
        new TypeReference<>() {
        });
    message.clear();
    message.putAll(objectMap);
  }

  private File getFullPath(String prefix, File targetFile) {
    return prefix == null ? targetFile : new File(new File(prefix), targetFile.getPath());
  }

  /**
   * Simple bundle of things associated with a validation message.
   */
  public static class MessageBundle {

    public Map<String, Object> message;
    public String rawMessage;
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
