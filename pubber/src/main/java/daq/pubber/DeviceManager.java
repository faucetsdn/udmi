package daq.pubber;

import udmi.schema.Config;
import udmi.schema.DevicePersistent;
import udmi.schema.Entry;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.Operation.SystemMode;
import udmi.schema.PubberOptions;

/**
 * Uber-manager for a complete device.
 */
public class DeviceManager extends ManagerBase {

  private PointsetManager pointsetManager;
  private SystemManager systemManager;

  /**
   * Create a new instance.
   */
  public DeviceManager(ManagerHost host, PubberOptions options, String serialNo) {
    super(host, options);
    systemManager = new SystemManager(host, options, serialNo);
    pointsetManager = new PointsetManager(host, options);
  }

  @Override
  protected void periodicUpdate() {

  }

  public void setPersistentData(DevicePersistent persistentData) {
    systemManager.setPersistentData(persistentData);
  }

  public void setMetadata(Metadata metadata) {
    pointsetManager.setPointsetModel(metadata.pointset);
    systemManager.setMetadata(metadata);
  }

  @Override
  public void cancelPeriodicSend() {
    super.cancelPeriodicSend();
    pointsetManager.cancelPeriodicSend();
    systemManager.cancelPeriodicSend();
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

  public void shutdown() {
    systemManager.shutdown();
    pointsetManager.shutdown();
  }
}
