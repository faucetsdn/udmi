package udmi.lib.intf;

import java.util.Map;
import java.util.function.BiConsumer;
import udmi.schema.DiscoveryEvents;
import udmi.schema.FamilyDiscoveryConfig;
import udmi.schema.RefDiscovery;

/**
 * Interface for a protocol family provider.
 */
public interface FamilyProvider {

  default void startScan(FamilyDiscoveryConfig discoveryConfig,
      BiConsumer<String, DiscoveryEvents> publish) {
    throw new RuntimeException("Not yet implemented");
  }

  default void stopScan() {
    throw new RuntimeException("Not yet implemented");
  }

  default Map<String, RefDiscovery> enumerateRefs(String addr) {
    throw new RuntimeException("Not yet implemented");
  }
}
