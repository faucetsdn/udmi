package daq.pubber;

public interface LocalnetProvider {

  default void startScan() {
    throw new RuntimeException("Not yet implemented");
  }

  default void stopScan() {
    throw new RuntimeException("Not yet implemented");
  }
}
