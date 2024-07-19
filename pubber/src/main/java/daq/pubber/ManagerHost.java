package daq.pubber;

/**
 * Collection of methods for how a manager can/should interface with it's host class.
 */
public interface ManagerHost {
  void update(Object update);

  void debug(String message);

  void info(String message);

  void warn(String message);

  void error(String message, Throwable e);

  void publish(Object message);

  FamilyProvider getLocalnetProvider(String family);
}
