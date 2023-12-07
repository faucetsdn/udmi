package daq.pubber;

import com.google.udmi.util.SiteModel;
import java.util.Map;
import udmi.schema.Config;
import udmi.schema.DevicePersistent;
import udmi.schema.Entry;
import udmi.schema.FamilyDiscoveryEvent;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.Operation.SystemMode;
import udmi.schema.PubberConfiguration;

/**
 * Uber-manager for a complete device.
 */
public class DeviceManager extends ManagerBase {

  private final PointsetManager pointsetManager;
  private final SystemManager systemManager;
  private final LocalnetManager localnetManager;
  private final GatewayManager gatewayManager;
  private final DiscoveryManager discoveryManager;


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

  public void setPersistentData(DevicePersistent persistentData) {
    systemManager.setPersistentData(persistentData);
  }

  /**
   * Set the metadata for this device.
   */
  public void setMetadata(Metadata metadata) {
    pointsetManager.setPointsetModel(metadata.pointset);
    systemManager.setMetadata(metadata);
    gatewayManager.setMetadata(metadata.gateway);
  }

  public void activate() {
    gatewayManager.activate();
  }

  public void systemLifecycle(SystemMode mode) {
    systemManager.systemLifecycle(mode);
  }

  public void maybeRestartSystem() {
    systemManager.maybeRestartSystem();
  }

  public void localLog(Entry report) {
    systemManager.localLog(report);
  }

  public void localLog(String message, Level trace, String timestamp, String detail) {
    SystemManager.localLog(message, trace, timestamp, detail);
  }

  public String getTestingTag() {
    return systemManager.getTestingTag();
  }

  /**
   * Update the config of this device.
   */
  public void updateConfig(Config config) {
    pointsetManager.updateConfig(config.pointset);
    systemManager.updateConfig(config.system, config.timestamp);
    gatewayManager.updateConfig(config.gateway);
    discoveryManager.updateConfig(config.discovery);
  }

  public void publishLogMessage(Entry logEntry) {
    systemManager.publishLogMessage(logEntry);
  }

  public void cloudLog(String message, Level level, String detail) {
    systemManager.cloudLog(message, level, detail);
  }

  /**
   * Shutdown everything, including sub-managers.
   */
  public void shutdown() {
    systemManager.shutdown();
    pointsetManager.shutdown();
    localnetManager.shutdown();
    gatewayManager.shutdown();
  }

  public Map<String, FamilyDiscoveryEvent> enumerateFamilies() {
    return localnetManager.enumerateFamilies();
  }

  public void setSiteModel(SiteModel siteModel) {
    discoveryManager.setSiteModel(siteModel);
  }
}
