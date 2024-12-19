package daq.pubber;

import com.google.udmi.util.SiteModel;
import java.util.ArrayList;
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
  private final List<SubBlockManager> subManagers = new ArrayList<>();

  /**
   * Create a new instance.
   * Managers are logically ordered to ensure proper initialization and shutdown.
   * Stop/shutdown order is the reverse of the boot order.
   * SystemManager should be created first b/c logging dependency.
   * The remaining managers are placed in a logical boot/shutdown order.
   */
  public PubberDeviceManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
    systemManager = addManager(new PubberSystemManager(host, configuration));
    localnetManager = addManager(new PubberLocalnetManager(host, configuration));
    pointsetManager = addManager(new PubberPointsetManager(host, configuration));
    discoveryManager = addManager(new PubberDiscoveryManager(host, configuration, this));
    gatewayManager = addManager(new PubberGatewayManager(host, configuration));
  }

  private <T extends SubBlockManager> T addManager(T manager) {
    // Keep the resulting list in reverse order for proper shutdown semantics.
    subManagers.add(0, manager);
    return manager;
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
