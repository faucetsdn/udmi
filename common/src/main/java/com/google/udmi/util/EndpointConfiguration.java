package daq.pubber;

/**
 * Configuration for the connection endpoint.
 */
public class EndpointConfiguration {

  public String bridgeHostname = "mqtt.googleapis.com";
  public String bridgePort = "443";
  public String cloudRegion;
  public String projectId;
  public String registryId;
}
