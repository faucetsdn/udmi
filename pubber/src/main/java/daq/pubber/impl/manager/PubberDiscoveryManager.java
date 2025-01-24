package daq.pubber.impl.manager;


import com.google.udmi.util.SiteModel;
import daq.pubber.impl.PubberFeatures;
import daq.pubber.impl.PubberManager;
import java.util.HashMap;
import java.util.Map;
import udmi.lib.client.manager.DeviceManager;
import udmi.lib.client.manager.DiscoveryManager;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.FeatureDiscovery;
import udmi.schema.PointPointsetModel;
import udmi.schema.PubberConfiguration;
import udmi.schema.SystemDiscoveryData;

/**
 * Manager wrapper for discovery functionality in pubber.
 */
public class PubberDiscoveryManager extends PubberManager implements DiscoveryManager {

  private final PubberDeviceManager deviceManager;
  private DiscoveryState discoveryState;
  private DiscoveryConfig discoveryConfig;

  private SiteModel siteModel;

  public PubberDiscoveryManager(ManagerHost host, PubberConfiguration configuration,
      PubberDeviceManager deviceManager) {
    super(host, configuration);
    this.deviceManager = deviceManager;
  }

  @Override
  public DeviceManager getDeviceManager() {
    return deviceManager;
  }

  @Override
  public DiscoveryState getDiscoveryState() {
    return discoveryState;
  }

  @Override
  public void setDiscoveryState(DiscoveryState discoveryState) {
    this.discoveryState = discoveryState;
  }

  @Override
  public DiscoveryConfig getDiscoveryConfig() {
    return discoveryConfig;
  }

  @Override
  public void setDiscoveryConfig(DiscoveryConfig discoveryConfig) {
    this.discoveryConfig = discoveryConfig;
  }

  @Override
  public Map<String, FeatureDiscovery> getFeatures() {
    return PubberFeatures.getFeatures();
  }

  public void setSiteModel(SiteModel siteModel) {
    this.siteModel = siteModel;
  }

  @Override
  public Map<String, PointPointsetModel> enumeratePoints(String deviceId) {
    return siteModel.getMetadata(deviceId).pointset.points;
  }

  @Override
  public void postDiscoveryProcess(String deviceId, DiscoveryEvents discoveryEvent) {
    discoveryEvent.system = new SystemDiscoveryData();
    discoveryEvent.system.ancillary = new HashMap<>();
    discoveryEvent.system.ancillary.put("device-name", deviceId);
  }
}
