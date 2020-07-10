package com.google.daq.mqtt.registrar;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.api.services.cloudiot.v1.model.DeviceCredential;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.daq.mqtt.util.CloudDeviceSettings;
import com.google.daq.mqtt.util.CloudIotManager;
import com.google.daq.mqtt.util.ExceptionMap;
import org.apache.commons.io.IOUtils;
import org.everit.json.schema.Schema;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import static com.google.daq.mqtt.registrar.Registrar.*;

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

  private static final String RSA_CERT_TYPE = "RS256_X509";
  private static final String RSA_KEY_FILE = "RSA_PEM";
  private static final String RSA_CERT_FILE = "RSA_X509_PEM";
  private static final String RSA_PUBLIC_PEM = "rsa_public.pem";
  private static final String RSA_CERT_PEM = "rsa_cert.pem";
  private static final String RSA_PRIVATE_PEM = "rsa_private.pem";
  private static final String RSA_PRIVATE_PKCS8 = "rsa_private.pkcs8";

  private static final Set<String> DEVICE_FILES = ImmutableSet.of(METADATA_JSON);
  private static final Set<String> KEY_FILES = ImmutableSet.of(RSA_PUBLIC_PEM, RSA_PRIVATE_PEM, RSA_PRIVATE_PKCS8);
  private static final Set<String> OPTIONAL_FILES = ImmutableSet.of(
      GENERATED_CONFIG_JSON, DEVICE_ERRORS_JSON, NORMALIZED_JSON);

  private static final String KEYGEN_EXEC_FORMAT = "validator/bin/keygen %s %s";
  public static final String METADATA_SUBFOLDER = "metadata";
  private static final String ERROR_FORMAT_INDENT = "  ";
  private static final int MAX_METADATA_LENGTH = 32767;
  public static final String INVALID_METADATA_HASH = "INVALID";

  private final String deviceId;
  private final Map<String, Schema> schemas;
  private final File deviceDir;
  private final UdmiSchema.Metadata metadata;
  private final ExceptionMap exceptionMap;

  private String deviceNumId;

  private CloudDeviceSettings settings;
  private DeviceCredential deviceCredential;

  LocalDevice(File devicesDir, String deviceId, Map<String, Schema> schemas) {
    try {
      this.deviceId = deviceId;
      this.schemas = schemas;
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

  public void validatedDeviceDir() {
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
    } catch (Exception metadata_exception) {
      exceptionMap.put("Validating", metadata_exception);
    }
    try {
      return OBJECT_MAPPER.readValue(metadataFile, UdmiSchema.Metadata.class);
    } catch (Exception mapping_exception) {
      exceptionMap.put("Reading", mapping_exception);
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

  private String getAuthType() {
    return metadata.cloud == null ? null : metadata.cloud.auth_type;
  }

  private String getAuthFileType() {
    return RSA_CERT_TYPE.equals(getAuthType()) ? RSA_CERT_FILE : RSA_KEY_FILE;
  }

  public DeviceCredential loadCredential() {
    deviceCredential = readCredential();
    return deviceCredential;
  }

  public DeviceCredential readCredential() {
    try {
      if (hasGateway() && getAuthType() != null) {
        throw new RuntimeException("Proxied devices should not have cloud.auth_type defined");
      }
      if (!isDirectConnect()) {
        return null;
      }
      if (getAuthType() == null) {
        throw new RuntimeException("Credential cloud.auth_type definition missing");
      }
      File deviceKeyFile = new File(deviceDir, publicKeyFile());
      if (!deviceKeyFile.exists()) {
        generateNewKey();
      }
      return CloudIotManager.makeCredentials(getAuthFileType(),
          IOUtils.toString(new FileInputStream(deviceKeyFile), Charset.defaultCharset()));
    } catch (Exception e) {
      throw new RuntimeException("While loading credential for local device " + deviceId, e);
    }
  }

  private Set<String> keyFiles() {
    if (!isDirectConnect()) {
      return ImmutableSet.of();
    }
    return Sets.union(Sets.union(DEVICE_FILES, KEY_FILES), Set.of(publicKeyFile()));
  }

  private String publicKeyFile() {
    return RSA_CERT_TYPE.equals(getAuthType()) ? RSA_CERT_PEM : RSA_PUBLIC_PEM;
  }

  private void generateNewKey() {
    String absolutePath = deviceDir.getAbsolutePath();
    try {
      String command = String.format(KEYGEN_EXEC_FORMAT, getAuthType(), absolutePath);
      System.err.println(command);
      int exitCode = Runtime.getRuntime().exec(command).waitFor();
      if (exitCode != 0) {
        throw new RuntimeException("Keygen exit code " + exitCode);
      }
    } catch (Exception e) {
      throw new RuntimeException("While generating new credential for " + deviceId, e);
    }
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
      settings.credential = deviceCredential;
      settings.metadata = metadataString();
      settings.config = deviceConfigString();
      settings.proxyDevices = getProxyDevicesList();
      return settings;
    } catch (Exception e) {
      throw new RuntimeException("While getting settings for device " + deviceId, e);
    }
  }

  private List<String> getProxyDevicesList() {
    return isGateway() ? metadata.gateway.proxy_ids : null;
  }

  private String deviceConfigString() {
    try {
      UdmiSchema.Config config = new UdmiSchema.Config();
      config.timestamp = metadata.timestamp;
      if (isGateway()) {
        config.gateway = new UdmiSchema.GatewayConfig();
        config.gateway.proxy_ids = getProxyDevicesList();
      }
      if (metadata.pointset != null) {
        config.pointset = getDevicePointsetConfig();
      }
      if (metadata.localnet != null) {
        config.localnet = getDeviceLocalnetConfig();
      }
      return OBJECT_MAPPER.writeValueAsString(config);
    } catch (Exception e) {
      throw new RuntimeException("While converting device config to string", e);
    }
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
            UdmiSchema.PointConfig.fromRef(value.ref)));
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

  public void writeErrors() {
    File errorsFile = new File(deviceDir, DEVICE_ERRORS_JSON);
    if (exceptionMap.isEmpty()) {
      System.err.println("Removing " + errorsFile);
      errorsFile.delete();
      return;
    }
    System.err.println("Updating " + errorsFile);
    try (PrintStream printStream = new PrintStream(new FileOutputStream(errorsFile))) {
      ExceptionMap.ErrorTree errorTree = ExceptionMap.format(exceptionMap, ERROR_FORMAT_INDENT);
      errorTree.write(printStream);
    } catch (Exception e) {
      throw new RuntimeException("While writing "+ errorsFile.getAbsolutePath(), e);
    }
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
      exceptionMap.put("Writing", e);
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

  public ExceptionMap getErrors() {
    return exceptionMap;
  }

  public boolean isValid() {
    return metadata != null;
  }

  private static class ProperPrettyPrinterPolicy extends DefaultPrettyPrinter {
    @Override
    public void writeObjectFieldValueSeparator(JsonGenerator jg) throws IOException {
      jg.writeRaw(": ");
    }
  }
}
