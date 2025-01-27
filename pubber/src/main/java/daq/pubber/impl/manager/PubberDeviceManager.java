package daq.pubber.impl.manager;

import com.google.udmi.util.SiteModel;
import daq.pubber.impl.PubberManager;
import java.util.ArrayList;
import java.util.List;
import udmi.lib.client.manager.DeviceManager;
import udmi.lib.client.manager.DiscoveryManager;
import udmi.lib.client.manager.GatewayManager;
import udmi.lib.client.manager.LocalnetManager;
import udmi.lib.client.manager.PointsetManager;
import udmi.lib.client.manager.SystemManager;
import udmi.lib.intf.ManagerHost;
import udmi.lib.intf.SubBlockManager;
import udmi.schema.PubberConfiguration;

/**
 * Uber-manager for a complete device.
 */
public class PubberDeviceManager extends PubberManager implements DeviceManager {

  private final PubberSystemManager systemManager;
  private final PubberLocalnetManager localnetManager;
  private final PubberPointsetManager pointsetManager;
  private final PubberDiscoveryManager discoveryManager;
  private final PubberGatewayManager gatewayManager;
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

  @Override
  public SystemManager getSystemManager() {
    return systemManager;
  }

  @Override
  public LocalnetManager getLocalnetManager() {
    return localnetManager;
  }

  @Override
  public PointsetManager getPointsetManager() {
    return pointsetManager;
  }

  @Override
  public DiscoveryManager getDiscoveryManager() {
    return discoveryManager;
  }

  @Override
  public GatewayManager getGatewayManager() {
    return gatewayManager;
  }

  @Override
  public List<SubBlockManager> getSubmanagers() {
    return subManagers;
  }

  @Override
  public void shutdown() {
    DeviceManager.super.shutdown();
  }

  @Override
  public void stop() {
    DeviceManager.super.stop();
  }

  /**
   * Set the site model.
   */
  public void setSiteModel(SiteModel siteModel) {
    discoveryManager.setSiteModel(siteModel);
    gatewayManager.setSiteModel(siteModel);
    localnetManager.setSiteModel(siteModel);
  }
}
