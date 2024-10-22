package daq.pubber;

import com.google.udmi.util.SiteModel;
import udmi.lib.base.ManagerBase;
import udmi.lib.client.DeviceManager;
import udmi.lib.client.DiscoveryManager;
import udmi.lib.client.GatewayManager;
import udmi.lib.client.LocalnetManager;
import udmi.lib.client.PointsetManager;
import udmi.lib.client.SystemManager;
import udmi.lib.intf.ManagerHost;
import udmi.schema.PubberConfiguration;

/**
 * Uber-manager for a complete device.
 */
public class PubberDeviceManager extends PubberManager implements DeviceManager {

  private final PointsetManager pointsetManager;
  private final SystemManager systemManager;
  private final LocalnetManager localnetManager;
  private final GatewayManager gatewayManager;
  private final DiscoveryManager discoveryManager;

  /**
   * Create a new instance.
   */
  public PubberDeviceManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
    systemManager = new PubberSystemManager(host, configuration);
    pointsetManager = new PubberPointsetManager(host, configuration);
    localnetManager = new PubberLocalnetManager(host, configuration);
    gatewayManager = new PubberGatewayManager(host, configuration);
    discoveryManager = new PubberDiscoveryManager(host, configuration, this);
  }

  @Override
  public PointsetManager getPointsetManager() {
    return pointsetManager;
  }

  @Override
  public SystemManager getSystemManager() {
    return systemManager;
  }

  @Override
  public LocalnetManager getLocalnetManager() {
    return localnetManager;
  }

  @Override
  public GatewayManager getGatewayManager() {
    return gatewayManager;
  }

  @Override
  public DiscoveryManager getDiscoveryManager() {
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
