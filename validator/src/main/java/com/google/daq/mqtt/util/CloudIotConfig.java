package com.google.daq.mqtt.util;

/**
 * Data class encapsulating cloud-based configuration.
 */
@SuppressWarnings("MemberName")
public class CloudIotConfig {
  public String registry_id;
  public String cloud_region;
  public String site_name;
  public String alt_project;
  public String alt_registry;
  public boolean block_unknown;
}
