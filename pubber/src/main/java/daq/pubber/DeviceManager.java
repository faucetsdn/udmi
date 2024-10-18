package daq.pubber;

import com.google.udmi.util.SiteModel;
import udmi.lib.ManagerBase;
import udmi.lib.ManagerHost;
import udmi.lib.client.DeviceManagerClient;
import udmi.lib.client.DiscoveryManagerClient;
import udmi.lib.client.GatewayManagerClient;
import udmi.lib.client.LocalnetManagerClient;
import udmi.lib.client.PointsetManagerClient;
import udmi.lib.client.SystemManagerClient;
import udmi.schema.PubberConfiguration;

/**
 * Uber-manager for a complete device.
 */
public class DeviceManager extends ManagerBase implements DeviceManagerClient {

  private final PointsetManagerClient pointsetManager;
  private final SystemManagerClient systemManager;
  private final LocalnetManagerClient localnetManager;
  private final GatewayManagerClient gatewayManager;
  private final DiscoveryManagerClient discoveryManager;

  /**
   * Create a new instance.
   */
  public DeviceManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
    systemManager = new SystemManager(host, configuration);
    pointsetManager = new PointsetManager(host, configuration);
    localnetManager = new LocalnetManager(host, configuration);
    gatewayManager = new GatewayManager(host, configuration);
    discoveryManager = new DiscoveryManager(host, configuration, this);
  }

  @Override
  public PointsetManagerClient getPointsetManager() {
    return pointsetManager;
  }

  @Override
  public SystemManagerClient getSystemManager() {
    return systemManager;
  }

  @Override
  public LocalnetManagerClient getLocalnetManager() {
    return localnetManager;
  }

  @Override
  public GatewayManagerClient getGatewayManager() {
    return gatewayManager;
  }

  @Override
  public DiscoveryManagerClient getDiscoveryManager() {
    return discoveryManager;
  }

  /**
   * Shutdown everything, including sub-managers.
   */
  @Override
  public void shutdown() {
    getGatewayManager().shutdown();
    getLocalnetManager().shutdown();
    getPointsetManager().shutdown();
    getSystemManager().shutdown();
  }


  /**
   * Stop periodic senders.
   */
  @Override
  public void stop() {
    getGatewayManager().stop();
    getLocalnetManager().stop();
    getPointsetManager().stop();
    getSystemManager().stop();
  }

  /**
   * Set the site model.
   */
  protected void setSiteModel(SiteModel siteModel) {
    getDiscoveryManager().setSiteModel(siteModel);
    getGatewayManager().setSiteModel(siteModel);
    getLocalnetManager().setSiteModel(siteModel);
  }

}
