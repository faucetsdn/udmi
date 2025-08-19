package com.google.daq.mqtt.registrar;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.registrar.Registrar.DEVICE_ERRORS_MAP;
import static com.google.daq.mqtt.registrar.Registrar.ENVELOPE_SCHEMA_JSON;
import static com.google.daq.mqtt.registrar.Registrar.METADATA_SCHEMA_JSON;
import static com.google.daq.mqtt.util.ConfigManager.GENERATED_CONFIG_JSON;
import static com.google.daq.mqtt.util.ConfigManager.configFrom;
import static com.google.udmi.util.Common.DEVICE_ID_ALLOWABLE;
import static com.google.udmi.util.Common.POINT_NAME_ALLOWABLE;
import static com.google.udmi.util.ContextWrapper.runInContext;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.OBJECT_MAPPER_STRICT;
import static com.google.udmi.util.GeneralUtils.catchToFalse;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.compressJsonString;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotTrueThen;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.writeString;
import static com.google.udmi.util.JsonUtil.OBJECT_MAPPER;
import static com.google.udmi.util.JsonUtil.getNowInstant;
import static com.google.udmi.util.JsonUtil.loadFile;
import static com.google.udmi.util.JsonUtil.unquoteJson;
import static com.google.udmi.util.SiteModel.CLOUD_MODEL_FILE;
import static com.google.udmi.util.SiteModel.METADATA_JSON;
import static com.google.udmi.util.SiteModel.NORMALIZED_JSON;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.daq.mqtt.util.CloudDeviceSettings;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ConfigManager;
import com.google.daq.mqtt.util.DeviceExceptionManager;
import com.google.udmi.util.ErrorMap;
import com.google.udmi.util.ErrorMap.ErrorMapException;
import com.google.udmi.util.ExceptionMap;
import com.google.udmi.util.ExceptionMap.ErrorTree;
import com.google.udmi.util.ExceptionMap.ExceptionCategory;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.MessageDowngrader;
import com.google.udmi.util.MessageValidator;
import com.google.udmi.util.SiteModel;
import com.google.udmi.util.SiteModel.MetadataException;
import com.google.udmi.util.ValidationError;
import com.google.udmi.util.ValidationException;
import com.google.udmi.util.ValidationWarning;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Auth_type;
import udmi.schema.Config;
import udmi.schema.Credential;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.GatewayModel;
import udmi.schema.Metadata;
import udmi.schema.PointPointsetModel;


class LocalDevice {

  public static final String INVALID_METADATA_HASH = "INVALID";
  private static final String RSA_PUBLIC_PEM = "rsa_public.pem";
  private static final String RSA2_PUBLIC_PEM = "rsa2_public.pem";
  private static final String RSA3_PUBLIC_PEM = "rsa3_public.pem";
  private static final String RSA_CERT_PEM = "rsa_cert.pem";
  private static final String RSA_PRIVATE_PEM = "rsa_private.pem";
  private static final String RSA_PRIVATE_PKCS8 = "rsa_private.pkcs8";
  private static final String RSA_PRIVATE_CRT = "rsa_private.crt";
  private static final String RSA_PRIVATE_CSR = "rsa_private.csr";
  private static final String ES_PUBLIC_PEM = "ec_public.pem";
  private static final String ES2_PUBLIC_PEM = "ec2_public.pem";
  private static final String ES3_PUBLIC_PEM = "ec3_public.pem";
  private static final String ES_CERT_PEM = "ec_cert.pem";
  private static final String ES_PRIVATE_PEM = "ec_private.pem";
  private static final String ES_PRIVATE_PKCS8 = "ec_private.pkcs8";
  private static final String EC_PRIVATE_CRT = "ec_private.crt";
  private static final String EC_PRIVATE_CSR = "ec_private.csr";
  private static final String RSA_AUTH_TYPE = Auth_type.RS_256.toString();
  private static final String RSA_CERT_TYPE = Auth_type.RS_256_X_509.toString();
  private static final String ES_AUTH_TYPE = Auth_type.ES_256.toString();
  private static final String ES_CERT_TYPE = Auth_type.ES_256_X_509.toString();
  private static final Map<String, String> PRIVATE_PKCS8_MAP =
      ImmutableMap.of(
          RSA_AUTH_TYPE, RSA_PRIVATE_PKCS8,
          RSA_CERT_TYPE, RSA_PRIVATE_PKCS8,
          ES_AUTH_TYPE, ES_PRIVATE_PKCS8,
          ES_CERT_TYPE, ES_PRIVATE_PKCS8);
  private static final String SAMPLES_DIR = "samples";
  private static final String ADJUNCT_DIR = "adjunct";
  private static final String CONFIG_DIR = "config";
  private static final String OUT_DIR = "out";
  private static final String EXPECTED_DIR = "expected";
  private static final String EXCEPTION_LOG_FILE = "exceptions.txt";
  private static final Set<String> DEVICE_FILES = ImmutableSet.of(METADATA_JSON);
  private static final Set<String> RSA_PRIVATE_KEY_FILES =
      ImmutableSet.of(RSA_PRIVATE_PEM, RSA_PRIVATE_PKCS8);
  private static final Set<String> ES_PRIVATE_KEY_FILES =
      ImmutableSet.of(ES_PRIVATE_PEM, ES_PRIVATE_PKCS8);
  private static final Map<String, Set<String>> PRIVATE_KEY_FILES_MAP =
      ImmutableMap.of(
          RSA_AUTH_TYPE, RSA_PRIVATE_KEY_FILES,
          RSA_CERT_TYPE, RSA_PRIVATE_KEY_FILES,
          ES_AUTH_TYPE, ES_PRIVATE_KEY_FILES,
          ES_CERT_TYPE, ES_PRIVATE_KEY_FILES);
  private static final Map<String, String> PUBLIC_KEY_FILE_MAP =
      ImmutableMap.of(
          RSA_AUTH_TYPE, RSA_PUBLIC_PEM,
          RSA_CERT_TYPE, RSA_PUBLIC_PEM,
          ES_AUTH_TYPE, ES_PUBLIC_PEM,
          ES_CERT_TYPE, ES_PUBLIC_PEM);
  private static final Map<String, String> CERT_FILE_MAP =
      ImmutableMap.of(
          RSA_CERT_TYPE, RSA_CERT_PEM,
          ES_CERT_TYPE, ES_CERT_PEM);
  private static final Set<String> OPTIONAL_FILES =
      ImmutableSet.of(
          RSA_PRIVATE_CRT,
          RSA_PRIVATE_CSR,
          RSA2_PUBLIC_PEM,
          RSA3_PUBLIC_PEM,
          EC_PRIVATE_CRT,
          EC_PRIVATE_CSR,
          ES2_PUBLIC_PEM,
          ES3_PUBLIC_PEM,
          SAMPLES_DIR,
          ADJUNCT_DIR,
          EXPECTED_DIR,
          CONFIG_DIR,
          OUT_DIR);
  private static final Set<String> OUT_FILES = ImmutableSet.of(
      GENERATED_CONFIG_JSON, DEVICE_ERRORS_MAP, NORMALIZED_JSON, EXCEPTION_LOG_FILE);
  private static final Set<String> ALL_KEY_FILES =
      ImmutableSet.of(
          RSA_PUBLIC_PEM,
          RSA2_PUBLIC_PEM,
          RSA3_PUBLIC_PEM,
          ES_PUBLIC_PEM,
          ES2_PUBLIC_PEM,
          ES3_PUBLIC_PEM);
  private static final Set<String> ALL_CERT_FILES = ImmutableSet.of(RSA_CERT_PEM, ES_CERT_PEM);
  private static final int MAX_JSON_LENGTH = 32767;
  private final String deviceId;
  private final Map<String, JsonSchema> schemas;
  private final File deviceDir;
  private final File outDir;
  private final DeviceKind deviceKind;
  private final Metadata metadata;
  private final ExceptionMap exceptionMap;
  private final String generation;
  private final List<Credential> deviceCredentials = new ArrayList<>();
  private ConfigManager config;
  private final DeviceExceptionManager exceptionManager;
  private final SiteModel siteModel;

  private String deviceNumId;

  private CloudDeviceSettings settings;
  private String baseVersion;
  private Date lastActive;
  private boolean blocked;
  private CloudModel cloudModel;
  private Instant lastUpdated;

  LocalDevice(
      SiteModel siteModel, String deviceId, Map<String, JsonSchema> schemas,
      String generation, DeviceKind kind) {
    try {
      this.deviceId = deviceId;
      this.schemas = schemas;
      this.generation = generation;
      this.siteModel = siteModel;
      this.deviceKind = kind;
      if (!DEVICE_ID_ALLOWABLE.matcher(deviceId).matches()) {
        throw new ValidationError(format("Device id does not match allowable pattern %s",
            DEVICE_ID_ALLOWABLE.pattern()));
      }
      exceptionMap = new ExceptionMap("Exceptions for " + deviceId);
      deviceDir = kind != DeviceKind.EXTRA
          ? siteModel.getDeviceDir(deviceId) : siteModel.getExtraDir(deviceId);
      outDir = new File(deviceDir, OUT_DIR);
      exceptionManager = new DeviceExceptionManager(new File(siteModel.getSitePath()));
      metadata = readMetadata();
    } catch (Exception e) {
      throw new RuntimeException("While loading local device " + deviceId, e);
    }
  }

  public void initialize() {
    prepareOutDir();
    ifTrueThen(deviceKind == DeviceKind.LOCAL && metadata != null, this::validateMetadata);
    configure();
  }

  void configure() {
    if (config == null) {
      config = configFrom(metadata, deviceId, siteModel);
      ifTrueThen(deviceKind == DeviceKind.EXTRA, this::loadExtraCloudModel);
    }
  }

  public static void parseMetadataValidateProcessingReport(ProcessingReport report)
      throws ValidationException {
    if (report.isSuccess()) {
      return;
    }

    for (ProcessingMessage msg : report) {
      if (msg.getLogLevel().compareTo(LogLevel.ERROR) >= 0) {
        throw MessageValidator.fromProcessingReport(report);
      }
    }
  }

  private void prepareOutDir() {
    if (!outDir.exists()) {
      outDir.mkdirs();
    }
    new File(outDir, EXCEPTION_LOG_FILE).delete();
  }

  public void validateExpectedFiles() {
    if (isExtraKind()) {
      return;
    }

    ExceptionMap exceptionMap = new ExceptionMap("expected files mismatch");

    String[] files = deviceDir.list();
    checkNotNull(files, "No files found in " + deviceDir.getAbsolutePath());
    Set<String> actualFiles = ImmutableSet.copyOf(files);
    Set<String> expectedFiles = Sets.union(DEVICE_FILES, keyFiles());
    SortedSet<String> missing = new TreeSet<>(Sets.difference(expectedFiles, actualFiles));
    if (!missing.isEmpty()) {
      exceptionMap.put(ExceptionCategory.missing,
          new RuntimeException("Missing files: " + missing));
    }
    SortedSet<String> extra = new TreeSet<>(
        Sets.difference(Sets.difference(actualFiles, expectedFiles), OPTIONAL_FILES));
    if (!extra.isEmpty()) {
      exceptionMap.put(ExceptionCategory.extra, new RuntimeException("Extra files: " + extra));
    }
    String[] outFiles = outDir.list();
    if (outFiles != null) {
      Set<String> outSet = ImmutableSet.copyOf(outFiles);
      SortedSet<String> extraOut = new TreeSet<>(Sets.difference(outSet, OUT_FILES));
      if (!extraOut.isEmpty()) {
        exceptionMap.put(ExceptionCategory.out,
            new RuntimeException("Extra out files: " + extraOut));
      }
    }

    exceptionMap.throwIfNotEmpty();
  }

  private void validateMetadata() {
    try {
      extraValidation(metadata);  // Do this first so it will always be called.
      JsonNode metadataObject = JsonUtil.convertTo(JsonNode.class, metadata);
      ProcessingReport report = schemas.get(METADATA_SCHEMA_JSON).validate(metadataObject);
      parseMetadataValidateProcessingReport(report);
    } catch (ProcessingException | ValidationException e) {
      exceptionMap.put(ExceptionCategory.validation, e);
    }
  }

  private void extraValidation(Metadata metadataObject) {
    HashMap<String, PointPointsetModel> points = catchToNull(() -> metadataObject.pointset.points);
    Set<String> pointNameErrors = ifNotNullGet(points, p -> p.keySet().stream()
        .filter(key -> !POINT_NAME_ALLOWABLE.matcher(key).matches()).collect(Collectors.toSet()));
    if (pointNameErrors != null && !pointNameErrors.isEmpty()) {
      throw new ValidationError(format("Found point names not matching allowed pattern %s: %s",
          POINT_NAME_ALLOWABLE.pattern(), CSV_JOINER.join(pointNameErrors)));
    }
  }

  private Metadata readMetadata() {
    try {
      Metadata deviceMetadata = siteModel.loadDeviceMetadata(deviceId);
      if (deviceMetadata instanceof MetadataException metadataException) {
        throw new RuntimeException("Loading " + metadataException.file.getAbsolutePath(),
            metadataException.exception);
      }
      baseVersion = ofNullable(deviceMetadata.upgraded_from).orElse(deviceMetadata.version);
      return deviceMetadata;
    } catch (Exception exception) {
      exceptionMap.put(ExceptionCategory.loading, exception);
      return null;
    }
  }

  private Metadata readNormalized() {
    try {
      File metadataFile = new File(outDir, NORMALIZED_JSON);
      return OBJECT_MAPPER_STRICT.readValue(metadataFile, Metadata.class);
    } catch (Exception e) {
      return new Metadata();
    }
  }

  private String metadataHash() {
    if (metadata == null) {
      return INVALID_METADATA_HASH;
    }
    String savedHash = metadata.hash;
    Date savedTimestamp = metadata.timestamp;
    try {
      metadata.hash = null;
      metadata.timestamp = null;
      String json = deviceMetadataString();
      return format("%08x", Objects.hash(json));
    } catch (Exception e) {
      throw new RuntimeException("Converting object to string", e);
    } finally {
      metadata.hash = savedHash;
      metadata.timestamp = savedTimestamp;
    }
  }

  private boolean hasAuthType() {
    return metadata.cloud != null && metadata.cloud.auth_type != null;
  }

  private String getAuthType() {
    return metadata.cloud == null ? null
        : metadata.cloud.auth_type == null ? null : metadata.cloud.auth_type.value();
  }

  private boolean isDeviceKeySource() {
    return metadata != null && (metadata.cloud != null && isTrue(metadata.cloud.device_key));
  }

  public void loadCredentials() {
    try {
      deviceCredentials.clear();
      if (metadata == null) {
        return;
      }
      if (isProxied() && hasAuthType()) {
        throw new RuntimeException("Proxied devices should not have cloud.auth_type defined");
      }
      if (!hasCloudConnection()) {
        return;
      }
      if (!hasAuthType()) {
        throw new RuntimeException("Credential cloud.auth_type definition missing");
      }
      String authType = getAuthType();
      Set<String> keyFiles = (authType.equals(ES_CERT_TYPE) || authType.equals(RSA_CERT_TYPE))
          ? ALL_CERT_FILES : ALL_KEY_FILES;
      for (String keyFile : keyFiles) {
        Credential deviceCredential = getDeviceCredential(keyFile);
        if (deviceCredential != null) {
          deviceCredentials.add(deviceCredential);
        }
      }
      int numCredentials = deviceCredentials.size();
      if (numCredentials == 0 || numCredentials > 3) {
        throw new RuntimeException(format("Found %d credentials", numCredentials));
      }
    } catch (Exception e) {
      throw new RuntimeException("While loading credentials for local device " + deviceId, e);
    }
  }

  private Credential getDeviceCredential(String keyFile) throws IOException {
    File deviceKeyFile = new File(deviceDir, keyFile);
    if (!deviceKeyFile.exists()) {
      return null;
    }
    return CloudIotManager.makeCredential(
        getAuthType(),
        IOUtils.toString(new FileInputStream(deviceKeyFile), Charset.defaultCharset()));
  }

  private Set<String> keyFiles() {
    if (!isGateway() && !isDirect()) {
      return ImmutableSet.of();
    }
    String authType = getAuthType();
    Set<String> certFile = getCertFiles();
    String keyFile = getPublicKeyFile();
    Set<String> publicKeyFiles = keyFile != null ? Set.of(keyFile) : Set.of();
    Set<String> privateKeyFiles = getPrivateKeyFiles();
    SetView<String> combined = Sets.union(publicKeyFiles, privateKeyFiles);
    boolean addCertFile =
        authType != null && (authType.equals(ES_CERT_TYPE) || authType.equals(
            RSA_CERT_TYPE));
    return addCertFile ? Sets.union(combined, certFile) : combined;
  }

  private Set<String> getPrivateKeyFiles() {
    if (isDeviceKeySource() || !hasAuthType()) {
      return Set.of();
    }
    return PRIVATE_KEY_FILES_MAP.get(getAuthType());
  }

  private String getPublicKeyFile() {
    if (!hasAuthType()) {
      return null;
    }
    return PUBLIC_KEY_FILE_MAP.get(getAuthType());
  }

  private Set<String> getCertFiles() {
    if (isDeviceKeySource() || !hasAuthType()) {
      return Set.of();
    }
    String authType = getAuthType();
    return (authType.equals(ES_CERT_TYPE) || authType.equals(
        RSA_CERT_TYPE))
        ? Set.of(CERT_FILE_MAP.get(getAuthType()))
        : Set.of();
  }

  boolean hasCloudConnection() {
    return isDirect() || isGateway();
  }

  boolean isDirect() {
    return config != null && config.isDirect();
  }

  boolean isVirtual() {
    return config != null && config.isVirtual();
  }

  boolean isGateway() {
    return config != null && config.isGateway();
  }

  boolean isProxied() {
    return isExtraKind() ? getGatewayId() != null
        : config != null && config.isProxied();
  }

  private void loadExtraCloudModel() {
    cloudModel = loadFile(CloudModel.class,
        new File(siteModel.getExtraDir(deviceId), CLOUD_MODEL_FILE));
  }

  private boolean isExtraKind() {
    return deviceKind == DeviceKind.EXTRA;
  }

  String getGatewayId() {
    GatewayModel gatewayModel = isExtraKind() ? cloudModel.gateway : metadata.gateway;
    return ifNotNullGet(gatewayModel, model -> model.gateway_id);
  }

  CloudDeviceSettings getSettings() {
    return checkNotNull(settings, "Device settings not initialized");
  }

  void initializeSettings() {
    try {
      settings = new CloudDeviceSettings();
      settings.credentials = deviceCredentials;
      settings.generation = generation;
      settings.blocked = deviceKind == DeviceKind.EXTRA;
      settings.proxyDevices = getProxyDevicesList();
      settings.deviceNumId = findDeviceNumId();

      if (metadata == null) {
        return;
      }

      settings.updated = config.getUpdatedTimestamp();
      settings.metadata = deviceMetadataString();
      settings.keyAlgorithm = getAuthType();
      settings.keyBytes = getKeyBytes();
      settings.config = deviceConfigString();
    } catch (Exception e) {
      captureError(ExceptionCategory.initializing, e);
    }
  }

  private String findDeviceNumId() {
    return isExtraKind() ? cloudModel.num_id : catchToNull(() -> metadata.cloud.num_id);
  }

  private List<String> getProxyDevicesList() {
    return isExtraKind() ? getCloudModelProxyList()
        : ifNotNullGet(config, ConfigManager::getProxyDevicesList);
  }

  private List<String> getCloudModelProxyList() {
    return catchToNull(() -> cloudModel.gateway.proxy_ids);
  }

  public void updateModel(CloudModel device) {
    setDeviceNumId(checkNotNull(device.num_id, "missing deviceNumId for " + deviceId));
    setLastActive(device.last_event_time);
    setLastUpdated(getNowInstant());
  }

  private void setLastUpdated(Instant lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  public String getLastActive() {
    return JsonUtil.isoConvert(lastActive);
  }

  private void setLastActive(Date lastEventTime) {
    this.lastActive = lastEventTime;
  }

  public byte[] getKeyBytes() {
    if (!hasCloudConnection()) {
      return null;
    }
    String keyFile = PRIVATE_PKCS8_MAP.get(getAuthType());
    if (keyFile == null) {
      throw new RuntimeException("Invalid auth type " + getAuthType());
    }
    File keyBytesFile = new File(deviceDir, keyFile);
    if (!keyBytesFile.exists()) {
      return null;
    }
    return getFileBytes(keyBytesFile.getAbsolutePath());
  }

  private byte[] getFileBytes(String dataFile) {
    Path dataPath = Paths.get(dataFile);
    try {
      return Files.readAllBytes(dataPath);
    } catch (Exception e) {
      throw new RuntimeException("While getting data from " + dataPath.toAbsolutePath(), e);
    }
  }

  private String deviceConfigString() {
    return runInContext("While converting device config", () -> {
      Object fromValue = config.deviceConfigJson();
      captureError(ExceptionCategory.schema, config.warningsAsException());

      ifNotNullThen(config.getSchemaViolationsMap(), map -> ifNotTrueThen(map.isEmpty(), () -> {
        ErrorMap schemaValidationErrors = new ErrorMap("schema validation errors");
        schemaValidationErrors.putAll(map);
        captureError(ExceptionCategory.schema, schemaValidationErrors.asException());
      }));

      if (fromValue instanceof String stringValue) {
        return stringValue;
      }
      JsonNode configJson = OBJECT_MAPPER_STRICT.valueToTree(fromValue);
      if (config.shouldBeDowngraded()) {
        new MessageDowngrader("config", configJson, metadata).downgrade(baseVersion);
      }
      return compressJsonString(configJson, MAX_JSON_LENGTH);
    });
  }

  private String deviceMetadataString() {
    try {
      return compressJsonString(metadata, MAX_JSON_LENGTH);
    } catch (Exception e) {
      throw new RuntimeException("While converting metadata to string", e);
    }
  }

  public void validateEnvelope(String registryId, String siteName) {
    try {
      // Create a fake envelope just to validate registryId and siteName fields.
      Envelope envelope = new Envelope();
      envelope.deviceId = deviceId;
      envelope.deviceRegistryId = registryId;
      envelope.subFolder = SubFolder.POINTSET;
      envelope.subType = SubType.EVENTS;
      // Don't use actual project id because it should be abstracted away.
      envelope.projectId = fakeProjectId();
      envelope.deviceNumId = makeNumId(envelope);
      String envelopeJson = OBJECT_MAPPER_STRICT.writeValueAsString(envelope);
      ProcessingReport processingReport = schemas.get(ENVELOPE_SCHEMA_JSON)
          .validate(OBJECT_MAPPER.readTree(envelopeJson));
      if (!processingReport.isSuccess()) {
        processingReport.forEach(action -> {
          throw new RuntimeException("Against envelope schema", action.asException());
        });
      }
    } catch (Exception e) {
      throw new IllegalStateException("Validating envelope " + deviceId, e);
    }

    checkConsistency(siteName);
  }

  private String fakeProjectId() {
    if (metadata != null && metadata.system != null && metadata.system.location != null
        && metadata.system.location.site != null) {
      return metadata.system.location.site.toLowerCase();
    } else {
      return "unknown";
    }
  }

  private void checkConsistency(String expectedSite) {
    if (metadata == null || metadata.system == null) {
      return;
    }
    if (metadata.system.physical_tag != null) {
      String assetName = metadata.system.physical_tag.asset.name;
      checkState(deviceId.equals(assetName),
          format("system.physical_tag.asset.name %s does not match expected %s", assetName,
              deviceId));

      String assetSite = metadata.system.physical_tag.asset.site;
      checkState(expectedSite.equals(assetSite),
          format(
              "system.physical_tag.asset.site %s does not match expected %s",
              assetSite, expectedSite));
    }

    if (metadata.system.location != null) {
      String siteName = metadata.system.location.site;
      checkState(expectedSite.equals(siteName),
          format(
              "system.location.site %s does not match expected %s", siteName, expectedSite));
    }
  }

  private String makeNumId(Envelope envelope) {
    int hash = Objects.hash(deviceId, envelope.deviceRegistryId, envelope.projectId);
    return Integer.toString(hash < 0 ? -hash : hash);
  }

  public void writeErrors() {
    ErrorTree errorTree = getErrorTree();
    File errorsFile = new File(outDir, DEVICE_ERRORS_MAP);
    if (errorTree != null) {
      try (PrintStream printStream = new PrintStream(Files.newOutputStream(errorsFile.toPath()))) {
        System.err.println("Updating errors " + errorsFile);
        errorTree.write(printStream);
      } catch (Exception e) {
        throw new RuntimeException("While writing " + errorsFile.getAbsolutePath(), e);
      }
    } else if (errorsFile.exists()) {
      System.err.println("Removing " + errorsFile);
      errorsFile.delete();
    }
  }

  private ErrorTree getErrorTree() {
    return getErrorTree(exceptionManager.forDevice(getDeviceId()));
  }

  ErrorTree getErrorTree(List<Pattern> ignoreErrors) {
    if (exceptionMap.isEmpty()) {
      return null;
    }
    ErrorTree errorTree = ExceptionMap.format(exceptionMap);
    return errorTree.purge(ignoreErrors) ? null : errorTree;
  }

  public boolean hasErrors() {
    return getErrorTree() != null;
  }

  String getNormalizedTimestamp() {
    return JsonUtil.getTimestampString(metadata.timestamp);
  }

  void writeNormalized() {
    try {
      validateConsistency();
    } catch (Exception e) {
      exceptionMap.put(ExceptionCategory.validation, e);
    }

    File metadataFile = new File(outDir, NORMALIZED_JSON);
    if (metadata == null) {
      System.err.println("Deleting (invalid) " + metadataFile.getAbsolutePath());
      metadataFile.delete();
      return;
    }
    metadata.timestamp = metadata.timestamp != null ? metadata.timestamp : new Date();
    Metadata normalized = readNormalized();
    String metadataHash = metadataHash();
    if (normalized.hash != null && normalized.hash.equals(metadataHash)) {
      metadata.timestamp = normalized.timestamp;
      return;
    }
    metadata.hash = metadataHash;
    System.err.println("Writing normalized " + metadataFile.getAbsolutePath());
    try {
      writeString(metadataFile, compressJsonString(metadata, MAX_JSON_LENGTH));
    } catch (Exception e) {
      exceptionMap.put(ExceptionCategory.writing, e);
    }
  }

  private void validateConsistency() {
    checkState(!(isProxied() && isGateway()), "device is both proxy and gateway");
    boolean hasProxyIds = catchToFalse(() -> metadata.gateway.proxy_ids != null);
    checkState(hasProxyIds == isGateway(), "gateway has no proxies");
    boolean hasGatewayId = catchToFalse(() -> metadata.gateway.gateway_id != null);
    checkState(hasGatewayId == isProxied(), "proxy has no gateway");

    Set<String> types = new HashSet<>();
    ifTrueThen(isProxied(), () -> types.add("proxied"));
    ifTrueThen(isGateway(), () -> types.add("gateway"));
    ifTrueThen(isDirect(), () -> types.add("direct"));
    ifTrueThen(isVirtual(), () -> types.add("virtual"));
    checkState(types.size() == 1, "Bad device classification: " + types);
  }

  /**
   * Write device config to the generated_config.json file
   */
  public void writeConfigFile() {
    File configFile = new File(outDir, GENERATED_CONFIG_JSON);
    configFile.delete();

    String config = unquoteJson(getSettings().config);

    if (config != null) {
      try (OutputStream outputStream = Files.newOutputStream(configFile.toPath())) {
        outputStream.write(config.getBytes());
      } catch (Exception e) {
        throw new RuntimeException("While writing " + configFile.getAbsolutePath(), e);
      }
    }
  }

  public String getDeviceId() {
    return deviceId;
  }

  public String getDeviceNumId() {
    return checkNotNull(getDeviceNumIdRaw(), "deviceNumId not set");
  }

  public void setDeviceNumId(String numId) {
    checkState(deviceNumId == null || deviceNumId.equals(numId),
        format("deviceNumId %s != %s", numId, deviceNumId));
    deviceNumId = numId;
  }

  public String getDeviceNumIdRaw() {
    return deviceNumId;
  }

  public DeviceStatus getStatus() {
    if (blocked) {
      return DeviceStatus.BLOCKED;
    }
    if (metadata == null) {
      return DeviceStatus.INVALID;
    }
    if (getTreeChildren().isEmpty()) {
      return DeviceStatus.CLEAN;
    }
    return DeviceStatus.ERROR;
  }

  public void captureError(ExceptionCategory exceptionType, Exception exception) {
    if (exception == null) {
      return;
    }

    exceptionMap.put(exceptionType, exception);
    File exceptionLog = new File(outDir, EXCEPTION_LOG_FILE);
    try {
      try (FileWriter fileWriter = new FileWriter(exceptionLog, true);
          PrintWriter printWriter = new PrintWriter(fileWriter)) {
        printWriter.println(exceptionType);
        exception.printStackTrace(printWriter);
        if (exception instanceof ExceptionMap exceptionMap) {
          exceptionMap.forEach(mapped -> mapped.printStackTrace(printWriter));
        } else if (exception instanceof ErrorMapException errorMap) {
          errorMap.getMap().forEach((message, ex) -> ex.printStackTrace(printWriter));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Writing exception log file " + exceptionLog.getAbsolutePath(), e);
    }
  }

  public boolean isValid() {
    return metadata != null || deviceKind == DeviceKind.EXTRA;
  }

  public void validateSamples() {
    File samplesDir = new File(deviceDir, SAMPLES_DIR);
    if (!samplesDir.exists()) {
      return;
    }
    // TODO: Remove this once it's been out there for a while, deprecated 2025/02/03.
    throw new RuntimeException("Deprecated samples/ directory: " + samplesDir.getAbsolutePath());
  }

  public Set<Entry<String, ErrorTree>> getTreeChildren() {
    ErrorTree errorTree = getErrorTree();
    if (errorTree != null && errorTree.children != null) {
      return errorTree.children.entrySet();
    }
    return Set.of();
  }

  public Metadata getMetadata() {
    return metadata;
  }

  public Config deviceConfigObject() {
    return config.deviceConfig();
  }

  public void setBlocked(boolean blocked) {
    this.blocked = blocked;
  }

  public LocalDevice duplicate(String newId) {
    return new LocalDevice(siteModel, newId, schemas, generation, deviceKind);
  }

  public void preprocessMetadata() {
    ifTrueWarn(catchToNull(() -> metadata.cloud.config.static_file) != null,
        "Disallowed cloud.config.static_file defined");
  }

  private void ifTrueWarn(boolean condition, String message) {
    ifTrueThen(condition && siteModel.getStrictWarnings(),
        () -> captureError(ExceptionCategory.metadata, new ValidationWarning(message)));
  }

  public boolean hasCategory(ExceptionCategory category) {
    return exceptionMap.hasCategory(category);
  }

  public List<String> getProxyIds() {
    return getMetadata().gateway.proxy_ids;
  }

  public enum DeviceStatus {
    CLEAN,
    ERROR,
    INVALID,
    BLOCKED
  }

  enum DeviceKind {
    LOCAL, SIMPLE, EXTRA
  }
}
