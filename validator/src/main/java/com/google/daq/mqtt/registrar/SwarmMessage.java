package com.google.daq.mqtt.registrar;

import udmi.schema.Metadata;

/**
 * Message for swarm device management.
 */
@SuppressWarnings("MemberName")
public class SwarmMessage {

  public String key_base64;
  public Metadata device_metadata;

  /**
   * Attribute map for this message.
   */
  public static class Attributes {

    public String deviceId;
    public String deviceRegistryLocation;
    public String deviceRegistryId;
    public String projectId;
    public String subFolder = "swarm";
  }
}
