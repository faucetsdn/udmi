package com.google.daq.mqtt.registrar;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.registrar.Registrar.DEVICE_ERRORS_MAP;
import static com.google.daq.mqtt.registrar.Registrar.ENVELOPE_JSON;
import static com.google.daq.mqtt.registrar.Registrar.GENERATED_CONFIG_JSON;
import static com.google.daq.mqtt.registrar.Registrar.METADATA_JSON;
import static com.google.daq.mqtt.registrar.Registrar.NORMALIZED_JSON;
import static com.google.udmi.util.Common.VERSION_KEY;
import static com.google.udmi.util.GeneralUtils.OBJECT_MAPPER_STRICT;
import static com.google.udmi.util.GeneralUtils.compressJsonString;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.writeString;
import static com.google.udmi.util.JsonUtil.OBJECT_MAPPER;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.MessageUpgrader.METADATA_SCHEMA;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.google.daq.mqtt.util.ExceptionMap;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import com.google.daq.mqtt.util.ValidationException;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.MessageDowngrader;
import com.google.udmi.util.MessageUpgrader;
import com.google.udmi.util.SiteModel;
import com.google.udmi.util.SiteModel.MetadataException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import udmi.schema.CloudModel.Auth_type;
import udmi.schema.Config;
import udmi.schema.Credential;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.GatewayConfig;
import udmi.schema.LocalnetConfig;
import udmi.schema.Metadata;
import udmi.schema.Operation;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointsetConfig;
import udmi.schema.SystemConfig;

class LocalDevice {

  public static final String INVALID_METADATA_HASH = "INVALID";
  public static final String EXCEPTION_INITIALIZING = "Initializing";
  public static final String EXCEPTION_VALIDATING = "Validating";
  public static final String EXCEPTION_CONVERTING = "Converting";
  public static final String EXCEPTION_LOADING = "Loading";
  public static final String EXCEPTION_READING = "Reading";
  public static final String EXCEPTION_WRITING = "Writing";
  public static final String EXCEPTION_FILES = "Files";
  public static final String EXCEPTION_REGISTERING = "Registering";
  public static final String EXCEPTION_CREDENTIALS = "Credential";
  public static final String EXCEPTION_ENVELOPE = "Envelope";
  public static final String EXCEPTION_SAMPLES = "Samples";
  public static final String EXCEPTION_BINDING = "Binding";
  private static final String RSA_PUBLIC_PEM = "rsa_public.pem";
  private static final String RSA2_PUBLIC_PEM = "rsa2_public.pem";
  private static final String RSA3_PUBLIC_PEM = "rsa3_public.pem";
  private static final String RSA_CERT_PEM = "rsa_cert.pem";
  private static final String RSA_PRIVATE_PEM = "rsa_private.pem";
  private static final String RSA_PRIVATE_PKCS8 = "rsa_private.pkcs8";
  private static final String ES_PUBLIC_PEM = "ec_public.pem";
  private static final String ES2_PUBLIC_PEM = "ec2_public.pem";
  private static final String ES3_PUBLIC_PEM = "ec3_public.pem";
  private static final String ES_CERT_PEM = "ec_cert.pem";
  private static final String ES_PRIVATE_PEM = "ec_private.pem";
  private static final String ES_PRIVATE_PKCS8 = "ec_private.pkcs8";
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
  private static final String AUX_DIR = "aux";
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
          RSA2_PUBLIC_PEM,
          RSA3_PUBLIC_PEM,
          ES2_PUBLIC_PEM,
          ES3_PUBLIC_PEM,
          SAMPLES_DIR,
          AUX_DIR,
          EXPECTED_DIR,
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
  private static final String ERROR_FORMAT_INDENT = "  ";
  private static final int MAX_JSON_LENGTH = 32767;
  private static final String UDMI_VERSION = "1.4.2";
  private final String deviceId;
  private final Map<String, JsonSchema> schemas;
  private final File siteDir;
  private final File deviceDir;
  private final File outDir;
  private final Metadata metadata;
  private final ExceptionMap exceptionMap;
  private final String generation;
  private final List<Credential> deviceCredentials = new ArrayList<>();
  private final Map<String, Object> siteMetadata;
  private final boolean validateMetadata;

  private String deviceNumId;

  private CloudDeviceSettings settings;
  private JsonNode baseVersion;

  LocalDevice(
      File siteDir, File devicesDir, String deviceId, Map<String, JsonSchema> schemas,
      String generation, Metadata siteMetadata, boolean validateMetadata) {
    try {
      this.deviceId = deviceId;
      this.schemas = schemas;
      this.generation = generation;
      this.siteDir = siteDir;
      this.validateMetadata = validateMetadata;
      this.siteMetadata = siteMetadata == null ? null : JsonUtil.asMap(siteMetadata);
      exceptionMap = new ExceptionMap("Exceptions for " + deviceId);
      deviceDir = new File(devicesDir, deviceId);
      outDir = new File(deviceDir, OUT_DIR);
      prepareOutDir();
      metadata = readMetadata();
    } catch (Exception e) {
      throw new RuntimeException("While loading local device " + deviceId, e);
    }
  }

  LocalDevice(
      File siteDir, File devicesDir, String deviceId, Map<String, JsonSchema> schemas,
      String generation, Metadata siteMetadata) {
    this(siteDir, devicesDir, deviceId, schemas, generation, siteMetadata, false);
  }

  LocalDevice(
      File siteDir, File devicesDir, String deviceId, Map<String, JsonSchema> schemas,
      String generation) {
    this(siteDir, devicesDir, deviceId, schemas, generation, null);
  }

  public static void parseMetadataValidateProcessingReport(ProcessingReport report)
      throws ValidationException {
    if (report.isSuccess()) {
      return;
    }

    for (ProcessingMessage msg : report) {
      if (msg.getLogLevel().compareTo(LogLevel.ERROR) >= 0) {
        throw ValidationException.fromProcessingReport(report);
      }
    }
  }

  static boolean deviceExists(File devicesDir, String deviceName) {
    return new File(new File(devicesDir, deviceName), METADATA_JSON).isFile();
  }

  private void prepareOutDir() {
    if (!outDir.exists()) {
      outDir.mkdir();
    }
    new File(outDir, EXCEPTION_LOG_FILE).delete();
  }

  public void validateExpected() {
    ExceptionMap exceptionMap = new ExceptionMap("expected files");

    String[] files = deviceDir.list();
    checkNotNull(files, "No files found in " + deviceDir.getAbsolutePath());
    Set<String> actualFiles = ImmutableSet.copyOf(files);
    Set<String> expectedFiles = Sets.union(DEVICE_FILES, keyFiles());
    SortedSet<String> missing = new TreeSet<>(Sets.difference(expectedFiles, actualFiles));
    if (!missing.isEmpty()) {
      exceptionMap.put("missing", new RuntimeException("Missing files: " + missing));
    }
    SortedSet<String> extra = new TreeSet<>(
        Sets.difference(Sets.difference(actualFiles, expectedFiles), OPTIONAL_FILES));
    if (!extra.isEmpty()) {
      exceptionMap.put("extra", new RuntimeException("Extra files: " + extra));
    }
    String[] outFiles = outDir.list();
    if (outFiles != null) {
      Set<String> outSet = ImmutableSet.copyOf(outFiles);
      SortedSet<String> extraOut = new TreeSet<>(Sets.difference(outSet, OUT_FILES));
      if (!extraOut.isEmpty()) {
        exceptionMap.put("out", new RuntimeException("Extra out files: " + extraOut));
      }
    }

    exceptionMap.throwIfNotEmpty();
  }

  private Metadata readMetadataWithValidation(boolean validate) {
    final JsonNode instance;
    try {
      Metadata loadedMetadata = SiteModel.loadDeviceMetadata(siteDir.getPath(), deviceId,
          LocalDevice.class);
      if (loadedMetadata instanceof MetadataException metadataException) {
        throw new RuntimeException("Loading " + metadataException.file.getAbsolutePath(),
            metadataException.exception);
      }
      instance = JsonUtil.convertTo(JsonNode.class, loadedMetadata);
      baseVersion = instance.get(VERSION_KEY);
      new MessageUpgrader(METADATA_SCHEMA, instance).upgrade(false);
    } catch (Exception exception) {
      exceptionMap.put(EXCEPTION_LOADING, exception);
      return null;
    }

    JsonNode mergedMetadata = getMergedMetadata(instance);

    try {
      ProcessingReport report = schemas.get(METADATA_JSON).validate(mergedMetadata);
      if (validate) {
        parseMetadataValidateProcessingReport(report);
      }
    } catch (ProcessingException | ValidationException e) {
      exceptionMap.put(EXCEPTION_VALIDATING, e);
    }
    return JsonUtil.convertTo(Metadata.class, mergedMetadata);
  }

  JsonNode getMergedMetadata(JsonNode instance) {
    try {
      String intermediary = JsonUtil.stringify(instance);
      if (siteMetadata == null) {
        return instance;
      } else {
        Map<String, Object> mergedMetadata = GeneralUtils.deepCopy(siteMetadata);
        GeneralUtils.mergeObject(mergedMetadata, asMap(intermediary));
        return JsonUtil.convertTo(JsonNode.class, mergedMetadata);
      }
    } catch (Exception e) {
      exceptionMap.put(EXCEPTION_READING, e);
    }
    return null;
  }

  private Metadata readMetadata() {
    Metadata deviceMetadata = readMetadataWithValidation(validateMetadata);
    if (deviceMetadata instanceof MetadataException metadataException) {
      exceptionMap.put(EXCEPTION_CONVERTING, metadataException.exception);
    }
    return deviceMetadata;
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
      return String.format("%08x", Objects.hash(json));
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
      if (hasGateway() && hasAuthType()) {
        throw new RuntimeException("Proxied devices should not have cloud.auth_type defined");
      }
      if (!isDirectConnect()) {
        return;
      }
      if (!hasAuthType()) {
        throw new RuntimeException("Credential cloud.auth_type definition missing");
      }
      String authType = getAuthType();
      Set<String> keyFiles =
          (authType.equals(ES_CERT_TYPE) || authType.equals(
              RSA_CERT_TYPE))
              ? ALL_CERT_FILES
              : ALL_KEY_FILES;
      for (String keyFile : keyFiles) {
        Credential deviceCredential = getDeviceCredential(keyFile);
        if (deviceCredential != null) {
          deviceCredentials.add(deviceCredential);
        }
      }
      int numCredentials = deviceCredentials.size();
      if (numCredentials == 0 || numCredentials > 3) {
        throw new RuntimeException(String.format("Found %d credentials", numCredentials));
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
    return CloudIotManager.makeCredentials(
        getAuthType(),
        IOUtils.toString(new FileInputStream(deviceKeyFile), Charset.defaultCharset()));
  }

  private Set<String> keyFiles() {
    if (metadata == null || !isDirectConnect()) {
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

  boolean isGateway() {
    return metadata != null
        && metadata.gateway != null
        && metadata.gateway.proxy_ids != null
        && !metadata.gateway.proxy_ids.isEmpty();
  }

  boolean hasGateway() {
    return metadata != null && metadata.gateway != null && metadata.gateway.gateway_id != null;
  }

  boolean isDirectConnect() {
    return isGateway() || !hasGateway();
  }

  CloudDeviceSettings getSettings() {
    return checkNotNull(settings, "Device settings not initialized");
  }

  void initializeSettings() {
    try {
      settings = new CloudDeviceSettings();
      settings.credentials = deviceCredentials;
      settings.generation = generation;

      if (metadata == null) {
        return;
      }

      settings.updated = getUpdatedTimestamp();
      settings.metadata = deviceMetadataString();
      settings.proxyDevices = getProxyDevicesList();
      settings.keyAlgorithm = getAuthType();
      settings.keyBytes = getKeyBytes();
      settings.config = deviceConfigString();
    } catch (Exception e) {
      captureError(EXCEPTION_INITIALIZING, e);
    }
  }

  public byte[] getKeyBytes() {
    if (!isDirectConnect()) {
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

  private List<String> getProxyDevicesList() {
    return isGateway() ? metadata.gateway.proxy_ids : null;
  }

  private String getUpdatedTimestamp() {
    return getTimestampString(metadata.timestamp);
  }

  private String getTimestampString(Date timestamp) {
    try {
      String quotedString = OBJECT_MAPPER_STRICT.writeValueAsString(timestamp);
      return quotedString.substring(1, quotedString.length() - 1);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("While generating updated timestamp", e);
    }
  }

  private String deviceConfigString() {
    try {
      JsonNode configJson = OBJECT_MAPPER_STRICT.valueToTree(deviceConfigObject());
      new MessageDowngrader("config", configJson).downgrade(baseVersion);
      return compressJsonString(configJson, MAX_JSON_LENGTH);
    } catch (Exception e) {
      throw new RuntimeException("While converting device config", e);
    }
  }

  public Config deviceConfigObject() {
    Config config = new Config();
    config.timestamp = metadata.timestamp;
    config.version = UDMI_VERSION;
    config.system = new SystemConfig();
    config.system.operation = new Operation();
    if (isGateway()) {
      config.gateway = new GatewayConfig();
      config.gateway.proxy_ids = getProxyDevicesList();
    }
    if (metadata.pointset != null) {
      config.pointset = getDevicePointsetConfig();
    }
    if (metadata.localnet != null) {
      config.localnet = getDeviceLocalnetConfig();
    }
    // Copy selected MetadataSystem properties into device config.
    if (metadata.system.min_loglevel != null) {
      config.system.min_loglevel = metadata.system.min_loglevel;
    }
    return config;
  }

  private LocalnetConfig getDeviceLocalnetConfig() {
    LocalnetConfig localnetConfig = new LocalnetConfig();
    localnetConfig.families = metadata.localnet.families;
    return localnetConfig;
  }

  private PointsetConfig getDevicePointsetConfig() {
    PointsetConfig pointsetConfig = new PointsetConfig();
    pointsetConfig.points = new HashMap<>();
    boolean excludeUnits = isTrue(metadata.pointset.exclude_units_from_config);
    metadata.pointset.points.forEach(
        (metadataKey, value) ->
            pointsetConfig.points.computeIfAbsent(
                metadataKey, configKey -> configFromMetadata(value, excludeUnits)));

    // Copy selected MetadataPointset properties into PointsetConfig.
    if (metadata.pointset.sample_limit_sec != null) {
      pointsetConfig.sample_limit_sec = metadata.pointset.sample_limit_sec;
    }
    if (metadata.pointset.sample_rate_sec != null) {
      pointsetConfig.sample_rate_sec = metadata.pointset.sample_rate_sec;
    }
    return pointsetConfig;
  }

  PointPointsetConfig configFromMetadata(PointPointsetModel metadata, boolean excludeUnits) {
    PointPointsetConfig pointConfig = new PointPointsetConfig();
    pointConfig.units = excludeUnits ? null : metadata.units;
    pointConfig.ref = metadata.ref;
    if (Boolean.TRUE.equals(metadata.writable)) {
      pointConfig.set_value = metadata.baseline_value;
    }
    return pointConfig;
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
      envelope.subType = SubType.EVENT;
      // Don't use actual project id because it should be abstracted away.
      envelope.projectId = fakeProjectId();
      envelope.deviceNumId = makeNumId(envelope);
      String envelopeJson = OBJECT_MAPPER_STRICT.writeValueAsString(envelope);
      ProcessingReport processingReport = schemas.get(ENVELOPE_JSON)
          .validate(OBJECT_MAPPER.readTree(envelopeJson));
      if (!processingReport.isSuccess()) {
        processingReport.forEach(action -> {
          throw new RuntimeException("against schema", action.asException());
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
          String.format("system.physical_tag.asset.name %s does not match expected %s", assetName,
              deviceId));

      String assetSite = metadata.system.physical_tag.asset.site;
      checkState(expectedSite.equals(assetSite),
          String.format(
              "system.physical_tag.asset.site %s does not match expected %s",
              assetSite, expectedSite));
    }

    if (metadata.system.location != null) {
      String siteName = metadata.system.location.site;
      checkState(expectedSite.equals(siteName),
          String.format(
              "system.location.site %s does not match expected %s", siteName, expectedSite));
    }
  }

  private String makeNumId(Envelope envelope) {
    int hash = Objects.hash(deviceId, envelope.deviceRegistryId, envelope.projectId);
    return Integer.toString(hash < 0 ? -hash : hash);
  }

  public void writeErrors(List<Pattern> ignoreErrors) {
    File errorsFile = new File(outDir, DEVICE_ERRORS_MAP);
    ErrorTree errorTree = getErrorTree(ignoreErrors);
    if (errorTree != null) {
      try (PrintStream printStream = new PrintStream(Files.newOutputStream(errorsFile.toPath()))) {
        System.err.println("Updating " + errorsFile);
        errorTree.write(printStream);
      } catch (Exception e) {
        throw new RuntimeException("While writing " + errorsFile.getAbsolutePath(), e);
      }
    } else if (errorsFile.exists()) {
      System.err.println("Removing " + errorsFile);
      errorsFile.delete();
    }
  }

  ErrorTree getErrorTree(List<Pattern> ignoreErrors) {
    if (exceptionMap.isEmpty()) {
      return null;
    }
    ErrorTree errorTree = ExceptionMap.format(exceptionMap);
    return errorTree.purge(ignoreErrors) ? null : errorTree;
  }

  String getNormalizedTimestamp() {
    return getTimestampString(metadata.timestamp);
  }

  void writeNormalized() {
    File metadataFile = new File(outDir, NORMALIZED_JSON);
    if (metadata == null) {
      System.err.println("Deleting (invalid) " + metadataFile.getAbsolutePath());
      metadataFile.delete();
      return;
    }
    if (metadata.cloud != null && metadata.cloud.credentials != null
        && metadata.cloud.credentials.isEmpty()) {
      metadata.cloud.credentials = null;
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
      exceptionMap.put(EXCEPTION_WRITING, e);
    }
  }

  public void writeConfigFile() {
    String config = getSettings().config;
    if (config != null) {
      File configFile = new File(outDir, GENERATED_CONFIG_JSON);
      try (OutputStream outputStream = Files.newOutputStream(configFile.toPath())) {
        outputStream.write(config.getBytes());
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException("While writing " + configFile.getAbsolutePath(), e);
      }
    }
  }

  public String getDeviceId() {
    return deviceId;
  }

  public String getDeviceNumId() {
    return checkNotNull(deviceNumId, "deviceNumId not set");
  }

  public void setDeviceNumId(String numId) {
    deviceNumId = numId;
  }

  public void captureError(String exceptionType, Exception exception) {
    exceptionMap.put(exceptionType, exception);
    File exceptionLog = new File(outDir, EXCEPTION_LOG_FILE);
    try {
      try (FileWriter fileWriter = new FileWriter(exceptionLog, true);
          PrintWriter printWriter = new PrintWriter(fileWriter)) {
        printWriter.println(exceptionType);
        exception.printStackTrace(printWriter);
      }
    } catch (Exception e) {
      throw new RuntimeException("Writing exception log file " + exceptionLog.getAbsolutePath(), e);
    }
  }

  public boolean isValid() {
    return metadata != null;
  }

  public void validateSamples() {
    File samplesDir = new File(deviceDir, SAMPLES_DIR);
    if (!samplesDir.exists()) {
      return;
    }
    File[] samples = samplesDir.listFiles();
    if (samples == null) {
      return;
    }
    ExceptionMap samplesMap = new ExceptionMap("Sample Validation");
    for (File sampleFile : samples) {
      String sampleName = sampleFile.getName();
      try (InputStream sampleStream = new FileInputStream(sampleFile)) {
        if (!schemas.containsKey(sampleName)) {
          throw new RuntimeException("No valid matching schema found");
        }
        schemas.get(sampleName).validate(OBJECT_MAPPER_STRICT.readTree(sampleStream));
      } catch (Exception e) {
        Exception scopedException =
            new RuntimeException("While validating sample file " + sampleName, e);
        samplesMap.put(sampleName, scopedException);
      }
    }
    samplesMap.throwIfNotEmpty();
  }

  public Set<Entry<String, ErrorTree>> getTreeChildren(List<Pattern> ignoreErrors) {
    ErrorTree errorTree = getErrorTree(ignoreErrors);
    if (errorTree != null && errorTree.children != null) {
      return errorTree.children.entrySet();
    }
    return Set.of();
  }

  public Metadata getMetadata() {
    return metadata;
  }

}
