package com.google.daq.mqtt.validator;

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
import com.google.bos.iot.core.proxy.IotCoreClient;
import com.google.bos.iot.core.proxy.MessagePublisher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.util.CloudIotConfig;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.ExceptionMap;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import com.google.daq.mqtt.util.FirestoreDataSink;
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
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.Metadata;
import udmi.schema.PointsetEvent;

/**
 * Class to validate streaming messages.
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
  private static final String PUBSUB_MARKER = "pubsub";
  private static final String FILES_MARKER = "files";
  private static final String REFLECT_MARKER = "reflect";
  private static final String DEVICE_FILE_FORMAT = "devices/%s";
  private static final String ATTRIBUTE_FILE_FORMAT = "%s.attr";
  private static final String MESSAGE_FILE_FORMAT = "%s.json";
  private static final String ERROR_FILE_FORMAT = "%s.out";
  private static final String SCHEMA_SKIP_FORMAT = "Unknown schema subFolder '%s' for %s";
  private static final String ENVELOPE_SCHEMA_ID = "envelope";
  private static final String METADATA_JSON = "metadata.json";
  private static final String DEVICES_SUBDIR = "devices";
  private static final String REPORT_JSON_FILENAME = "validation_report.json";
  private static final String DEVICE_REGISTRY_ID_KEY = "deviceRegistryId";
  private static final String UNKNOWN_FOLDER_DEFAULT = "unknown";
  private static final String EVENT_POINTSET = "event_pointset";
  private static final String GCP_REFLECT_KEY_PKCS8 = "gcp_reflect_key.pkcs8";
  private static final String EMPTY_MESSAGE = "{}";
  private static final String CONFIG_PREFIX = "config_";
  private static final String STATE_PREFIX = "state_";
  private static final String UNKNOWN_TYPE_DEFAULT = "event";
  private final String projectId;
  private final Map<String, ReportingDevice> expectedDevices = new TreeMap<>();
  private final Set<String> extraDevices = new TreeSet<>();
  private final Set<String> processedDevices = new TreeSet<>();
  private final Set<String> base64Devices = new TreeSet<>();
  private final Set<String> ignoredRegistries = new HashSet();
  private File outBaseDir;
  private File metadataReportFile;
  private FirestoreDataSink dataSink;
  private File schemaRoot;
  private String schemaSpec;
  private CloudIotConfig cloudIotConfig;
  private CloudIotManager cloudIotManager;
  private String siteDir;
  private String deviceId;
  private MessagePublisher client;

  /**
   * Validator for streaming message validator.
   *
   * @param projectId Project to validate against.
   */
  public Validator(String projectId) {
    this.projectId = projectId;
  }

  /**
   * Instantiate a stream validator instance.
   *
   * @param args What to do!
   */
  public static void main(String[] args) {
    if (args.length != 5) {
      throw new IllegalArgumentException("Args: [project] [schema] [target] [instance] [site]");
    }
    try {
      Validator validator = new Validator(args[0]);
      validator.setSchemaSpec(args[1]);
      String targetSpec = args[2];
      String instName = args[3];
      String siteDir = args[4];
      validator.setSiteDir(siteDir);
      switch (targetSpec) {
        case PUBSUB_MARKER:
          validator.initializeCloudIoT();
          validator.initializeFirestoreDataSink();
          validator.validatePubSub(instName);
          break;
        case FILES_MARKER:
          validator.validateFilesOutput(instName);
          break;
        case REFLECT_MARKER:
          validator.validateReflector(instName);
          break;
        default:
          throw new RuntimeException("Unknown target spec " + targetSpec);
      }
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

  private void setSiteDir(String siteDir) {
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
    metadataReportFile = new File(outBaseDir, REPORT_JSON_FILENAME);
    metadataReportFile.delete();
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

  private void initializeFirestoreDataSink() {
    // TODO: Make this configurable somehow.
    //    dataSink = new FirestoreDataSink(projectId);
    //    System.err.println("Results will be uploaded to " + dataSink.getViewUrl());
  }

  private void setSchemaSpec(String schemaPath) {
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
    outBaseDir.mkdirs();
    System.err.println("Results may be in such directories as " + outBaseDir.getAbsolutePath());
    System.err.println("Generating report file in " + metadataReportFile.getAbsolutePath());

    Map<String, JsonSchema> schemaMap = getSchemaMap();
    return (message, attributes) -> validateMessage(schemaMap, message, attributes);
  }

  private void validatePubSub(String instName) {
    String registryId = cloudIotConfig.registry_id;
    client = new PubSubClient(projectId, registryId, instName);
  }

  private void validateReflector(String instName) {
    deviceId = NO_SITE.equals(instName) ? null : instName;
    String keyFile = new File(siteDir, GCP_REFLECT_KEY_PKCS8).getAbsolutePath();
    System.err.println("Loading reflector key file from " + keyFile);
    client = new IotCoreClient(projectId, cloudIotConfig, keyFile);
  }

  private void messageLoop() {
    if (client == null) {
      return;
    }
    System.err.println(
        "Entering message loop on " + client.getSubscriptionId() + " with device " + deviceId);
    BiConsumer<Map<String, Object>, Map<String, String>> validator = messageValidator();
    boolean initialized = false;
    while (client.isActive()) {
      try {
        if (!initialized) {
          initialized = true;
          sendInitializationQuery();
        }
        client.processMessage(validator);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    System.err.println("Message loop complete");
  }

  private void sendInitializationQuery() {
    if (deviceId != null) {
      System.err.println("Sending initialization query messages for device " + deviceId);
      client.publish(deviceId, STATE_QUERY_TOPIC, EMPTY_MESSAGE);
    }
  }

  private Set<String> convertIgnoreSet(String ignoreSpec) {
    if (ignoreSpec == null) {
      return ImmutableSet.of();
    }
    return Arrays.stream(ignoreSpec.split(",")).collect(Collectors.toSet());
  }

  private void validateMessage(
      Map<String, JsonSchema> schemaMap,
      Map<String, Object> message,
      Map<String, String> attributes) {
    if (validateUpdate(schemaMap, message, attributes)) {
      writeDeviceMetadataReport();
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
      message.remove(TIMESTAMP_ATTRIBUTE);
    }
  }

  private boolean validateUpdate(
      Map<String, JsonSchema> schemaMap,
      Map<String, Object> message,
      Map<String, String> attributes) {

    String registryId = attributes.get(DEVICE_REGISTRY_ID_KEY);
    if (cloudIotConfig != null && !cloudIotConfig.registry_id.equals(registryId)) {
      if (ignoredRegistries.add(registryId)) {
        System.err.println("Ignoring data for not-configured registry " + registryId);
      }
      return false;
    }

    if (!shouldValidateMessage(attributes)) {
      return false;
    }

    try {
      String deviceId = attributes.get("deviceId");
      Preconditions.checkNotNull(deviceId, "Missing deviceId in message");

      if (expectedDevices.containsKey(deviceId)) {
        processedDevices.add(deviceId);
      }

      String schemaName = messageSchema(attributes);
      final ReportingDevice reportingDevice = getReportingDevice(deviceId);
      if (!reportingDevice.markMessageType(schemaName)) {
        return false;
      }

      System.err.printf(
          "Processing device #%d/%d: %s/%s%n",
          processedDevices.size(), expectedDevices.size(), deviceId, schemaName);

      if (attributes.get("wasBase64").equals("true")) {
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
        processViolation(message, attributes, deviceId, ENVELOPE_SCHEMA_ID, errorOut, e);
        reportingDevice.addError(e);
      }

      if (schemaMap.containsKey(schemaName)) {
        try {
          validateMessage(schemaMap.get(schemaName), message);
          if (dataSink != null) {
            dataSink.validationResult(deviceId, schemaName, attributes, message, null);
          }
        } catch (Exception e) {
          System.err.println("Error validating schema: " + e.getMessage());
          processViolation(message, attributes, deviceId, schemaName, errorOut, e);
          reportingDevice.addError(e);
        }
      }

      boolean updated = false;

      if (expectedDevices.isEmpty()) {
        // No devices configured, so don't check metadata.
      } else if (expectedDevices.containsKey(deviceId)) {
        try {
          if (EVENT_POINTSET.equals(schemaName)) {
            PointsetEvent pointsetMessage =
                OBJECT_MAPPER.convertValue(message, PointsetEvent.class);
            updated = !reportingDevice.hasBeenValidated();
            reportingDevice.validateMetadata(pointsetMessage);
          }
        } catch (Exception e) {
          System.err.println("Error validating contents: " + e.getMessage());
          processViolation(message, attributes, deviceId, schemaName, errorOut, e);
          reportingDevice.addError(e);
        }
      } else if (extraDevices.add(deviceId)) {
        updated = true;
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

      return updated;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private boolean shouldValidateMessage(Map<String, String> attributes) {
    String subType = attributes.get("subType");
    String subFolder = attributes.get("subFolder");
    return subType == null
        || SubType.EVENT.value().equals(subType)
        || SubFolder.UPDATE.value().equals(subFolder);
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

  private void writeDeviceMetadataReport() {
    try {
      MetadataReport metadataReport = new MetadataReport();
      metadataReport.updated = new Date();
      metadataReport.missingDevices = new TreeSet<>();
      metadataReport.extraDevices = extraDevices;
      metadataReport.pointsetDevices = new TreeSet<>();
      metadataReport.base64Devices = base64Devices;
      metadataReport.expectedDevices = expectedDevices.keySet();
      metadataReport.errorDevices = new TreeMap<>();
      for (ReportingDevice deviceInfo : expectedDevices.values()) {
        String deviceId = deviceInfo.getDeviceId();
        if (deviceInfo.hasMetadataDiff() || deviceInfo.hasError()) {
          metadataReport.errorDevices.put(deviceId, deviceInfo.getMetadataDiff());
        } else if (deviceInfo.hasBeenValidated()) {
          metadataReport.pointsetDevices.add(deviceId);
        } else {
          metadataReport.missingDevices.add(deviceId);
        }
      }
      OBJECT_MAPPER.writeValue(metadataReportFile, metadataReport);
    } catch (Exception e) {
      throw new RuntimeException(
          "While generating metadata report file " + metadataReportFile.getAbsolutePath(), e);
    }
  }

  private void processViolation(
      Map<String, Object> message,
      Map<String, String> attributes,
      String deviceId,
      String schemaId,
      PrintStream errorOut,
      Exception e) {
    ErrorTree errorTree = ExceptionMap.format(e, ERROR_FORMAT_INDENT);
    if (dataSink != null) {
      dataSink.validationResult(deviceId, schemaId, attributes, message, errorTree);
    }
    errorTree.write(errorOut);
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

  private String getTimestamp() {
    try {
      String dateString = OBJECT_MAPPER.writeValueAsString(new Date());
      return dateString.substring(1, dateString.length() - 1);
    } catch (Exception e) {
      throw new RuntimeException("Creating timestamp", e);
    }
  }

  /**
   * Report for results from processing the metadata.
   */
  public static class MetadataReport {

    public Date updated;
    public Set<String> expectedDevices;
    public Set<String> missingDevices;
    public Set<String> extraDevices;
    public Set<String> pointsetDevices;
    public Set<String> base64Devices;
    public Map<String, ReportingDevice.MetadataDiff> errorDevices;
  }

  class RelativeDownloader implements URIDownloader {

    public static final String FILE_URL_PREFIX = "file:";

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
