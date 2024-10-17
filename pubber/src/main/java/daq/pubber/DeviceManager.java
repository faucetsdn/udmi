package daq.pubber;

import com.google.udmi.util.SiteModel;
import daq.pubber.client.DeviceManagerProvider;
import daq.pubber.client.DiscoveryManagerProvider;
import daq.pubber.client.GatewayManagerProvider;
import daq.pubber.client.LocalnetManagerProvider;
import daq.pubber.client.PointsetManagerProvider;
import daq.pubber.client.SystemManagerProvider;
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
