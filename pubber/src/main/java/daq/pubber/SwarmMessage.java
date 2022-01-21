package daq.pubber;

import udmi.schema.Metadata;

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
