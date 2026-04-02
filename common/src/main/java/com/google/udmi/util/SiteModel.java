package com.google.udmi.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.DEFAULT_REGION;
import static com.google.udmi.util.Common.NO_SITE;
import static com.google.udmi.util.Common.UDMI_COMMIT_ENV;
import static com.google.udmi.util.Common.UDMI_REF_ENV;
import static com.google.udmi.util.Common.UDMI_TIMEVER_ENV;
import static com.google.udmi.util.Common.UDMI_VERSION_ENV;
import static com.google.udmi.util.Common.getNamespacePrefix;
import static com.google.udmi.util.GeneralUtils.OBJECT_MAPPER_RAW;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;
import static com.google.udmi.util.GeneralUtils.getFileBytes;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullGetElse;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.GeneralUtils.removeStringArg;
import static com.google.udmi.util.GeneralUtils.sha256;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.convertToStrict;
import static com.google.udmi.util.JsonUtil.loadFileRequired;
import static com.google.udmi.util.JsonUtil.stringifyTerse;
import static com.google.udmi.util.JsonUtil.writeFile;
import static com.google.udmi.util.MessageUpgrader.METADATA_SCHEMA;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.udmi.util.ExceptionMap.ExceptionCategory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Auth_type;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.GatewayModel;
import udmi.schema.IotAccess.IotProvider;
import udmi.schema.Metadata;
import udmi.schema.SiteMetadata;

public class SiteModel {

  public static final String DEFAULT_CLEARBLADE_HOSTNAME = "us-central1-mqtt.clearblade.com";
  public static final String DEFAULT_CLEARBLADE_HOSTNAME_FORMAT = "%s-mqtt.clearblade.com";
  public static final String DEFAULT_GBOS_HOSTNAME = "mqtt.bos.goog";
  public static final String MOCK_PROJECT = "mock-project";
  public static final String MOCK_CLEAN = "mock-clean";
  public static final String LOCALHOST_HOSTNAME = "localhost";
  public static final String DEVICES_DIR = "devices";
  public static final String TEMPLATES_DIR = "templates";
  public static final String REFLECTOR_DIR = "reflector";
  public static final String METADATA_JSON = "metadata.json";
  public static final String EXTRAS_DIR = "extras";
  public static final String EXTRA_DEVICES_FORMAT = EXTRAS_DIR + "/%s";
  public static final String NORMALIZED_JSON = "metadata_norm.json";
  public static final String SITE_DEFAULTS_FILE = "site_defaults.json";
  public static final String REGISTRATION_SUMMARY_BASE = "out/registration_summary";
  public static final String LEGACY_METADATA_FILE = "site_metadata.json";
  public static final String SITE_METADATA_FILE = "site_metadata.json";
  public static final String METADATA_DIR = "cloud_metadata";
  public static final String CLOUD_MODEL_FILE = "cloud_model.json";
  private static final String ID_FORMAT = "projects/%s/locations/%s/registries/%s/devices/%s";
  private static final String KEY_SITE_PATH_FORMAT = "%s/devices/%s/%s%s_private.pkcs8";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setDateFormat(new ISO8601DateFormat())
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  private static final Pattern IOT_CORE_PATTERN = Pattern.compile(
      "projects/(.*)/locations/(.*)/registries/(.*)/devices/(.*)");
  private static final Pattern MQTT_PATTERN = Pattern.compile("/r/(.*)/d/(.*)");
  private static final String CLOUD_IOT_CONFIG_JSON = "cloud_iot_config.json";
  private static final Pattern SPEC_PATTERN = Pattern.compile(
      "(//([a-z]+)/)?(([a-z-]+))(/([a-z0-9]+))?(\\+([a-z0-9-]+))?");
  private static final int SPEC_PROVIDER_GROUP = 2;
  private static final int SPEC_PROJECT_GROUP = SPEC_PROVIDER_GROUP + 2;
  private static final int SPEC_NAMESPACE_GROUP = SPEC_PROJECT_GROUP + 2;
  private static final int SPEC_USER_GROUP = SPEC_NAMESPACE_GROUP + 2;
  private static final File CONFIG_OUT_DIR = new File("out/");
  private static final String RSA_PRIVATE_KEY = "rsa_private.pkcs8";
  private static final String EC_PRIVATE_KEY = "ec_private.pkcs8";
  private static final Set<String> ALLOWED_FILES_IN_DEVICES_DIR = ImmutableSet.of(
      "README.md"
  );
  private static final String TEMPLATE_KEY = "extend";

  private final String sitePath;
  private final Map<String, Object> siteDefaults;
  private final File siteConf;
  private final ExecutionConfiguration exeConfig;
  private final List<String> KEY_SUFFIXES = ImmutableList.of("", "2", "3");
  private final Matcher specMatcher;
  private Map<String, Metadata> allMetadata;
  private Map<String, CloudModel> allDevices;
  public ExceptionMap siteMetadataExceptionMap;
  private boolean warningsAsErrors;
  private SiteMetadata siteMetadata;
  private final Map<String, Map<String, Object>> allTemplates;

  public SiteModel(String specPath) {
    this(specPath, null, null);
  }

  public SiteModel(String sitePath, ExecutionConfiguration config) {
    this(sitePath, null, config);
  }

  public SiteModel(String specPath, Supplier<String> specSupplier,
      ExecutionConfiguration overrides) {
    File specFile = new File(requireNonNull(specPath, "site model not defined"));
    boolean specIsDirectory = specFile.isDirectory();
    siteConf = specIsDirectory ? cloudConfigPath(specFile) : specFile;
    if (!siteConf.exists()) {
      throw new RuntimeException("File not found: " + siteConf.getAbsolutePath());
    }
    specMatcher = specIsDirectory && specSupplier != null ? extractSpec(specSupplier.get()) : null;
    exeConfig = loadSiteConfig();
    sitePath = ofNullable(exeConfig.site_model).map(f -> maybeRelativeTo(f, exeConfig.src_file))
        .orElse(siteConf.getParent());
    exeConfig.site_model = new File(sitePath).getAbsolutePath();
    loadVersionInfo(exeConfig);
    siteDefaults = ofNullable(asMap(getSiteFile(SITE_DEFAULTS_FILE))).orElseGet(HashMap::new);
    allTemplates = loadAllTemplates();
    if (overrides != null && overrides.project_id != null) {
      exeConfig.iot_provider = overrides.iot_provider;
      exeConfig.project_id = overrides.project_id;
      exeConfig.udmi_namespace = overrides.udmi_namespace;
    }
  }

  public SiteModel(String toolName, List<String> argList) {
    this(removeStringArg(argList, "site_model"), projectSpecSupplier(argList), null);
    ExecutionConfiguration executionConfiguration = getExecutionConfiguration();
    File outFile = new File(CONFIG_OUT_DIR, format("%s_conf.json", toolName));
    System.err.println("Writing reconciled configuration file to " + outFile.getAbsolutePath());
    CONFIG_OUT_DIR.mkdirs();
    writeFile(executionConfiguration, outFile);
  }

  public SiteModel(ExecutionConfiguration executionConfiguration) {
    this(executionConfiguration.site_model);
    // TODO: Fix this total hack.
    exeConfig.registry_id = executionConfiguration.registry_id;
    exeConfig.registry_suffix = executionConfiguration.registry_suffix;
  }

  private static void loadVersionInfo(ExecutionConfiguration exeConfig) {
    exeConfig.udmi_version = System.getenv(UDMI_VERSION_ENV);
    exeConfig.udmi_commit = System.getenv(UDMI_COMMIT_ENV);
    exeConfig.udmi_ref = System.getenv(UDMI_REF_ENV);
    exeConfig.udmi_timever = System.getenv(UDMI_TIMEVER_ENV);
  }

  private static Supplier<String> projectSpecSupplier(List<String> argList) {
    return () -> {
      if (argList.isEmpty()) {
        return NO_SITE;
      }
      String nextArg = argList.get(0);
      if (nextArg.equals(NO_SITE)) {
        return argList.remove(0);
      } else if (nextArg.startsWith("-")) {
        return null;
      }
      return removeStringArg(argList, "project spec");
    };
  }

  private static File cloudConfigPath(File specFile) {
    return new File(specFile, CLOUD_IOT_CONFIG_JSON);
  }

  public static EndpointConfiguration makeEndpointConfig(String iotProject,
      ExecutionConfiguration executionConfig, String deviceId) {
    EndpointConfiguration endpoint = new EndpointConfiguration();
    endpoint.client_id = getClientId(iotProject,
        executionConfig.cloud_region, getRegistryActual(executionConfig), deviceId);
    endpoint.hostname = getEndpointHostname(executionConfig);
    return endpoint;
  }

  private static String getEndpointHostname(ExecutionConfiguration executionConfig) {
    IotProvider iotProvider = ofNullable(executionConfig.iot_provider).orElse(IotProvider.IMPLICIT);
    return switch (iotProvider) {
      case CLEARBLADE -> ifNotNullGet(executionConfig.cloud_region,
          region -> format(DEFAULT_CLEARBLADE_HOSTNAME_FORMAT, region),
          DEFAULT_CLEARBLADE_HOSTNAME);
      case GBOS -> DEFAULT_GBOS_HOSTNAME;
      case IMPLICIT, DYNAMIC -> LOCALHOST_HOSTNAME;
      case MQTT -> requireNonNull(executionConfig.project_id, "missing project_id as hostname");
      default -> throw new RuntimeException("Unsupported iot_provider " + iotProvider);
    };
  }

  public static String getClientId(String iotProject, String cloudRegion, String registryId,
      String deviceId) {
    return format(ID_FORMAT,
        requireNonNull(iotProject, "iot project not defined"),
        requireNonNull(cloudRegion, "cloud region not defined"),
        requireNonNull(registryId, "registry id not defined"),
        requireNonNull(deviceId, "device id not defined"));
  }

  private static ExecutionConfiguration makeExecutionConfiguration(Envelope attributes) {
    ExecutionConfiguration executionConfiguration = new ExecutionConfiguration();
    executionConfiguration.registry_id = checkNotNull(attributes.deviceRegistryId,
        "deviceRegistryId");
    executionConfiguration.cloud_region = checkNotNull(
        attributes.deviceRegistryLocation,
        "deviceRegistryLocation");
    return executionConfiguration;
  }

  public static EndpointConfiguration makeEndpointConfig(Envelope attributes) {
    ExecutionConfiguration executionConfiguration = makeExecutionConfiguration(attributes);
    return makeEndpointConfig(attributes.projectId, executionConfiguration, attributes.deviceId);
  }

  /**
   * Parse a GCP clientId string into component parts including project, etc...
   *
   * @param clientId client id to parse
   * @return bucket of parameters
   */
  public static ClientInfo parseClientId(String clientId) {
    if (clientId == null) {
      throw new IllegalArgumentException("client_id not specified");
    }

    Matcher iotCoreMatcher = IOT_CORE_PATTERN.matcher(clientId);
    if (iotCoreMatcher.matches()) {
      ClientInfo clientInfo = new ClientInfo();
      clientInfo.iotProject = iotCoreMatcher.group(1);
      clientInfo.cloudRegion = iotCoreMatcher.group(2);
      clientInfo.registryId = iotCoreMatcher.group(3);
      clientInfo.deviceId = iotCoreMatcher.group(4);
      return clientInfo;
    }

    Matcher mqttMatcher = MQTT_PATTERN.matcher(clientId);
    if (mqttMatcher.matches()) {
      ClientInfo clientInfo = new ClientInfo();
      clientInfo.iotProject = LOCALHOST_HOSTNAME;
      clientInfo.cloudRegion = DEFAULT_REGION;
      clientInfo.registryId = mqttMatcher.group(1);
      clientInfo.deviceId = mqttMatcher.group(2);
      return clientInfo;
    }

    throw new IllegalArgumentException(format("client_id %s does not match pattern %s or %s",
        clientId, IOT_CORE_PATTERN.pattern(), MQTT_PATTERN.pattern()));
  }

  public static List<String> listDevices(File devicesDir) {
    if (!devicesDir.exists()) {
      return ImmutableList.of();
    }
    String[] devices = requireNonNull(devicesDir.list());
    return Arrays.stream(devices).filter(device -> validDeviceDirectory(devicesDir, device))
        .collect(Collectors.toList());
  }

  private static boolean validDeviceDirectory(File baseDir, String dirName) {
    File file = new File(baseDir, dirName);

    if (!file.isDirectory()) {
      if (ALLOWED_FILES_IN_DEVICES_DIR.contains(dirName)) {
        return false;
      }
      throw new RuntimeException("Unexpected file in devices directory: " + file.getAbsolutePath());
    }

    if (dirName.startsWith(".") || dirName.endsWith("~")) {
      System.err.println("Warning: Skipping invalid device directory: " + dirName);
      return false;
    }

    return true;
  }

  public static String getRegistryActual(ExecutionConfiguration iotConfig) {
    return getRegistryActual(iotConfig.udmi_namespace, iotConfig.registry_id,
        iotConfig.registry_suffix);
  }

  public static String getRegistryActual(String namespace,
      String registry_id, String registry_suffix) {
    if (registry_id == null) {
      return null;
    }
    return getNamespacePrefix(namespace) + registry_id + ofNullable(registry_suffix).orElse("");
  }

  public Set<String> getExtraDevices() {
    String[] existing = ofNullable(getExtrasDir().list()).orElse(new String[0]);
    return Arrays.stream(existing).filter(this::extraExists).collect(Collectors.toSet());
  }

  private boolean extraExists(String extraId) {
    return new File(getExtraDir(extraId), CLOUD_MODEL_FILE).exists();
  }

  private static void augmentConfig(ExecutionConfiguration exeConfig, Matcher specMatcher) {
    try {
      String iotProvider = specMatcher.group(SPEC_PROVIDER_GROUP);
      exeConfig.iot_provider = ifNotNullGet(iotProvider, IotProvider::fromValue);
      String matchedId = specMatcher.group(SPEC_PROJECT_GROUP);
      exeConfig.project_id = NO_SITE.equals(matchedId) ? null : matchedId;
      exeConfig.user_name = specMatcher.group(SPEC_USER_GROUP);
      exeConfig.udmi_namespace = specMatcher.group(SPEC_NAMESPACE_GROUP);
    } catch (Exception e) {
      throw new RuntimeException(
          "While augmenting config from provider spec " + specMatcher.group(0), e);
    }
  }

  private String maybeRelativeTo(String siteDir, String srcFile) {
    if ((srcFile == null) || (new File(siteDir).isAbsolute())) {
      return siteDir;
    }
    return new File(new File(srcFile).getParentFile(), siteDir).getPath();
  }

  public EndpointConfiguration makeEndpointConfig(String iotProject, String deviceId) {
    return makeEndpointConfig(iotProject, exeConfig, deviceId);
  }

  public Set<String> getDeviceIds() {
    checkState(sitePath != null, "sitePath not defined");
    File devicesFile = getDevicesDir();
    File[] files = Objects.requireNonNull(devicesFile.listFiles(),
        "no files in " + devicesFile.getAbsolutePath());
    return Arrays.stream(files).map(File::getName)
        .filter(device -> validDeviceDirectory(devicesFile, device)).collect(Collectors.toSet());
  }

  public Set<String> getTemplateIds() {
    requireNonNull(sitePath, "sitePath not defined");
    File templatesDir = getTemplatesDir();
    File[] files = templatesDir.listFiles();

    return ifNotNullGetElse(files,
        (templates) -> Arrays.stream(templates)
            .filter(template -> template.getName().endsWith(".json"))
            .map(template -> template.getName().split(".json")[0])
            .collect(Collectors.toSet()), Collections::emptySet);
  }

  public SiteMetadata loadSiteMetadata() {
    if (siteMetadata != null) {
      return siteMetadata;
    }

    ObjectNode siteMetadataObject = null;
    siteMetadataExceptionMap = new ExceptionMap("Site metadata validation");

    try {
      File siteMetadataFile = new File(new File(sitePath), SITE_METADATA_FILE);
      siteMetadataObject = loadFileRequired(ObjectNode.class, siteMetadataFile);
      siteMetadata = convertToStrict(SiteMetadata.class, siteMetadataObject);
    } catch (Exception e) {
      // Can't check getWarningsAsErrors now, since it might be set w/ a  flag after loading.
      siteMetadataExceptionMap.put(ExceptionCategory.site_metadata, e);
      siteMetadata = convertTo(SiteMetadata.class, siteMetadataObject);
    }

    ifNullThen(siteMetadata, () -> siteMetadata = new SiteMetadata());

    return siteMetadata;
  }

  public Metadata loadDeviceMetadata(String deviceId, boolean safeLoading,
      boolean upgradeMetadata) {

    try {
      File deviceDir = getDeviceDir(deviceId);
      File deviceMetadataFile = new File(deviceDir, METADATA_JSON);

      ObjectNode rawMetadata = loadFileRequired(ObjectNode.class, deviceMetadataFile);
      Map<String, Object> mergedMetadata = GeneralUtils.deepCopy(siteDefaults);
      JsonNode usedTemplate = rawMetadata.remove(TEMPLATE_KEY);
      if (usedTemplate != null) {
        String templateName = usedTemplate.asText();
        Map<String, Object> templateData = allTemplates.get(templateName);
        requireNonNull(templateData, String.format("template %s not found in %s",
            templateName, getTemplatesDir()));
        GeneralUtils.mergeObject(mergedMetadata, templateData);
      }
      GeneralUtils.mergeObject(mergedMetadata, asMap(rawMetadata));

      ObjectNode metadataObject = OBJECT_MAPPER_RAW.valueToTree(mergedMetadata);

      // We must upgrade before converting to Metadata, otherwise data is lost
      // The empty array fix (below) needs a Metadata typed metadata object
      // So upgrade if applicable, convert, apply the fixes
      // But, the `baseVersion` aka `upgraded from` is required for config downgrading
      // There is an `upgraded_from` - but this is where the upgrade errors are put
      if (upgradeMetadata) {
        try {
          new MessageUpgrader(METADATA_SCHEMA, metadataObject).upgrade(false);
        } catch (Exception e) {
          throw new RuntimeException("Error upgrading message " + friendlyStackTrace(e), e);
        }
      }

      return convertToStrict(Metadata.class, metadataObject);

    } catch (Exception e) {

      if (safeLoading) {
        // Adds safety by returning an exception rather than throwing it.
        return new MetadataException(getDeviceFile(deviceId, METADATA_JSON), e);
      } else {
        throw new RuntimeException("While loading device metadata for " + deviceId, e);
      }

    }

  }

  private void loadAllDeviceMetadata() {
    Set<String> deviceIds = getDeviceIds();
    allMetadata = deviceIds.stream().collect(toMap(key -> key, this::loadDeviceMetadataSafe));
    allDevices = deviceIds.stream().collect(toMap(key -> key, this::newCloudModel));
  }

  private Map<String, Map<String, Object>> loadAllTemplates() {
    Set<String> templateIds = getTemplateIds();
    return templateIds.stream().collect(toMap(id -> id, this::loadSingleTemplate));
  }

  /**
   * Loads a single template, throwing an exception if it's not found or invalid.
   */
  private Map<String, Object> loadSingleTemplate(String templateId) {
    File templateFile = getTemplateFile(templateId);
    Map<String, Object> templateMap = asMap(templateFile);

    if (templateMap == null) {
      throw new UncheckedIOException(
          new FileNotFoundException("Template file not found: " + templateFile.getAbsolutePath()));
    }

    return templateMap;
  }

  private CloudModel newCloudModel(String deviceId) {
    return new CloudModel();
  }

  public Metadata loadDeviceMetadataSafe(String deviceId) {
    return loadDeviceMetadata(deviceId, true, true);
  }

  public Metadata loadDeviceMetadata(String deviceId) {
    return loadDeviceMetadata(deviceId, false, true);
  }

  public File getDeviceDir(String deviceId) {
    return new File(getDevicesDir(), deviceId);
  }

  public File getDevicesDir() {
    return new File(new File(sitePath), "devices");
  }

  public File getTemplatesDir() {
    return new File(new File(sitePath), TEMPLATES_DIR);
  }

  public File getDeviceFile(String deviceId, String path) {
    return new File(getDeviceDir(deviceId), path);
  }

  public File getTemplateFile(String templateId) {
    return new File(getTemplatesDir(), templateId + ".json");
  }

  public Metadata getMetadata(String deviceId) {
    Metadata metadata = allMetadata.get(deviceId);
    return metadata instanceof MetadataException exception
        ? new MetadataException(exception) : deepCopy(metadata);
  }

  public Collection<CloudModel> allDevices() {
    return allDevices.values();
  }

  public Map<String, Metadata> allMetadata() {
    return allMetadata;
  }

  public Collection<String> allDeviceIds() {
    return allDevices.keySet();
  }

  public void forEachDeviceId(Consumer<String> consumer) {
    allDevices.keySet().forEach(consumer);
  }

  public void forEachMetadata(BiConsumer<String, Metadata> consumer) {
    ifNullThen(allMetadata, this::loadAllDeviceMetadata);
    allMetadata.forEach(consumer);
  }

  public void forEachMetadata(Consumer<Entry<String, Metadata>> consumer) {
    ifNullThen(allMetadata, this::loadAllDeviceMetadata);
    allMetadata.forEach((id, metadata) -> consumer.accept(Map.entry(id, metadata)));
  }

  public Stream<Entry<String, Metadata>> metadataStream() {
    ifNullThen(allMetadata, this::loadAllDeviceMetadata);
    return allMetadata.entrySet().stream();
  }

  private ExecutionConfiguration loadSiteConfig() {
    try {
      ExecutionConfiguration config = OBJECT_MAPPER.readValue(siteConf,
          ExecutionConfiguration.class);
      config.src_file = siteConf.getAbsolutePath();
      ifNotNullThen(specMatcher, () -> augmentConfig(config, specMatcher));
      return config;
    } catch (Exception e) {
      throw new RuntimeException("While reading config file " + siteConf.getAbsolutePath(), e);
    }
  }

  public void initialize() {
    loadAllDeviceMetadata();
  }

  public Auth_type getAuthType(String deviceId) {
    return allMetadata.get(deviceId).cloud.auth_type;
  }

  public String getDeviceKeyFile(String deviceId) {
    String gatewayId = findGateway(deviceId);
    String keyDevice = gatewayId != null ? gatewayId : deviceId;
    Optional<File> keyMatch = KEY_SUFFIXES.stream()
        .map(suffix -> format(KEY_SITE_PATH_FORMAT, sitePath,
            keyDevice, getDeviceKeyPrefix(keyDevice), suffix))
        .map(File::new)
        .filter(File::exists)
        .findFirst();
    return keyMatch.orElseThrow(() -> new IllegalStateException("No valid key file found"))
        .getAbsolutePath();
  }

  private String findGateway(String deviceId) {
    GatewayModel gateway = getMetadata(deviceId).gateway;
    return gateway != null ? gateway.gateway_id : null;
  }

  private String getDeviceKeyPrefix(String targetId) {
    Auth_type auth_type = getMetadata(targetId).cloud.auth_type;
    return auth_type.value().startsWith("RS") ? "rsa" : "ec";
  }

  /**
   * Get the site registry name.
   */
  public String getRegistryId() {
    return getRegistryActual(exeConfig);
  }

  /**
   * Get the reflect region name
   *
   * @return reflect region
   */
  public String getReflectRegion() {
    return exeConfig.reflect_region;
  }

  /**
   * Get the cloud region for this model.
   *
   * @return cloud region
   */
  public String getCloudRegion() {
    return exeConfig.cloud_region;
  }

  /**
   * Get the update topic for the site, if defined.
   *
   * @return update topic
   */
  public String getUpdateTopic() {
    return exeConfig.update_topic;
  }

  public CloudModel getDevice(String deviceId) {
    return allDevices.get(deviceId);
  }

  public String getSitePath() {
    return sitePath;
  }

  public String validatorKey() {
    return sitePath + "/reflector/rsa_private.pkcs8";
  }

  public File getDeviceWorkingDir(String deviceId) {
    File file = new File(sitePath + "/out/devices/" + deviceId);
    if (!file.exists()) {
      file.mkdirs();
    }
    if (!file.isDirectory()) {
      throw new RuntimeException(
          "Device working dir is not a valid directory: " + file.getAbsolutePath());
    }
    return file;
  }

  public ExecutionConfiguration getExecutionConfiguration() {
    return exeConfig;
  }

  public File getSiteFile(String path) {
    return new File(sitePath, path);
  }

  public File getExtraDir(String deviceId) {
    return new File(getExtrasDir(), deviceId);
  }

  public File getExtrasDir() {
    return getSiteFile(EXTRAS_DIR);
  }

  public boolean deviceExists(String deviceId) {
    return getDeviceDir(deviceId).exists();
  }

  private Matcher extractSpec(String projectSpec) {
    if (projectSpec == null) {
      return null;
    }
    Matcher matcher = SPEC_PATTERN.matcher(projectSpec);
    if (!matcher.matches()) {
      throw new RuntimeException(
          format("Project spec %s does not match expression %s", projectSpec,
              SPEC_PATTERN.pattern()));
    }
    return matcher;
  }

  public File getReflectorDir() {
    return getSiteFile(REFLECTOR_DIR);
  }

  public String getDevicePassword(String deviceId) {
    return sha256(getFileBytes(getDeviceKeyFile(deviceId))).substring(0, 8);
  }

  public String getSiteName() {
    return exeConfig.site_name;
  }

  public void resetRegistryId(String altRegistry) {
    String previousId = getRegistryId();
    getExecutionConfiguration().registry_id = altRegistry;
    getExecutionConfiguration().alt_registry = null;
    String newId = getRegistryId();
    checkState(!newId.equals(previousId), "resetting to unchanged registry id");
    System.err.printf("Switched target registry from %s to %s%n", previousId, newId);
  }

  public void setStrictWarnings() {
    siteMetadata.strict_warnings = true;
  }

  public boolean getStrictWarnings() {
    return isTrue(siteMetadata.strict_warnings);
  }

  public void createNewDevice(String deviceId, Metadata metadata) {
    updateMetadataRaw(deviceId, metadata);
  }

  public boolean updateMetadata(String deviceId, Metadata updateTo) {
    Metadata current = allMetadata.get(deviceId);
    String currentString = stringifyTerse(current);
    String updateString = stringifyTerse(updateTo);
    if (currentString.equals(updateString)) {
      return false;
    }
    updateMetadataRaw(deviceId, updateTo);
    return true;
  }

  private void updateMetadataRaw(String deviceId, Metadata metadata) {
    File metadataFile = getDeviceFile(deviceId, METADATA_JSON);
    System.err.println("Writing device metadata file " + metadataFile);
    metadataFile.getParentFile().mkdirs();
    writeFile(metadata, metadataFile);
    allMetadata.put(deviceId, metadata);
  }

  public static class MetadataException extends Metadata {

    public final File file;
    public final Exception exception;

    public MetadataException(File deviceMetadataFile, Exception metadataException) {
      file = deviceMetadataFile;
      exception = metadataException;
    }

    public MetadataException(MetadataException orig) {
      this(orig.file, orig.exception);
    }
  }

  public static class ClientInfo {

    public String iotProject;
    public String cloudRegion;
    public String registryId;
    public String deviceId;
  }
}
