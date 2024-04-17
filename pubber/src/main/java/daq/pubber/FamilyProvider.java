package daq.pubber;

import java.util.function.BiConsumer;
import udmi.schema.DiscoveryEvents;

/**
 * Interface for a protocol family provider.
 */
public interface FamilyProvider {

  default void startScan(boolean enumerate, BiConsumer<String, DiscoveryEvents> publish) {
    throw new RuntimeException("Not yet implemented");
  }

  default void stopScan() {
    throw new RuntimeException("Not yet implemented");
  }
}
