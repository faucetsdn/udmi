package daq.pubber;

import udmi.schema.Metadata;

/**
 * Message for handling device swarms (k8s pubber cluster).
 */
@SuppressWarnings("MemberName")
public class SwarmMessage {
  public String key_base64;
  public Metadata device_metadata;

  static class Attributes {
    public String deviceId;
    public String deviceRegistryLocation;
    public String deviceRegistryId;
    public String projectId;
    public String subFolder;
  }
}
