package daq.pubber;

import java.util.function.Consumer;
import udmi.schema.DiscoveryEvent;

/**
 * Interface for a protocol family provider.
 */
public interface FamilyProvider {

  default void startScan(Consumer<DiscoveryEvent> publish) {
    throw new RuntimeException("Not yet implemented");
  }

  default void stopScan() {
    throw new RuntimeException("Not yet implemented");
  }
}
