package daq.pubber.impl.manager;

import static com.google.udmi.util.GeneralUtils.catchToNull;

import com.google.udmi.util.SiteModel;
import daq.pubber.impl.PubberFeatures;
import daq.pubber.impl.PubberManager;
import java.util.HashMap;
import java.util.Map;
import udmi.lib.client.manager.DeviceManager;
import udmi.lib.client.manager.DiscoveryManager;
import udmi.lib.client.manager.DiscoveryScheduler;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.ManagerHost;
import udmi.schema.DiscoveryEvents;
import udmi.schema.FamilyDiscoveryModel;
import udmi.schema.FeatureDiscovery;
import udmi.schema.PointPointsetModel;
import udmi.schema.PubberConfiguration;

/**
 * Manager wrapper for discovery functionality in pubber.
 */
public class PubberDiscoveryManager extends PubberManager implements DiscoveryManager {

  private final PubberDeviceManager deviceManager;
  private final DiscoveryScheduler discoveryScheduler = new DiscoveryScheduler(this);

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
  public DiscoveryScheduler getDiscoveryScheduler() {
    return discoveryScheduler;
  }

  @Override
  public FamilyProvider discoveryProvider(String family) {
    FamilyProvider provider = DiscoveryManager.super.discoveryProvider(family);
    if (provider == null) {
      return null;
    }
    if (siteModel != null) {
      Map<String, FamilyDiscoveryModel> configured =
          catchToNull(() -> siteModel.getMetadata(getDeviceId()).discovery.families);
      if (configured == null || !configured.containsKey(family)) {
        return null;
      }
    }
    return provider;
  }

  @Override
  public void shutdown() {
    shutdownDiscoveryScans();
    super.shutdown();
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
    if (discoveryEvent.system != null && deviceId != null) {
      if (discoveryEvent.system.ancillary == null) {
        discoveryEvent.system.ancillary = new HashMap<>();
      }
      discoveryEvent.system.ancillary.put("device-name", deviceId);
    }
  }
}
