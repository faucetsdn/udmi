package daq.pubber;

import com.google.udmi.util.SiteModel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import udmi.lib.client.DeviceManager;
import udmi.lib.client.DiscoveryManager;
import udmi.lib.client.GatewayManager;
import udmi.lib.client.LocalnetManager;
import udmi.lib.client.PointsetManager;
import udmi.lib.client.SubBlockManager;
import udmi.lib.client.SystemManager;
import udmi.lib.intf.ManagerHost;
import udmi.schema.Config;
import udmi.schema.PubberConfiguration;

/**
 * Uber-manager for a complete device.
 */
public class PubberDeviceManager extends PubberManager implements DeviceManager {

  private final PubberPointsetManager pointsetManager;
  private final PubberSystemManager systemManager;
  private final PubberLocalnetManager localnetManager;
  private final PubberGatewayManager gatewayManager;
  private final PubberDiscoveryManager discoveryManager;
  private final List<SubBlockManager> subManagers;

  /**
   * Create a new instance.
   */
  public PubberDeviceManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
    systemManager = new PubberSystemManager(host, configuration);
    localnetManager = new PubberLocalnetManager(host, configuration);
    pointsetManager = new PubberPointsetManager(host, configuration);
    discoveryManager = new PubberDiscoveryManager(host, configuration, this);
    gatewayManager = new PubberGatewayManager(host, configuration);
    subManagers = Arrays.asList(
            systemManager, localnetManager, pointsetManager, discoveryManager, gatewayManager);
    Collections.reverse(subManagers);
  }

  @Override
  public void updateConfig(Config config) {
    DeviceManager.super.updateConfig(config);
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
    subManagers.forEach(SubBlockManager::shutdown);
  }

  /**
   * Stop periodic senders.
   */
  @Override
  public void stop() {
    subManagers.forEach(SubBlockManager::stop);
  }

  /**
   * Set the site model.
   */
  protected void setSiteModel(SiteModel siteModel) {
    discoveryManager.setSiteModel(siteModel);
    gatewayManager.setSiteModel(siteModel);
    localnetManager.setSiteModel(siteModel);
  }
}
