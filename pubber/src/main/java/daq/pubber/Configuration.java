package daq.pubber;

/**
 */
public class Configuration {
  public String bridgeHostname = "mqtt.googleapis.com";
  public String bridgePort = "443";
  public String projectId;
  public String cloudRegion;
  public String registryId;
  public String gatewayId;
  public String deviceId;
  public String sitePath;
  public String keyFile = "local/rsa_private.pkcs8";
  public byte[] keyBytes;
  public String algorithm = "RS256";
  public Object extraField;
  public String serialNo;
}
