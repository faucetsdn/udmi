package com.google.daq.mqtt.util;

import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.getTimestampString;

import java.util.HashMap;
import java.util.List;
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

  private PointsetConfig getDevicePointsetConfig() {
    PointsetConfig pointsetConfig = new PointsetConfig();
    boolean excludeUnits = isTrue(metadata.pointset.exclude_units_from_config);
    boolean excludePoints = isTrue(metadata.pointset.exclude_points_from_config);
    if (!excludePoints) {
      pointsetConfig.points = new HashMap<>();
      metadata.pointset.points.forEach(
          (metadataKey, value) ->
              pointsetConfig.points.computeIfAbsent(
                  metadataKey, configKey -> configFromMetadata(value, excludeUnits)));
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

  PointPointsetConfig configFromMetadata(PointPointsetModel metadata, boolean excludeUnits) {
    PointPointsetConfig pointConfig = new PointPointsetConfig();
    pointConfig.units = excludeUnits ? null : metadata.units;
    pointConfig.ref = metadata.ref;
    if (Boolean.TRUE.equals(metadata.writable)) {
      pointConfig.set_value = metadata.baseline_value;
    }
    return pointConfig;
  }

  private LocalnetConfig getDeviceLocalnetConfig() {
    LocalnetConfig localnetConfig = new LocalnetConfig();
    localnetConfig.families = metadata.localnet.families;
    return localnetConfig;
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

  public boolean hasGateway() {
    return metadata != null && metadata.gateway != null && metadata.gateway.gateway_id != null;
  }

  public List<String> getProxyDevicesList() {
    return isGateway() ? metadata.gateway.proxy_ids : null;
  }

  public String getUpdatedTimestamp() {
    return getTimestampString(metadata.timestamp);
  }

}
