package com.google.daq.mqtt.util;

import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.util.NetworkFamily.NAMED_FAMILIES;
import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.GeneralUtils.ifNotNullThrow;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.getTimestampString;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import java.util.HashMap;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import udmi.schema.Common.ProtocolFamily;
import udmi.schema.Config;
import udmi.schema.GatewayConfig;
import udmi.schema.LocalnetConfig;
import udmi.schema.Metadata;
import udmi.schema.Operation;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointsetConfig;
import udmi.schema.SystemConfig;

/**
 * Container class for working with generated UDMI configs (from device metadata).
 */
public class ConfigGenerator {

  public static final String GENERATED_CONFIG_JSON = "generated_config.json";
  public static final ProtocolFamily DEFAULT_FAMILY = ProtocolFamily.VENDOR;

  private final Metadata metadata;

  public ConfigGenerator(Metadata metadata) {
    this.metadata = metadata;
  }

  public static ConfigGenerator configFrom(Metadata metadata) {
    return new ConfigGenerator(metadata);
  }

  /**
   * Return a complete UDMI Config message object.
   */
  public Config deviceConfig() {
    Config config = new Config();
    config.timestamp = metadata.timestamp;
    config.version = metadata.version;
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
    system.min_loglevel = metadata.system.min_loglevel;
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
    } else if (isProxied()) {
      final GatewayConfig configVar = gatewayConfig;
      ifNotNullThen(metadata.gateway.target, target -> {
        ifNotNullThrow(target.addr, "metadata.gateway.target.addr should not be defined");
        configVar.target = deepCopy(target);
        configVar.target.addr = ifNotNullGet(target.family, this::getLocalnetAddr);
      });
    } else {
      throw new RuntimeException("gateway block is neither gateway nor proxied");
    }
    return gatewayConfig;
  }

  private String getLocalnetAddr(ProtocolFamily rawFamily) {
    ProtocolFamily family = ofNullable(rawFamily).orElse(DEFAULT_FAMILY);
    String address = catchToNull(() -> metadata.localnet.families.get(family).addr);
    return requireNonNull(address,
        format("metadata.localnet.families[%s].addr undefined", family.value()));
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
    ProtocolFamily rawFamily = catchToNull(() -> metadata.gateway.target.family);
    ProtocolFamily family = ofNullable(rawFamily).orElse(DEFAULT_FAMILY);

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
    NAMED_FAMILIES.get(family).refValidator(pointRef);
    return pointRef;
  }

  private String getGatewayId() {
    return catchToNull(() -> metadata.gateway.gateway_id);
  }

  private LocalnetConfig getDeviceLocalnetConfig() {
    return null;
  }

  /**
   * Indicate if this is a gateway device.
   */
  public boolean isGateway() {
    return metadata != null
        && metadata.gateway != null
        && metadata.gateway.proxy_ids != null
        && !metadata.gateway.proxy_ids.isEmpty();
  }

  public boolean isProxied() {
    return getGatewayId() != null;
  }

  public List<String> getProxyDevicesList() {
    return isGateway() ? metadata.gateway.proxy_ids : null;
  }

  public String getUpdatedTimestamp() {
    return getTimestampString(metadata.timestamp);
  }

}










































