package com.google.daq.mqtt.registrar;

import java.util.*;

public class UdmiSchema {
  static class Envelope {
    public String deviceId;
    public String deviceNumId;
    public String deviceRegistryId;
    public String projectId;
    public final String subFolder = LocalDevice.METADATA_SUBFOLDER;
  }

  public static class Metadata {
    public PointsetMetadata pointset;
    public SystemMetadata system;
    public GatewayMetadata gateway;
    public LocalnetMetadata localnet;
    public CloudMetadata cloud;
    public Integer version;
    public Date timestamp;
    public String hash;
  }

  static class CloudMetadata {
    public String auth_type;
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

  static class Config {
    public Integer version = 1;
    public Date timestamp;
    public GatewayConfig gateway;
    public LocalnetConfig localnet;
    public PointsetConfig pointset;
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

  static class PointConfig {
    public String ref;

    static PointConfig fromRef(String ref) {
      PointConfig pointConfig = new PointConfig();
      pointConfig.ref = ref;
      return pointConfig;
    }
  }

  static class LocalnetMetadata {
    public Map<String, LocalnetSubsystem> subsystem;
  }

  static class LocalnetSubsystem {
    public String local_id;
  }

  public static class PointsetMessage {
    public Map<String, PointData> points = new TreeMap<>();
  }

  static class PointData {

  }
}
