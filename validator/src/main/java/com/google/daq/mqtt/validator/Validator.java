package com.google.daq.mqtt.validator;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.bos.iot.core.proxy.IotCoreClient;
import com.google.bos.iot.core.proxy.MessagePublisher;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.registrar.UdmiSchema;
import com.google.daq.mqtt.registrar.UdmiSchema.Config;
import com.google.daq.mqtt.registrar.UdmiSchema.PointsetMessage;
import com.google.daq.mqtt.util.CloudIotConfig;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigUtil;
import com.google.daq.mqtt.util.ExceptionMap;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import com.google.daq.mqtt.util.FirestoreDataSink;
import com.google.daq.mqtt.util.PubSubClient;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

public class Validator {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
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
  private static final File OUT_BASE_FILE = new File("out");
  private static final String DEVICE_FILE_FORMAT = "devices/%s";
  private static final String ATTRIBUTE_FILE_FORMAT = "%s.attr";
  private static final String MESSAGE_FILE_FORMAT = "%s.json";
  private static final String ERROR_FILE_FORMAT = "%s.out";
  private static final Pattern DEVICE_ID_PATTERN =
      Pattern.compile("^([a-z][_a-z0-9-]*[a-z0-9]|[A-Z][_A-Z0-9-]*[A-Z0-9])$");
  private static final String DEVICE_MATCH_FORMAT = "DeviceId %s must match pattern %s";
  private static final String SCHEMA_SKIP_FORMAT = "Unknown schema subFolder '%s' for %s";
  private static final String ENVELOPE_SCHEMA_ID = "envelope";
  private static final String METADATA_JSON = "metadata.json";
  private static final String DEVICES_SUBDIR = "devices";
  private static final String REPORT_JSON_FILENAME = "validation_report.json";
  private static final String DEVICE_REGISTRY_ID_KEY = "deviceRegistryId";
  private static final String UNKNOWN_SCHEMA_DEFAULT = "unknown";
  private static final String EVENT_POINTSET = "event_pointset";
  private static final String NO_SITE = "--";
  private static final String GCP_REFLECT_KEY_PKCS8 = "gcp_reflect_key.pkcs8";
  private static final String EMPTY_MESSAGE = "{}";
  public static final String STATE_QUERY_TOPIC = "query/state";
  public static final String TIMESTAMP_ATTRIBUTE = "timestamp";
  private final String projectId;
  private FirestoreDataSink dataSink;
  private File schemaRoot;
  private String schemaSpec;
  private final Map<String, ReportingDevice> expectedDevices = new TreeMap<>();
  private final Set<String> extraDevices = new TreeSet<>();
  private final Set<String> processedDevices = new TreeSet<>();
  private final Set<String> base64Devices = new TreeSet<>();
  private CloudIotConfig cloudIotConfig;
  public static final File METADATA_REPORT_FILE = new File(OUT_BASE_FILE, REPORT_JSON_FILENAME);
  private final Set<String> ignoredRegistries = new HashSet();
  private CloudIotManager cloudIotManager;
  private String siteDir;
  private File expectedDir;
  private List<ExpectedMessage> expectedList;
  private String deviceId;
  private int redundantCounter;

  public static void main(String[] args) {
    if (args.length != 6) {
      throw new IllegalArgumentException(
          "Args: [project] [schema] [target] [instance] [site] [expect]");
    }
    try {
      Validator validator = new Validator(args[0]);
      validator.setSchemaSpec(args[1]);
      String targetSpec = args[2];
      String instName = args[3];
      String siteDir = args[4];
      String expectDir = args[5];
      if (!NO_SITE.equals(siteDir)) {
        validator.setSiteDir(siteDir);
      }
      if (!NO_SITE.equals(expectDir)) {
        validator.setExpectDir(expectDir);
      }
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
    } catch (ExceptionMap | ValidationException processingException) {
      System.exit(2);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.flush();
      System.exit(-1);
    }
    System.exit(0);
  }

  public Validator(String projectId) {
    this.projectId = projectId;
  }

  private void setExpectDir(String expectDir) {
    expectedDir = new File(expectDir);
    if (!expectedDir.isDirectory()) {
      throw new IllegalArgumentException(
          "Expected directory not valid: " + expectedDir.getAbsolutePath());
    }
    try {
      expectedList = new ArrayList<>();
      Arrays.stream(
          Objects.requireNonNull(expectedDir.list()))
          .filter(item -> item.endsWith(JSON_SUFFIX))
          .sorted()
          .forEach(item -> expectedList.add(loadMessage(expectedDir, item)));
    } catch (Exception e) {
      throw new RuntimeException(
          "While loading expected messages from " + expectedDir.getAbsolutePath(), e);
    }
  }

  private ExpectedMessage loadMessage(File expectedDir, String fileName) {
    return new ExpectedMessage(new File(expectedDir, fileName));
  }

  private void setSiteDir(String siteDir) {
    this.siteDir = siteDir;
    File cloudConfig = new File(siteDir, "cloud_iot_config.json");
    try {
      cloudIotConfig = ConfigUtil.readCloudIotConfig(cloudConfig);
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + cloudConfig.getAbsolutePath(), e);
    }

    initializeExpectedDevices(siteDir);
  }

  private void initializeExpectedDevices(String siteDir) {
    File devicesDir = new File(siteDir, DEVICES_SUBDIR);
    if (!devicesDir.exists()) {
      System.err
          .println("Directory not found, assuming no devices: " + devicesDir.getAbsolutePath());
      return;
    }
    try {
      for (String device : Objects.requireNonNull(devicesDir.list())) {
        ReportingDevice reportingDevice = new ReportingDevice(device);
        try {
          File deviceDir = new File(devicesDir, device);
          File metadataFile = new File(deviceDir, METADATA_JSON);
          reportingDevice.setMetadata(
              OBJECT_MAPPER.readValue(metadataFile, UdmiSchema.Metadata.class));
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
    dataSink = new FirestoreDataSink(projectId);
    System.err.println("Results will be uploaded to " + dataSink.getViewUrl());
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

  private Map<String, Schema> getSchemaMap() {
    Map<String, Schema> schemaMap = new TreeMap<>();
    for (File schemaFile : makeFileList(schemaRoot)) {
      Schema schema = getSchema(schemaFile);
      String fullName = schemaFile.getName();
      String schemaName = schemaFile.getName()
          .substring(0, fullName.length() - JSON_SUFFIX.length());
      schemaMap.put(schemaName, schema);
    }
    if (!schemaMap.containsKey(ENVELOPE_SCHEMA_ID)) {
      throw new RuntimeException("Missing schema for attribute validation: " + ENVELOPE_SCHEMA_ID);
    }
    return schemaMap;
  }

  private BiConsumer<Map<String, Object>, Map<String, String>> messageValidator() {
    Map<String, Schema> schemaMap = getSchemaMap();
    OUT_BASE_FILE.mkdirs();
    System.err.println("Results may be in such directories as " + OUT_BASE_FILE.getAbsolutePath());
    System.err.println("Generating report file in " + METADATA_REPORT_FILE.getAbsolutePath());

    return (message, attributes) -> validateMessage(schemaMap, message, attributes);
  }

  private void validatePubSub(String instName) {
    PubSubClient client = new PubSubClient(projectId, instName);
    messageLoop(client);
  }

  private void validateReflector(String instName) {
    deviceId = instName;
    IotCoreClient client = new IotCoreClient(projectId, cloudIotConfig, GCP_REFLECT_KEY_PKCS8);
    messageLoop(client);
  }

  private void messageLoop(MessagePublisher client) {
    System.err.println("Entering message loop on "
        + client.getSubscriptionId() + " for device " + deviceId);
    BiConsumer<Map<String, Object>, Map<String, String>> validator = messageValidator();
    boolean initialized = false;
    while (client.isActive() && sendNextExpected(client)) {
      try {
        if (!initialized) {
          initialized = true;
          sendInitializationQuery(client);
        }
        client.processMessage(validator);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    System.err.println("Message loop complete");
  }

  private void sendInitializationQuery(MessagePublisher client) {
    System.err.println("Sending initialization query messages");
    client.publish(deviceId, STATE_QUERY_TOPIC, EMPTY_MESSAGE);
  }

  private Set<String> convertIgnoreSet(String ignoreSpec) {
    if (ignoreSpec == null) {
      return ImmutableSet.of();
    }
    return Arrays.stream(ignoreSpec.split(",")).collect(Collectors.toSet());
  }

  private void validateMessage(Map<String, Schema> schemaMap, Map<String, Object> message,
      Map<String, String> attributes) {
    attributes.put(TIMESTAMP_ATTRIBUTE, getTimestamp());
    if (expectedList != null) {
      matchNextExpected(message, attributes);
    } else if (validateUpdate(schemaMap, message, attributes)) {
      writeDeviceMetadataReport();
    }
  }

  private void matchNextExpected(Map<String, Object> message, Map<String, String> attributes) {
    try {
      String schemaName = getSchemaName(attributes);
      File errorFile = prepareDeviceOutDir(message, attributes, deviceId, schemaName);
      List<ExpectedMessage> groupMessages = getExpectedGroupList(false);
      if (groupMessages == null) {
        return;
      }
      System.err.printf("Matching %s at %s: %s%n", schemaName, attributes.get(TIMESTAMP_ATTRIBUTE),
          Joiner.on(" ").join(groupMessages));
      int i = 0;
      for (ExpectedMessage expectedMessage : groupMessages) {
        i++;
        boolean typeMatch = expectedMessage.messageTypeErrors(attributes).isEmpty();
        if (!typeMatch) {
          continue;
        }
        List<String> matchErrors = expectedMessage.matches(message, attributes);
        if (matchErrors.isEmpty()) {
          System.err.println("Successful match against " + expectedMessage.getName());
          expectedList.remove(expectedMessage);
          errorFile.delete();
        } else {
          matchErrors.add("against " + expectedMessage.getName());
          System.err.printf("Match error (%d): %s%n", i, Joiner.on(", ").join(matchErrors));
          try (PrintStream errorOut = new PrintStream(errorFile)) {
            errorOut.print(Joiner.on("\n").join(matchErrors));
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("While checking next match", e);
    }
  }

  private String getSchemaName(Map<String, String> attributes) {
    String subType = attributes.get("subType");
    String subFolder = attributes.get("subFolder");
    return String.format("%s_%s", subType, subFolder);
  }

  private boolean sendNextExpected(MessagePublisher sender) {
    if (expectedList == null) {
      return true;
    }
    List<ExpectedMessage> groupList = getExpectedGroupList(true);
    if (groupList == null) {
      return false;
    }
    for (ExpectedMessage sendingMessage : groupList) {
      System.err.println("Sending message " + sendingMessage.getName());
      sender.publish(deviceId, sendingMessage.getSendTopic(), sendingMessage.createMessage());
      expectedList.remove(sendingMessage);
    }
    return !expectedList.isEmpty();
  }

  private List<ExpectedMessage> getExpectedGroupList(boolean sendMessage) {
    if (expectedList == null || expectedList.isEmpty()) {
      return null;
    }
    ExpectedMessage firstMessage = expectedList.get(0);
    List<ExpectedMessage> groupList = expectedList.stream()
        .filter(item -> item.isSameGroup(firstMessage) && item.isSendMessage() == sendMessage)
        .collect(Collectors.toList());
    return groupList;
  }

  private boolean validateUpdate(Map<String, Schema> schemaMap, Map<String, Object> message,
      Map<String, String> attributes) {

    String registryId = attributes.get(DEVICE_REGISTRY_ID_KEY);
    if (cloudIotConfig != null && !cloudIotConfig.registry_id.equals(registryId)) {
      if (ignoredRegistries.add(registryId)) {
        System.err.println("Ignoring data for not-configured registry " + registryId);
      }
      return false;
    }

    try {
      String deviceId = attributes.get("deviceId");
      Preconditions.checkNotNull(deviceId, "Missing deviceId in message");

      if (expectedDevices.containsKey(deviceId)) {
        processedDevices.add(deviceId);
      }

      String schemaName = makeSchemaName(attributes);
      final ReportingDevice reportingDevice = getReportingDevice(deviceId);
      if (!reportingDevice.markMessageType(schemaName)) {
        return false;
      }

      System.err.printf("Processing device #%d/%d: %s/%s%n",
          processedDevices.size(), expectedDevices.size(), deviceId, schemaName);

      if (attributes.get("wasBase64").equals("true")) {
        base64Devices.add(deviceId);
      }

      File errorFile = prepareDeviceOutDir(message, attributes, deviceId, schemaName);

      try {
        if (!schemaMap.containsKey(schemaName)) {
          throw new IllegalArgumentException(
              String.format(SCHEMA_SKIP_FORMAT, schemaName, deviceId));
        }
      } catch (Exception e) {
        System.err.println(e.getMessage());
        try (PrintStream errorOut = new PrintStream(errorFile)) {
            errorOut.println(e.getMessage());
        }
        reportingDevice.addError(e);
      }

      try {
        validateMessage(schemaMap.get(ENVELOPE_SCHEMA_ID), attributes);
        validateDeviceId(deviceId);
      } catch (ExceptionMap | ValidationException e) {
        System.err.println("Error validating attributes: " + e.getMessage());
        processViolation(message, attributes, deviceId, ENVELOPE_SCHEMA_ID, errorFile, e);
        reportingDevice.addError(e);
      }

      if (schemaMap.containsKey(schemaName)) {
        try {
          validateMessage(schemaMap.get(schemaName), message);
          if (dataSink != null) {
            dataSink.validationResult(deviceId, schemaName, attributes, message, null);
          }
        } catch (ExceptionMap | ValidationException e) {
          System.err.println("Error validating message: " + e.getMessage());
          processViolation(message, attributes, deviceId, schemaName, errorFile, e);
          reportingDevice.addError(e);
        }
      }

      boolean updated = false;

      if (expectedDevices.isEmpty()) {
        // No devices configured, so don't check metadata.
        updated = false;
      } else if (expectedDevices.containsKey(deviceId)) {
        try {
          if (EVENT_POINTSET.equals(schemaName)) {
            PointsetMessage pointsetMessage =
                OBJECT_MAPPER.convertValue(message, PointsetMessage.class);
            updated = !reportingDevice.hasBeenValidated();
            reportingDevice.validateMetadata(pointsetMessage);
          }
        } catch (Exception e) {
          e.printStackTrace();
          OBJECT_MAPPER.writeValue(errorFile, e.getMessage());
          reportingDevice.addError(e);
        }
      } else if (extraDevices.add(deviceId)) {
        updated = true;
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

  private File prepareDeviceOutDir(Map<String, Object> message, Map<String, String> attributes,
      String deviceId, String schemaName) throws IOException {

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
    File deviceDir = new File(OUT_BASE_FILE, String.format(DEVICE_FILE_FORMAT, deviceId));
    deviceDir.mkdirs();
    return deviceDir;
  }

  private String makeSchemaName(Map<String, String> attributes) {
    String subFolder = attributes.get("subFolder");
    String subType = attributes.get("subType");

    if (Strings.isNullOrEmpty(subFolder)) {
      subFolder = UNKNOWN_SCHEMA_DEFAULT;
    }

    if (Strings.isNullOrEmpty(subType)) {
      return "event_" + subFolder;
    }

    if (!subType.endsWith("s")) {
      throw new RuntimeException("Malformed message subType " + subType);
    }

    return String.format("%s_%s", subType.substring(0, subType.length() - 1), subFolder);
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
      OBJECT_MAPPER.writeValue(METADATA_REPORT_FILE, metadataReport);
    } catch (Exception e) {
      throw new RuntimeException(
          "While generating metadata report file " + METADATA_REPORT_FILE.getAbsolutePath(), e);
    }
  }

  public static class MetadataReport {

    public Date updated;
    public Set<String> expectedDevices;
    public Set<String> missingDevices;
    public Set<String> extraDevices;
    public Set<String> pointsetDevices;
    public Set<String> base64Devices;
    public Map<String, ReportingDevice.MetadataDiff> errorDevices;
  }

  private void processViolation(Map<String, Object> message, Map<String, String> attributes,
      String deviceId, String schemaId, File errorFile, RuntimeException e)
      throws FileNotFoundException {
    ErrorTree errorTree = ExceptionMap.format(e, ERROR_FORMAT_INDENT);
    if (dataSink != null) {
      dataSink.validationResult(deviceId, schemaId, attributes, message, errorTree);
    }
    try (PrintStream errorOut = new PrintStream(errorFile)) {
      errorTree.write(errorOut);
    }
  }

  private Config getCurrentConfig(String deviceId) {
    String deviceConfig = cloudIotManager.getDeviceConfig(deviceId);
    try {
      return OBJECT_MAPPER.readValue(deviceConfig, Config.class);
    } catch (Exception e) {
      throw new RuntimeException("While converting previous config " + deviceId);
    }
  }

  private void validateDeviceId(String deviceId) {
    if (!DEVICE_ID_PATTERN.matcher(deviceId).matches()) {
      throw new ExceptionMap(
          String.format(DEVICE_MATCH_FORMAT, deviceId, DEVICE_ID_PATTERN.pattern()));
    }
  }

  private void validateFiles(String schemaSpec, String targetSpec) {
    List<File> schemaFiles = makeFileList(schemaSpec);
    if (schemaFiles.size() == 0) {
      throw new RuntimeException("Cowardly refusing to validate against zero schemas");
    }
    List<File> targetFiles = makeFileList(targetSpec);
    if (targetFiles.size() == 0) {
      throw new RuntimeException("Cowardly refusing to validate against zero targets");
    }
    ExceptionMap schemaExceptions = new ExceptionMap(
        String.format(SCHEMA_VALIDATION_FORMAT, schemaFiles.size()));
    for (File schemaFile : schemaFiles) {
      try {
        Schema schema = getSchema(schemaFile);
        ExceptionMap validateExceptions = new ExceptionMap(
            String.format(TARGET_VALIDATION_FORMAT, targetFiles.size(), schemaFile.getName()));
        for (File targetFile : targetFiles) {
          try {
            System.out
                .println("Validating " + targetFile.getName() + " against " + schemaFile.getName());
            validateFile(targetFile, schema);
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
      validateFiles(schemaSpec, targetSpec);
    } catch (ExceptionMap | ValidationException processingException) {
      ErrorTree errorTree = ExceptionMap.format(processingException, ERROR_FORMAT_INDENT);
      errorTree.write(System.err);
      throw processingException;
    }
  }

  private Schema getSchema(File schemaFile) {
    try (InputStream schemaStream = new FileInputStream(schemaFile)) {
      JSONObject rawSchema = new JSONObject(new JSONTokener(schemaStream));
      SchemaLoader loader = SchemaLoader.builder().schemaJson(rawSchema)
          .httpClient(new RelativeClient()).build();
      return loader.load().build();
    } catch (Exception e) {
      throw new RuntimeException("While loading schema " + schemaFile.getAbsolutePath(), e);
    }
  }

  class RelativeClient implements SchemaClient {

    public static final String FILE_URL_PREFIX = "file:";

    @Override
    public InputStream get(String url) {
      try {
        if (!url.startsWith(FILE_URL_PREFIX)) {
          throw new IllegalStateException("Expected path to start with " + FILE_URL_PREFIX);
        }
        String new_url =
            FILE_URL_PREFIX + new File(schemaRoot, url.substring(FILE_URL_PREFIX.length()));
        return (InputStream) (new URL(new_url)).getContent();
      } catch (Exception e) {
        throw new RuntimeException("While loading URL " + url, e);
      }
    }
  }

  private List<File> makeFileList(String spec) {
    return makeFileList(new File(spec));
  }

  private List<File> makeFileList(File target) {
    if (target.isFile()) {
      return ImmutableList.of(target);
    }
    boolean isDir = target.isDirectory();
    String prefix = isDir ? "" : target.getName();
    File parent = isDir ? target : target.getAbsoluteFile().getParentFile();
    if (!parent.isDirectory()) {
      throw new RuntimeException("Parent directory not found " + parent.getAbsolutePath());
    }

    FilenameFilter filter = (dir, file) -> file.startsWith(prefix) && file.endsWith(JSON_SUFFIX);
    String[] fileNames = parent.list(filter);

    return Arrays.stream(fileNames).map(name -> new File(parent, name))
        .collect(Collectors.toList());
  }

  private void validateMessage(Schema schema, Object message) {
    final String stringMessage;
    try {
      stringMessage = OBJECT_MAPPER.writeValueAsString(message);
    } catch (Exception e) {
      throw new RuntimeException("While converting to string", e);
    }
    schema.validate(new JSONObject(new JSONTokener(stringMessage)));
  }

  private void validateFile(File targetFile, Schema schema) {
    try (InputStream targetStream = new FileInputStream(targetFile)) {
      schema.validate(new JSONObject(new JSONTokener(targetStream)));
    } catch (Exception e) {
      throw new RuntimeException("Against input " + targetFile, e);
    }
  }

  private String getTimestamp() {
    try {
      String dateString = OBJECT_MAPPER.writeValueAsString(new Date());
      return dateString.substring(1, dateString.length() - 1);
    } catch (Exception e) {
      throw new RuntimeException("Creating timestamp", e);
    }
  }
}
