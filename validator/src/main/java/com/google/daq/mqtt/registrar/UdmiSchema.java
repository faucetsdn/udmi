package com.google.daq.mqtt.registrar;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class UdmiSchema {

  static class Envelope {
    public String deviceId;
    public String deviceNumId;
    public String deviceRegistryId;
    public String projectId;
    public String subFolder;
  }

  public static class UdmiBase {
    public Integer version = 1;
    public Date timestamp;
  }

  public static class Metadata extends UdmiBase {
    public PointsetMetadata pointset;
    public SystemMetadata system;
    public GatewayMetadata gateway;
    public LocalnetMetadata localnet;
    public CloudMetadata cloud;
    public String hash;
  }

  static class CloudMetadata {
    public String auth_type;
    public boolean device_key;
  }

  public static class PointsetMetadata {
    public Map<String, PointMetadata> points;
  }

  static class SystemMetadata {
    public LocationMetadata location;
    public PhysicalTagMetadata physical_tag;
  }

  static class GatewayMetadata {
    public String gateway_id;
    public List<String> proxy_ids;
    public String subsystem;
  }

  static class PointMetadata {
    public String units;
    public String ref;
  }

  static class LocationMetadata {
    public String site;
    public String section;
    public Object position;
  }

  static class PhysicalTagMetadata {
    public AssetMetadata asset;
  }

  static class AssetMetadata {
    public String guid;
    public String name;
    public String site;
  }

  public static class Config extends UdmiBase {
    public GatewayConfig gateway;
    public LocalnetConfig localnet;
    public PointsetConfig pointset;
    public SystemConfig system;
  }

  static class GatewayConfig {
    public List<String> proxy_ids = new ArrayList<>();
  }

  static class LocalnetConfig {
    public Map<String, LocalnetSubsystem> subsystems = new TreeMap<>();
  }

  static class PointsetConfig {
    public Map<String, PointConfig> points = new TreeMap<>();
  }

  static class SystemConfig {
  }

  static class PointConfig {
    public String ref;
    public String units;

    static PointConfig fromMetadata(PointMetadata metadata) {
      PointConfig pointConfig = new PointConfig();
      pointConfig.ref = metadata.ref;
      pointConfig.units = metadata.units;
      return pointConfig;
    }
  }

  static class LocalnetMetadata {
    public Map<String, LocalnetSubsystem> subsystem;
  }

  static class LocalnetSubsystem {
    public String local_id;
  }

  public static class PointsetMessage extends UdmiBase {
    public Map<String, PointData> points = new TreeMap<>();
  }

  static class PointData {
    public Object present_value;
  }
}
