package com.google.daq.mqtt.registrar;

import static com.google.daq.mqtt.registrar.Registrar.DEVICE_ERRORS_JSON;
import static com.google.daq.mqtt.registrar.Registrar.ENVELOPE_JSON;
import static com.google.daq.mqtt.registrar.Registrar.GENERATED_CONFIG_JSON;
import static com.google.daq.mqtt.registrar.Registrar.METADATA_JSON;
import static com.google.daq.mqtt.registrar.Registrar.NORMALIZED_JSON;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.api.services.cloudiot.v1.model.DeviceCredential;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.daq.mqtt.registrar.UdmiSchema.Config;
import com.google.daq.mqtt.registrar.UdmiSchema.GatewayConfig;
import com.google.daq.mqtt.util.CloudDeviceSettings;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ExceptionMap;
import com.google.daq.mqtt.util.ExceptionMap.ErrorTree;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.json.JSONTokener;

class LocalDevice {

  private static final PrettyPrinter PROPER_PRETTY_PRINTER_POLICY = new ProperPrettyPrinterPolicy();

  private static final ObjectMapper OBJECT_MAPPER_RAW = new ObjectMapper()
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
      .enable(Feature.ALLOW_TRAILING_COMMA)
      .enable(Feature.STRICT_DUPLICATE_DETECTION)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(Include.NON_NULL);

  private static final ObjectMapper OBJECT_MAPPER = OBJECT_MAPPER_RAW.copy()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(SerializationFeature.INDENT_OUTPUT);

  private static final String RSA_AUTH_TYPE = "RS256";
  private static final String RSA_CERT_TYPE = "RS256_X509";
  private static final String RSA_KEY_FORMAT = "RSA_PEM";
  private static final String RSA_CERT_FORMAT = "RSA_X509_PEM";
  private static final String RSA_PUBLIC_PEM = "rsa_public.pem";
  private static final String RSA2_PUBLIC_PEM = "rsa2_public.pem";
  private static final String RSA3_PUBLIC_PEM = "rsa3_public.pem";
  private static final String RSA_CERT_PEM = "rsa_cert.pem";
  private static final String RSA_PRIVATE_PEM = "rsa_private.pem";
  private static final String RSA_PRIVATE_PKCS8 = "rsa_private.pkcs8";

  private static final String ES_AUTH_TYPE = "ES256";
  private static final String ES_CERT_TYPE = "ES256_X509";
  private static final String ES_KEY_FORMAT = "ES256_PEM";
  private static final String ES_CERT_FILE = "ES256_X509_PEM";
  private static final String ES_PUBLIC_PEM = "ec_public.pem";
  private static final String ES2_PUBLIC_PEM = "ec2_public.pem";
  private static final String ES3_PUBLIC_PEM = "ec3_public.pem";
  private static final String ES_CERT_PEM = "ec_cert.pem";
  private static final String ES_PRIVATE_PEM = "ec_private.pem";
  private static final String ES_PRIVATE_PKCS8 = "ec_private.pkcs8";

  private static final String SAMPLES_DIR = "samples";

  private static final Set<String> DEVICE_FILES = ImmutableSet.of(METADATA_JSON);
  private static final Set<String> RSA_PRIVATE_KEY_FILES = ImmutableSet.of(
      RSA_PRIVATE_PEM, RSA_PRIVATE_PKCS8);
  private static final Set<String> ES_PRIVATE_KEY_FILES = ImmutableSet.of(
      ES_PRIVATE_PEM, ES_PRIVATE_PKCS8);
  private static final Map<String, Set<String>> PRIVATE_KEY_FILES_MAP = ImmutableMap.of(
      RSA_AUTH_TYPE, RSA_PRIVATE_KEY_FILES,
      RSA_CERT_TYPE, RSA_PRIVATE_KEY_FILES,
      ES_AUTH_TYPE, ES_PRIVATE_KEY_FILES,
      ES_CERT_TYPE, ES_PRIVATE_KEY_FILES
  );
  private static final Map<String, String> PUBLIC_KEY_FILE_MAP = ImmutableMap.of(
      RSA_AUTH_TYPE, RSA_PUBLIC_PEM,
      RSA_CERT_TYPE, RSA_CERT_PEM,
      ES_AUTH_TYPE, ES_PUBLIC_PEM,
      ES_CERT_TYPE, ES_CERT_PEM
  );

  private static final Set<String> OPTIONAL_FILES = ImmutableSet.of(
      RSA2_PUBLIC_PEM, RSA3_PUBLIC_PEM, ES2_PUBLIC_PEM, ES3_PUBLIC_PEM,
      GENERATED_CONFIG_JSON, DEVICE_ERRORS_JSON, NORMALIZED_JSON, SAMPLES_DIR);
  private static final Set<String> ALL_KEY_FILES = ImmutableSet.of(
      RSA_CERT_PEM, RSA_PUBLIC_PEM, RSA2_PUBLIC_PEM, RSA3_PUBLIC_PEM,
      ES_CERT_PEM, ES_PUBLIC_PEM, ES2_PUBLIC_PEM, ES3_PUBLIC_PEM
  );

  private static final Map<String, String> AUTH_TYPE_MAP = ImmutableMap.of(
      RSA_AUTH_TYPE, RSA_KEY_FORMAT,
      RSA_CERT_TYPE, RSA_CERT_FORMAT,
      ES_AUTH_TYPE, ES_KEY_FORMAT,
      ES_CERT_TYPE, ES_CERT_FILE
  );

  public static final String POINTSET_SUBFOLDER = "pointset";
  public static final String SYSTEM_SUBFOLDER = "system";
  public static final String GATEWAY_SUBFOLDER = "gateway";
  public static final String LOCALNET_SUBFOLDER = "localnet";
  private static final String ERROR_FORMAT_INDENT = "  ";
  private static final int MAX_METADATA_LENGTH = 32767;
  public static final String INVALID_METADATA_HASH = "INVALID";

  public static final String EXCEPTION_VALIDATING = "Validating";
  public static final String EXCEPTION_LOADING = "Loading";
  public static final String EXCEPTION_READING = "Reading";
  public static final String EXCEPTION_WRITING = "Writing";
  public static final String EXCEPTION_FILES = "Files";
  public static final String EXCEPTION_REGISTERING = "Registering";
  public static final String EXCEPTION_CREDENTIALS = "Credential";
  public static final String EXCEPTION_ENVELOPE = "Envelope";
  public static final String EXCEPTION_SAMPLES = "Samples";

  private final String deviceId;
  private final Map<String, Schema> schemas;
  private final File deviceDir;
  private final UdmiSchema.Metadata metadata;
  private final ExceptionMap exceptionMap;
  private final String generation;

  private String deviceNumId;

  private CloudDeviceSettings settings;
  private List<DeviceCredential> deviceCredentials;

  LocalDevice(File devicesDir, String deviceId, Map<String, Schema> schemas,
      String generation) {
    try {
      this.deviceId = deviceId;
      this.schemas = schemas;
      this.generation = generation;
      exceptionMap = new ExceptionMap("Exceptions for " + deviceId);
      deviceDir = new File(devicesDir, deviceId);
      metadata = readMetadata();
    } catch (Exception e) {
      throw new RuntimeException("While loading local device " + deviceId, e);
    }
  }

  static boolean deviceExists(File devicesDir, String deviceName) {
    return new File(new File(devicesDir, deviceName), METADATA_JSON).isFile();
  }

  public void validateExpected() {
    try {
      String[] files = deviceDir.list();
      Preconditions.checkNotNull(files, "No files found in " + deviceDir.getAbsolutePath());
      Set<String> actualFiles = ImmutableSet.copyOf(files);
      Set<String> expectedFiles = Sets.union(DEVICE_FILES, keyFiles());
      SetView<String> missing = Sets.difference(expectedFiles, actualFiles);
      if (!missing.isEmpty()) {
        throw new RuntimeException("Missing files: " + missing);
      }
      SetView<String> extra = Sets.difference(Sets.difference(actualFiles, expectedFiles), OPTIONAL_FILES);
      if (!extra.isEmpty()) {
        throw new RuntimeException("Extra files: " + extra);
      }
    } catch (Exception e) {
      throw new RuntimeException("While validating device directory " + deviceId, e);
    }
  }

  private UdmiSchema.Metadata readMetadata() {
    File metadataFile = new File(deviceDir, METADATA_JSON);
    try (InputStream targetStream = new FileInputStream(metadataFile)) {
      schemas.get(METADATA_JSON).validate(new JSONObject(new JSONTokener(targetStream)));
    } catch (ValidationException metadata_exception) {
      exceptionMap.put(EXCEPTION_VALIDATING, metadata_exception);
    } catch (IOException ioException) {
      exceptionMap.put(EXCEPTION_LOADING, ioException);
    }
    try {
      return OBJECT_MAPPER.readValue(metadataFile, UdmiSchema.Metadata.class);
    } catch (Exception mapping_exception) {
      exceptionMap.put(EXCEPTION_READING, mapping_exception);
    }
    return null;
  }

  private UdmiSchema.Metadata readNormalized() {
    try {
      File metadataFile = new File(deviceDir, NORMALIZED_JSON);
      return OBJECT_MAPPER.readValue(metadataFile, UdmiSchema.Metadata.class);
    } catch (Exception mapping_exception) {
      return new UdmiSchema.Metadata();
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
      String json = metadataString();
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
    return metadata.cloud == null ? null : metadata.cloud.auth_type;
  }

  private boolean isDeviceKeySource() {
    return metadata.cloud == null ? null : Boolean.TRUE.equals(metadata.cloud.device_key);
  }

  private String getAuthFileType() {
    String authType = getAuthType();
    if (!AUTH_TYPE_MAP.containsKey(authType)) {
      throw new RuntimeException("Invalid auth type: " + authType);
    }
    return AUTH_TYPE_MAP.get(authType);
  }

  public void loadCredentials() {
    try {
      if (hasGateway() && hasAuthType()) {
        throw new RuntimeException("Proxied devices should not have cloud.auth_type defined");
      }
      if (!isDirectConnect()) {
        return;
      }
      if (!hasAuthType()) {
        throw new RuntimeException("Credential cloud.auth_type definition missing");
      }
      deviceCredentials = new ArrayList<>();
      for (String keyFile : ALL_KEY_FILES) {
        DeviceCredential deviceCredential = getDeviceCredential(keyFile);
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

  private DeviceCredential getDeviceCredential(String keyFile) throws IOException {
    File deviceKeyFile = new File(deviceDir, keyFile);
    if (!deviceKeyFile.exists()) {
      return null;
    }
    return CloudIotManager.makeCredentials(getAuthFileType(),
        IOUtils.toString(new FileInputStream(deviceKeyFile), Charset.defaultCharset()));
  }

  private Set<String> keyFiles() {
    if (!isDirectConnect()) {
      return ImmutableSet.of();
    }
    Set<String> publicKeyFile = Set.of(publicKeyFile());
    Set<String> privateKeyFiles = privateKeyFiles();
    return Sets.union(publicKeyFile, privateKeyFiles);
  }

  private Set<String> privateKeyFiles() {
    if (isDeviceKeySource() || !hasAuthType()) {
      return Set.of();
    }
    return PRIVATE_KEY_FILES_MAP.get(getAuthType());
  }

  private String publicKeyFile() {
    return PUBLIC_KEY_FILE_MAP.get(getAuthType());
  }

  boolean isGateway() {
    return metadata != null && metadata.gateway != null &&
        metadata.gateway.proxy_ids != null;
  }

  boolean hasGateway() {
    return metadata != null && metadata.gateway != null &&
        metadata.gateway.gateway_id != null;
  }

  boolean isDirectConnect() {
    return isGateway() || !hasGateway();
  }

  CloudDeviceSettings getSettings() {
    try {
      if (settings != null) {
        return settings;
      }
      settings = new CloudDeviceSettings();
      if (metadata == null) {
        return settings;
      }
      settings.credentials = deviceCredentials;
      settings.metadata = metadataString();
      settings.config = deviceConfigString();
      settings.updated = getUpdatedTimestamp();
      settings.proxyDevices = getProxyDevicesList();
      settings.keyAlgorithm = getAuthType();
      settings.keyBytes = getKeyBytes();
      settings.generation = generation;
      return settings;
    } catch (Exception e) {
      throw new RuntimeException("While getting settings for device " + deviceId, e);
    }
  }

  private byte[] getKeyBytes() {
    File keyBytesFile = new File(deviceDir, RSA_PRIVATE_PKCS8);
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
    try {
      String quotedString = OBJECT_MAPPER.writeValueAsString(metadata.timestamp);
      return quotedString.substring(1, quotedString.length() - 1);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("While generating updated timestamp", e);
    }
  }

  private String deviceConfigString() {
    try {
      Config config = deviceConfigObject();
      return OBJECT_MAPPER.writeValueAsString(config);
    } catch (Exception e) {
      throw new RuntimeException("While converting device config to string", e);
    }
  }

  public UdmiSchema.Config deviceConfigObject() {
    Config config = new Config();
    config.timestamp = metadata.timestamp;
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
    return config;
  }

  private UdmiSchema.LocalnetConfig getDeviceLocalnetConfig() {
    UdmiSchema.LocalnetConfig localnetConfig = new UdmiSchema.LocalnetConfig();
    localnetConfig.subsystems = metadata.localnet.subsystem;
    return localnetConfig;
  }

  private UdmiSchema.PointsetConfig getDevicePointsetConfig() {
    UdmiSchema.PointsetConfig pointsetConfig = new UdmiSchema.PointsetConfig();
    metadata.pointset.points.forEach((metadataKey, value) ->
        pointsetConfig.points.computeIfAbsent(metadataKey, configKey ->
            UdmiSchema.PointConfig.fromMetadata(value)));
    return pointsetConfig;
  }

  private String metadataString() {
    try {
      String prettyString = OBJECT_MAPPER.writeValueAsString(metadata);
      if (prettyString.length() <= MAX_METADATA_LENGTH) {
        return prettyString;
      }
      return OBJECT_MAPPER_RAW.writeValueAsString(metadata);
    } catch (Exception e) {
      throw new RuntimeException("While converting metadata to string", e);
    }
  }

  public void validateEnvelope(String registryId, String siteName) {
    checkConsistency(siteName);
    try {
      UdmiSchema.Envelope envelope = new UdmiSchema.Envelope();
      envelope.deviceId = deviceId;
      envelope.deviceRegistryId = registryId;
      envelope.subFolder = POINTSET_SUBFOLDER;
      // Don't use actual project id because it should be abstracted away.
      envelope.projectId = fakeProjectId();
      envelope.deviceNumId = makeNumId(envelope);
      String envelopeJson = OBJECT_MAPPER.writeValueAsString(envelope);
      schemas.get(ENVELOPE_JSON).validate(new JSONObject(new JSONTokener(envelopeJson)));
    } catch (Exception e) {
      throw new IllegalStateException("Validating envelope " + deviceId, e);
    }
  }

  private String fakeProjectId() {
    return metadata.system.location.site.toLowerCase();
  }

  private void checkConsistency(String expectedSite) {
    String assetName = metadata.system.physical_tag.asset.name;
    Preconditions.checkState(deviceId.equals(assetName),
        String.format("system.physical_tag.asset.name %s does not match expected %s", assetName, deviceId));

    String assetSite = metadata.system.physical_tag.asset.site;
    Preconditions.checkState(expectedSite.equals(assetSite),
        String.format("system.physical_tag.asset.site %s does not match expected %s", assetSite, expectedSite));

    String siteName = metadata.system.location.site;
    Preconditions.checkState(expectedSite.equals(siteName),
        String.format("system.location.site %s does not match expected %s", siteName, expectedSite));
  }

  private String makeNumId(UdmiSchema.Envelope envelope) {
    int hash = Objects.hash(deviceId, envelope.deviceRegistryId, envelope.projectId);
    return Integer.toString(hash < 0 ? -hash : hash);
  }

  public void writeErrors(List<Pattern> ignoreErrors) {
    File errorsFile = new File(deviceDir, DEVICE_ERRORS_JSON);
    ErrorTree errorTree = getErrorTree(ignoreErrors);
    if (errorTree != null) {
      try (PrintStream printStream = new PrintStream(new FileOutputStream(errorsFile))) {
        System.err.println("Updating " + errorsFile);
        errorTree.write(printStream);
      } catch (Exception e) {
        throw new RuntimeException("While writing "+ errorsFile.getAbsolutePath(), e);
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
    ErrorTree errorTree = ExceptionMap.format(exceptionMap, ERROR_FORMAT_INDENT);
    return errorTree.purge(ignoreErrors) ? null : errorTree;
  }

  void writeNormalized() {
    File metadataFile = new File(deviceDir, NORMALIZED_JSON);
    if (metadata == null) {
      System.err.println("Deleting (invalid) " + metadataFile.getAbsolutePath());
      metadataFile.delete();
      return;
    }
    UdmiSchema.Metadata normalized = readNormalized();
    String writeHash = metadataHash();
    if (normalized.hash != null && normalized.hash.equals(writeHash)) {
      metadata.timestamp = normalized.timestamp;
      return;
    }
    metadata.timestamp = new Date();
    metadata.hash = writeHash;
    System.err.println("Writing normalized " + metadataFile.getAbsolutePath());
    try (OutputStream outputStream = new FileOutputStream(metadataFile)) {
      // Super annoying, but can't set this on the global static instance.
      JsonGenerator generator = OBJECT_MAPPER.getFactory()
          .createGenerator(outputStream)
          .setPrettyPrinter(PROPER_PRETTY_PRINTER_POLICY);
      OBJECT_MAPPER.writeValue(generator, metadata);
    } catch (Exception e) {
      exceptionMap.put(EXCEPTION_WRITING, e);
    }
  }

  public void writeConfigFile() {
    File configFile = new File(deviceDir, GENERATED_CONFIG_JSON);
    try (OutputStream outputStream = new FileOutputStream(configFile)) {
      outputStream.write(getSettings().config.getBytes());
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("While writing "+ configFile.getAbsolutePath(), e);
    }
  }

  public String getDeviceId() {
    return deviceId;
  }

  public String getDeviceNumId() {
    return Preconditions.checkNotNull(deviceNumId, "deviceNumId not set");
  }

  public void setDeviceNumId(String numId) {
    deviceNumId = numId;
  }

  public ExceptionMap getErrorMap() {
    return exceptionMap;
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
        schemas.get(sampleName).validate(new JSONObject(new JSONTokener(sampleStream)));
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

  private static class ProperPrettyPrinterPolicy extends DefaultPrettyPrinter {
    @Override
    public void writeObjectFieldValueSeparator(JsonGenerator jg) throws IOException {
      jg.writeRaw(": ");
    }
  }
}
