package daq.pubber;

import udmi.schema.PointsetState;
import udmi.schema.PubberOptions;

/**
 * Collection of methods for how a manager can/should interface with it's host class.
 */
public interface ManagerHost {
  void update(PointsetState pointsetState);

  PubberOptions getOptions();

  void debug(String message);

  void info(String message);

  void error(String message, Throwable e);

  void publish(Object message);
}
