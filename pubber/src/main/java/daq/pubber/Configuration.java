package daq.pubber;


/**
 * General bucket of pubber configuration information.
 */
public class Configuration {
  public EndpointConfiguration endpoint = new EndpointConfiguration();
  public String gatewayId;
  public String deviceId;
  public String sitePath;
  public String keyFile = "local/rsa_private.pkcs8";
  public byte[] keyBytes;
  public String algorithm = "RS256";
  public String serialNo;
  public String macAddr;
  public ConfigurationOptions options = new ConfigurationOptions();
}
