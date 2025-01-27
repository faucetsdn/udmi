package udmi.lib.client.manager;

import java.util.List;
import java.util.Map;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.SubBlockManager;
import udmi.schema.Config;
import udmi.schema.DevicePersistent;
import udmi.schema.Entry;
import udmi.schema.FamilyDiscovery;
import udmi.schema.Level;
import udmi.schema.Metadata;
import udmi.schema.Operation.SystemMode;

/**
 * Device client.
 */
public interface DeviceManager extends SubBlockManager {

  List<SubBlockManager> getSubmanagers();

  SystemManager getSystemManager();

  LocalnetManager getLocalnetManager();

  PointsetManager getPointsetManager();

  DiscoveryManager getDiscoveryManager();

  GatewayManager getGatewayManager();

  /**
   * Keep the resulting list in reverse order for proper shutdown semantics.
   */
  default <T extends SubBlockManager> T addManager(T manager) {
    getSubmanagers().add(0, manager);
    return manager;
  }

  default Map<String, FamilyDiscovery> enumerateFamilies() {
    return getLocalnetManager().enumerateFamilies();
  }

  default void setPersistentData(DevicePersistent persistentData) {
    getSystemManager().setPersistentData(persistentData);
  }

  /**
   * Set the metadata for this device.
   */
  default void setMetadata(Metadata metadata) {
    getPointsetManager().setPointsetModel(metadata.pointset);
    getSystemManager().setMetadata(metadata);
    getGatewayManager().setMetadata(metadata);
  }

  default void systemLifecycle(SystemMode mode) {
    getSystemManager().systemLifecycle(mode);
  }

  default void maybeRestartSystem() {
    getSystemManager().maybeRestartSystem();
  }

  default void localLog(Entry report) {
    getSystemManager().localLog(report);
  }

  default void localLog(String message, Level trace, String timestamp, String detail) {
    getSystemManager().localLog(message, trace, timestamp, detail);
  }

  default String getTestingTag() {
    return getSystemManager().getTestingTag();
  }

  /**
   * Update the config of this device.
   * Maintain the same order as manager initialization to ensure consistency.
   * Update gateway config last, as it may restart gateway for certain implementations.
   */
  default void updateConfig(Config config) {
    getSystemManager().updateConfig(config.system, config.timestamp);
    getLocalnetManager().updateConfig(config.localnet);
    getPointsetManager().updateConfig(config.pointset);
    getDiscoveryManager().updateConfig(config.discovery);
    getGatewayManager().updateConfig(config.gateway);
  }

  /**
   * Publish log message for target device.
   */
  default void publishLogMessage(Entry logEntry, String targetId) {
    if (getDeviceId().equals(targetId)) {
      getSystemManager().publishLogMessage(logEntry);
    } else {
      getGatewayManager().publishLogMessage(logEntry, targetId);
    }
  }

  /**
   * Set status for target device.
   */
  default void setStatus(Entry report, String targetId) {
    if (getDeviceId().equals(targetId)) {
      getSystemManager().setStatus(report);
    } else {
      getGatewayManager().setStatus(report, targetId);
    }
  }

  default void cloudLog(String message, Level level, String detail) {
    getSystemManager().cloudLog(message, level, detail);
  }

  default FamilyProvider getLocalnetProvider(String family) {
    return getLocalnetManager().getLocalnetProvider(family);
  }

  default void activate() {
    getGatewayManager().activate();
  }

  /**
   * Stop periodic senders.
   */
  default void stop() {
    getSubmanagers().forEach(SubBlockManager::stop);
  }

  /**
   * Shutdown everything, including sub-managers.
   */
  default void shutdown() {
    getSubmanagers().forEach(SubBlockManager::shutdown);
  }
}
