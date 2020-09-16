package com.google.daq.mqtt.validator;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.daq.mqtt.registrar.UdmiSchema;
import com.google.daq.mqtt.registrar.UdmiSchema.Config;
import com.google.daq.mqtt.registrar.UdmiSchema.PointsetMessage;
import com.google.daq.mqtt.util.*;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Validator {

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy hh:mm");

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
  private static final String POINTSET_TYPE = "pointset";
  private static final String NO_SITE = "--";
  private static final String SUB_FOLDER_KEY = "subFolder";
  private static final String STATE_SUBFOLER = "state";
  private static final String DEVICE_ID_KEY = "deviceId";
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
  private Set<String> ignoredRegistries = new HashSet();
  private CloudIotManager cloudIotManager;

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
      if (!NO_SITE.equals(siteDir)) {
        validator.setSiteDir(siteDir);
      }
      if (targetSpec.equals(PUBSUB_MARKER)) {
        validator.validatePubSub(instName);
      } else {
        validator.validateFilesOutput(targetSpec);
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

  private void setSiteDir(String siteDir) {
    File cloudConfig = new File(siteDir, "cloud_iot_config.json");
    try {
      cloudIotConfig = ConfigUtil.readCloudIotConfig(cloudConfig);
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + cloudConfig.getAbsolutePath(), e);
    }

    try {
      this.cloudIotManager = new CloudIotManager(projectId, cloudConfig, "foobar");
    } catch (Exception e) {
      throw new RuntimeException("While initializating cloud IoT for project " + projectId);
    }

    File devicesDir = new File(siteDir, DEVICES_SUBDIR);
    if (!devicesDir.exists()) {
      System.out.println("Directory not found, assuming no devices: " + devicesDir.getAbsolutePath());
      return;
    }
    try {
      for (String device : Objects.requireNonNull(devicesDir.list())) {
        try {
          File deviceDir = new File(devicesDir, device);
          File metadataFile = new File(deviceDir, METADATA_JSON);
          ReportingDevice reportingDevice = new ReportingDevice(device);
          reportingDevice.setMetadata(
              OBJECT_MAPPER.readValue(metadataFile, UdmiSchema.Metadata.class));
          expectedDevices.put(device, reportingDevice);
        } catch (Exception e) {
          throw new RuntimeException("While loading device " + device, e);
        }
      }
      System.out.println("Loaded " + expectedDevices.size() + " expected devices");
    } catch (Exception e) {
      throw new RuntimeException(
          "While loading devices directory " + devicesDir.getAbsolutePath(), e);
    }
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

  private void validatePubSub(String instName) {
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
    dataSink = new FirestoreDataSink(projectId);
    System.out.println("Results will be uploaded to " + dataSink.getViewUrl());
    OUT_BASE_FILE.mkdirs();
    System.out.println("Also found in such directories as " + OUT_BASE_FILE.getAbsolutePath());
    System.out.println("Generating report file in " + METADATA_REPORT_FILE.getAbsolutePath());
    PubSubClient client = new PubSubClient(projectId, instName);
    System.out.println("Entering pubsub message loop on " + client.getSubscriptionId());
    while(client.isActive()) {
      try {
        client.processMessage(
            (message, attributes) -> validateMessage(schemaMap, message, attributes));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    System.out.println("Message loop complete");
  }

  private Set<String> convertIgnoreSet(String ignoreSpec) {
    if (ignoreSpec == null) {
      return ImmutableSet.of();
    }
    return Arrays.stream(ignoreSpec.split(",")).collect(Collectors.toSet());
  }

  private void validateMessage(Map<String, Schema> schemaMap, Map<String, Object> message,
      Map<String, String> attributes) {
    if (validateUpdate(schemaMap, message, attributes)) {
      writeDeviceMetadataReport();
    }
    if (STATE_SUBFOLER.equals(attributes.get(SUB_FOLDER_KEY))) {
      if (!checkStateResponse(message, attributes)) {
        sendConfigUpdate(attributes.get(DEVICE_ID_KEY));
      }
    }
  }

  private boolean checkStateResponse(Map<String, Object> message, Map<String, String> attributes) {
    System.out.println("Handling state response");
    return false;
  }

  private boolean validateUpdate(Map<String, Schema> schemaMap, Map<String, Object> message,
            Map<String, String> attributes) {

    String registryId = attributes.get(DEVICE_REGISTRY_ID_KEY);
    if (cloudIotConfig != null && !cloudIotConfig.registry_id.equals(registryId)) {
      if (ignoredRegistries.add(registryId)) {
        System.out.println("Ignoring data for not-configured registry " + registryId);
      }
      return false;
    }

    try {
      String deviceId = attributes.get("deviceId");
      Preconditions.checkNotNull(deviceId, "Missing deviceId in message");

      String subFolder = attributes.get("subFolder");

      if (Strings.isNullOrEmpty(subFolder)) {
        subFolder = UNKNOWN_SCHEMA_DEFAULT;
      }

      if (expectedDevices.containsKey(deviceId)) {
        processedDevices.add(deviceId);
      }

      final ReportingDevice reportingDevice = getReportingDevice(deviceId);
      if (!reportingDevice.markMessageType(subFolder)) {
        return false;
      }

      System.out.println(String.format("Processing device #%d/%d: %s/%s",
          processedDevices.size(), expectedDevices.size(), deviceId, subFolder));

      if (attributes.get("wasBase64").equals("true")) {
        base64Devices.add(deviceId);
      }

      File deviceDir = new File(OUT_BASE_FILE, String.format(DEVICE_FILE_FORMAT, deviceId));
      deviceDir.mkdirs();

      File attributesFile = new File(deviceDir, String.format(ATTRIBUTE_FILE_FORMAT, subFolder));
      OBJECT_MAPPER.writeValue(attributesFile, attributes);

      File messageFile = new File(deviceDir, String.format(MESSAGE_FILE_FORMAT, subFolder));
      OBJECT_MAPPER.writeValue(messageFile, message);

      File errorFile = new File(deviceDir, String.format(ERROR_FILE_FORMAT, subFolder));

      try {
        if (!schemaMap.containsKey(subFolder)) {
          throw new IllegalArgumentException(String.format(SCHEMA_SKIP_FORMAT, subFolder, deviceId));
        }
      } catch (Exception e) {
        System.out.println(e.getMessage());
        OBJECT_MAPPER.writeValue(errorFile, e.getMessage());
        reportingDevice.addError(e);
      }

      try {
        validateMessage(schemaMap.get(ENVELOPE_SCHEMA_ID), attributes);
        validateDeviceId(deviceId);
      } catch (ExceptionMap | ValidationException e) {
        processViolation(message, attributes, deviceId, ENVELOPE_SCHEMA_ID, attributesFile, errorFile, e);
        reportingDevice.addError(e);
      }

      if (schemaMap.containsKey(subFolder)) {
        try {
          validateMessage(schemaMap.get(subFolder), message);
          dataSink.validationResult(deviceId, subFolder, attributes, message, null);
        } catch (ExceptionMap | ValidationException e) {
          processViolation(message, attributes, deviceId, subFolder, messageFile, errorFile, e);
          reportingDevice.addError(e);
        }
      }

      boolean updated = false;

      if (expectedDevices.isEmpty()) {
        // No devices configured, so don't check metadata.
        updated = false;
      } else if (expectedDevices.containsKey(deviceId)) {
        try {
          if (POINTSET_TYPE.equals(subFolder)) {
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
        System.out.println(String.format("Validation complete %s/%s", deviceId, subFolder));
      }

      return updated;
    } catch (Exception e){
      e.printStackTrace();
      return false;
    }
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
      throw new RuntimeException("While generating metadata report file " + METADATA_REPORT_FILE.getAbsolutePath(), e);
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
      String deviceId, String schemaId, File inputFile, File errorFile, RuntimeException e)
      throws FileNotFoundException {
    System.out.println("Error validating " + inputFile + ": " + e.getMessage());
    ErrorTree errorTree = ExceptionMap.format(e, ERROR_FORMAT_INDENT);
    dataSink.validationResult(deviceId, schemaId, attributes, message, errorTree);
    try (PrintStream errorOut = new PrintStream(errorFile)) {
      errorTree.write(errorOut);
    }
  }

  private void sendConfigUpdate(String deviceId) {
    Config config = getCurrentConfig(deviceId);
    config.timestamp = new Date();
    try {
      cloudIotManager.setDeviceConfig(deviceId, OBJECT_MAPPER.writeValueAsString(config));
    } catch (Exception e) {
      throw new RuntimeException("While setting device config for " + deviceId);
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
      throw new ExceptionMap(String.format(DEVICE_MATCH_FORMAT, deviceId, DEVICE_ID_PATTERN.pattern()));
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
            System.out.println("Validating " + targetFile.getName() + " against " + schemaFile.getName());
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
      SchemaLoader loader = SchemaLoader.builder().schemaJson(rawSchema).httpClient(new RelativeClient()).build();
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
        String new_url = FILE_URL_PREFIX + new File(schemaRoot, url.substring(FILE_URL_PREFIX.length()));
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


}
