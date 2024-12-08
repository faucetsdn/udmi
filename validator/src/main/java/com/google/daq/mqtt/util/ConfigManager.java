package com.google.daq.mqtt.util;

import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.util.FamilyProvider.NAMED_FAMILIES;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotNullThrow;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.loadFileString;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import com.google.common.collect.ImmutableList;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import udmi.lib.ProtocolFamily;
import udmi.schema.Config;
import udmi.schema.FamilyLocalnetConfig;
import udmi.schema.GatewayConfig;
import udmi.schema.LocalnetConfig;
import udmi.schema.Metadata;
import udmi.schema.Operation;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointsetConfig;
import udmi.schema.SystemConfig;
import udmi.util.SchemaVersion;

/**
 * Container class for working with generated UDMI configs (from device metadata).
 */
public class ConfigManager {

  public static final String GENERATED_CONFIG_JSON = "generated_config.json";
  private static final String DEFAULT_FAMILY = ProtocolFamily.VENDOR;
  private static final String CONFIG_DIR = "config";
  private static final String JSON_STRING_FMT = "\"%s\"";
  private final Metadata metadata;
  private final String deviceId;
  private final SiteModel siteModel;

  /**
   * Initiates ConfigManager for the given device at the given file location.
   *
   * @param metadata  Device metadata
   * @param deviceId  Device ID
   * @param siteModel Site model
   */
  public ConfigManager(Metadata metadata, String deviceId, SiteModel siteModel) {
    this.metadata = metadata;
    this.deviceId = deviceId;
    this.siteModel = siteModel;
  }

  public static ConfigManager configFrom(Metadata metadata) {
    return new ConfigManager(metadata, null, null);
  }

  public static ConfigManager configFrom(Metadata metadata, String deviceId, SiteModel siteModel) {
    return new ConfigManager(metadata, deviceId, siteModel);
  }

  private boolean isStaticConfig() {
    return catchToNull(() -> metadata.cloud.config.static_file) != null;
  }

  private String readStaticConfigFromFile(String fileName) {
    try {
      File deviceDir = siteModel.getDeviceDir(deviceId);
      File configDir = new File(deviceDir, CONFIG_DIR);
      File configFile = new File(configDir, fileName);
      String canonicalPath = configFile.getCanonicalPath();
      String configDirPath = configDir.getCanonicalPath();
      if (!canonicalPath.startsWith(configDirPath)) {
        throw new IllegalArgumentException();
      }
      return loadFileString(configFile);
    } catch (Exception e) {
      throw new RuntimeException(String.format("While reading config file: %s", fileName), e);
    }
  }

  public Config deviceConfig() {
    return generateDeviceConfig();
  }

  /**
   * Whether the device config be downgraded.
   *
   * @return yes/no
   */
  public boolean shouldBeDowngraded() {
    return !isStaticConfig();
  }

  /**
   * Returns a JSON representation of the device config, allowing for any static config overrides.
   *
   * @return JSON Object of Device Config
   */
  public Object deviceConfigJson() {
    if (isStaticConfig()) {
      return format(JSON_STRING_FMT, readStaticConfigFromFile(metadata.cloud.config.static_file));
    } else {
      return deviceConfig();
    }
  }


  /**
   * Return a complete UDMI Config message object.
   */
  public Config generateDeviceConfig() {
    if (metadata == null) {
      throw new RuntimeException("config could not be generated due to metadata errors");
    }
    Config config = new Config();
    config.timestamp = metadata.timestamp;
    config.version = SchemaVersion.CURRENT.key();
    config.system = getSystemConfig();
    config.gateway = getGatewayConfig();
    config.pointset = getDevicePointsetConfig();
    config.localnet = getDeviceLocalnetConfig();
    return config;
  }

  @NotNull
  private SystemConfig getSystemConfig() {
    SystemConfig system;
    system = new SystemConfig();
    system.operation = new Operation();
    if (catchToNull(() -> metadata.system.min_loglevel) != null) {
      system.min_loglevel = metadata.system.min_loglevel;
    }
    if (catchToNull(() -> metadata.system.metrics_rate_sec) != null) {
      system.metrics_rate_sec = metadata.system.metrics_rate_sec;
    }
    return system;
  }

  @NotNull
  private GatewayConfig getGatewayConfig() {
    if (metadata.gateway == null) {
      return null;
    }

    GatewayConfig gatewayConfig = new GatewayConfig();
    gatewayConfig.proxy_ids = null;
    if (isGateway()) {
      gatewayConfig.proxy_ids = getProxyDevicesList();
    }

    final GatewayConfig configVar = gatewayConfig;
    ifNotNullThen(metadata.gateway.target, target -> {
      ifNotNullThrow(target.addr, "metadata.gateway.target.addr should not be defined");
      configVar.target = deepCopy(target);
      configVar.target.addr = ifNotNullGet(target.family, this::getLocalnetAddr);
    });

    return gatewayConfig;
  }

  private String getLocalnetAddr(String rawFamily) {
    String family = ofNullable(rawFamily).orElse(DEFAULT_FAMILY);
    String address = catchToNull(() -> metadata.localnet.families.get(family).addr);
    return requireNonNull(address, format("metadata.localnet.families[%s].addr undefined", family));
  }

  private PointsetConfig getDevicePointsetConfig() {
    if (metadata.pointset == null) {
      return null;
    }

    PointsetConfig pointsetConfig = new PointsetConfig();
    boolean excludeUnits = isTrue(metadata.pointset.exclude_units_from_config);
    boolean excludePoints = isTrue(metadata.pointset.exclude_points_from_config);
    if (!excludePoints) {
      pointsetConfig.points = new HashMap<>();
      metadata.pointset.points.forEach(
          (metadataKey, value) ->
              pointsetConfig.points.computeIfAbsent(
                  metadataKey, configKey -> configFromMetadata(configKey, value, excludeUnits)));
    }

    // Copy selected MetadataPointset properties into PointsetConfig.
    if (metadata.pointset.sample_limit_sec != null) {
      pointsetConfig.sample_limit_sec = metadata.pointset.sample_limit_sec;
    }
    if (metadata.pointset.sample_rate_sec != null) {
      pointsetConfig.sample_rate_sec = metadata.pointset.sample_rate_sec;
    }
    return pointsetConfig;
  }

  PointPointsetConfig configFromMetadata(String configKey, PointPointsetModel metadata,
      boolean excludeUnits) {
    try {
      PointPointsetConfig pointConfig = new PointPointsetConfig();
      pointConfig.units = excludeUnits ? null : metadata.units;
      pointConfig.ref = pointConfigRef(metadata);
      if (Boolean.TRUE.equals(metadata.writable)) {
        pointConfig.set_value = metadata.baseline_value;
      }
      return pointConfig;
    } catch (Exception e) {
      throw new RuntimeException("While converting point " + configKey, e);
    }
  }

  private String pointConfigRef(PointPointsetModel model) {
    String pointRef = model.ref;
    String rawFamily = catchToNull(() -> metadata.gateway.target.family);
    String family = ofNullable(rawFamily).orElse(DEFAULT_FAMILY);

    if (!isProxied()) {
      return pointRef;
    }

    requireNonNull(family, "missing gateway.target.family designation");
    checkState(NAMED_FAMILIES.containsKey(family), "gateway.target.family unknown: " + family);
    ifNotNullThrow(catchToNull(() -> metadata.gateway.target.addr),
        "gateway.target.addr field should not be defined");
    ifNotNullThen(rawFamily, raw ->
        requireNonNull(catchToNull(() -> metadata.localnet.families.get(family).addr),
            format("metadata.localnet.families.[%s].addr not defined", family)));

    NAMED_FAMILIES.get(family).validateRef(pointRef);
    return pointRef;
  }

  private String getGatewayId() {
    return catchToNull(() -> metadata.gateway.gateway_id);
  }

  private LocalnetConfig getDeviceLocalnetConfig() {
    if (metadata.localnet == null) {
      return null;
    }
    LocalnetConfig localnetConfig = new LocalnetConfig();
    localnetConfig.families = new HashMap<>();
    metadata.localnet.families.keySet()
        .forEach(family -> localnetConfig.families.put(family, new FamilyLocalnetConfig()));
    return localnetConfig;
  }

  /**
   * Indicate if this is a gateway device.
   */
  public boolean isGateway() {
    return metadata != null
        && metadata.gateway != null
        && metadata.gateway.gateway_id == null;
  }

  public boolean isProxied() {
    return getGatewayId() != null;
  }

  /**
   * Get a list of proxy devices as per configuration.
   */
  public List<String> getProxyDevicesList() {
    List<String> proxyIds = ofNullable(catchToNull(() -> metadata.gateway.proxy_ids)).orElse(
        ImmutableList.of());
    return isGateway() ? proxyIds : null;
  }

  public String getUpdatedTimestamp() {
    return isoConvert(metadata.timestamp);
  }

}
