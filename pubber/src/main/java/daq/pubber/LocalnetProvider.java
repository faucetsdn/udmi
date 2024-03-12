package daq.pubber;

import udmi.schema.DiscoveryEvent;

public interface LocalnetProvider {

  default void startScan(DiscoveryEvent event) {
    throw new RuntimeException("Not yet implemented");
  }

  default void stopScan() {
    throw new RuntimeException("Not yet implemented");
  }
}
