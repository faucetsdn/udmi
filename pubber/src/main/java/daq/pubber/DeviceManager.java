package daq.pubber;

import com.google.udmi.util.SiteModel;
import udmi.lib.ManagerBase;
import udmi.lib.ManagerHost;
import udmi.lib.client.DeviceManagerProvider;
import udmi.lib.client.DiscoveryManagerProvider;
import udmi.lib.client.GatewayManagerProvider;
import udmi.lib.client.LocalnetManagerProvider;
import udmi.lib.client.PointsetManagerProvider;
import udmi.lib.client.SystemManagerProvider;
import udmi.schema.PubberConfiguration;

/**
 * Uber-manager for a complete device.
 */
public class DeviceManager extends ManagerBase implements DeviceManagerProvider {

  private final PointsetManagerProvider pointsetManager;
  private final SystemManagerProvider systemManager;
  private final LocalnetManagerProvider localnetManager;
  private final GatewayManagerProvider gatewayManager;
  private final DiscoveryManagerProvider discoveryManager;

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
  public PointsetManagerProvider getPointsetManager() {
    return pointsetManager;
  }

  @Override
  public SystemManagerProvider getSystemManager() {
    return systemManager;
  }

  @Override
  public LocalnetManagerProvider getLocalnetManager() {
    return localnetManager;
  }

  @Override
  public GatewayManagerProvider getGatewayManager() {
    return gatewayManager;
  }

  @Override
  public DiscoveryManagerProvider getDiscoveryManager() {
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
