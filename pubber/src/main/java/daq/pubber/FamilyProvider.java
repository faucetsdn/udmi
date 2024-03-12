package daq.pubber;

import udmi.schema.DiscoveryEvent;

/**
 * Interface for a protocol family provider.
 */
public interface FamilyProvider {

  default void startScan(DiscoveryEvent event) {
    throw new RuntimeException("Not yet implemented");
  }

  default void stopScan() {
    throw new RuntimeException("Not yet implemented");
  }
}
