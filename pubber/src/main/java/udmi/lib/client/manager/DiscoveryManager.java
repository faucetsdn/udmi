package udmi.lib.client.manager;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.GeneralUtils.instantNow;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import udmi.lib.intf.FamilyProvider;
import udmi.lib.intf.SubBlockManager;
import udmi.schema.DiscoveryConfig;
import udmi.schema.DiscoveryEvents;
import udmi.schema.DiscoveryState;
import udmi.schema.Enumerations.Depth;
import udmi.schema.FeatureDiscovery;
import udmi.schema.PointPointsetModel;

/**
 * Discovery client manager contract, delegating scan scheduling and concurrency to
 * {@link DiscoveryScheduler}.
 */
public interface DiscoveryManager extends SubBlockManager {

  int SCAN_DURATION_SEC = 10;

  DiscoveryScheduler getDiscoveryScheduler();

  Map<String, FeatureDiscovery> getFeatures();

  Map<String, PointPointsetModel> enumeratePoints(String deviceId);

  DeviceManager getDeviceManager();

  ScheduledFuture<?> scheduleFuture(Date startGeneration, Runnable runnable);

  void postDiscoveryProcess(String deviceId, DiscoveryEvents discoveryEvents);

  default Instant getScanNow() {
    return instantNow();
  }

  default DiscoveryState getDiscoveryState() {
    return getDiscoveryScheduler().getDiscoveryState();
  }

  default DiscoveryConfig getDiscoveryConfig() {
    return getDiscoveryScheduler().getDiscoveryConfig();
  }

  /**
   * Updates the discovery configuration.
   *
   * @param discovery Discovery config.
   */
  default void updateConfig(DiscoveryConfig discovery) {
    getDiscoveryScheduler().updateConfig(discovery);
  }

  /**
   * Stops all active scans and cancels scheduled scan futures during manager shutdown.
   */
  default void shutdownDiscoveryScans() {
    getDiscoveryScheduler().shutdown();
  }

  /**
   * Returns the scan-capable provider for the given protocol family, or null if unsupported.
   */
  default FamilyProvider discoveryProvider(String family) {
    FamilyProvider provider = getHost().getLocalnetProvider(family);
    return provider != null && provider.supportsDiscoveryScan() ? provider : null;
  }

  static boolean shouldEnumerate(Depth config, Depth threshold) {
    return config != null && config.ordinal() >= threshold.ordinal();
  }

  /**
   * Checks whether a discovered address matches the configured target addresses or ranges.
   */
  static boolean matchesTarget(Set<String> targets, String addr) {
    if (targets == null) {
      return true;
    }
    if (addr == null) {
      return false;
    }
    if (targets.contains(addr)) {
      return true;
    }
    Long addrVal = catchToNull(() -> Long.parseLong(addr));
    if (addrVal == null) {
      return false;
    }
    return targets.stream().anyMatch(target -> {
      String[] parts = target.split("-", 2);
      if (parts.length == 2) {
        Long low = catchToNull(() -> Long.parseLong(parts[0]));
        Long high = catchToNull(() -> Long.parseLong(parts[1]));
        return low != null && high != null && addrVal >= low && addrVal <= high;
      }
      return false;
    });
  }
}
