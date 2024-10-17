package daq.pubber;

/**
 * Collection of methods for how a manager can/should interface with it's host class.
 */
public interface ManagerHost extends ManagerLog {
  void update(Object update);

  void publish(Object message);

  FamilyProvider getLocalnetProvider(String family);
}
