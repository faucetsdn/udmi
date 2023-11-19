package daq.pubber;

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


  /**
   * Create a new instance.
   */
  public DeviceManager(ManagerHost host, PubberConfiguration configuration) {
    super(host, configuration);
    systemManager = new SystemManager(host, configuration);
    pointsetManager = new PointsetManager(host, configuration);
    localnetManager = new LocalnetManager(host, configuration);
  }

  public void setPersistentData(DevicePersistent persistentData) {
    systemManager.setPersistentData(persistentData);
  }

  public void setMetadata(Metadata metadata) {
    pointsetManager.setPointsetModel(metadata.pointset);
    systemManager.setMetadata(metadata);
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
    systemManager.localLog(message, trace, timestamp, detail);
  }

  public String getTestingTag() {
    return systemManager.getTestingTag();
  }

  public void updateConfig(Config config) {
    pointsetManager.updateConfig(config.pointset);
    systemManager.updateConfig(config.system, config.timestamp);
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
  }

  public Map<String, FamilyDiscoveryEvent> enumerateFamilies() {
    return localnetManager.enumerateFamilies();
  }
}
