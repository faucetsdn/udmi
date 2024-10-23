package udmi.lib.intf;

/**
 * Collection of methods for how a manager can/should interface with it's host class.
 */
public interface ManagerHost extends ManagerLog {
  void update(Object update);

  default void publish(Object message) {
    publish(null, message);
  }

  void publish(String targetId, Object message);

  FamilyProvider getLocalnetProvider(String family);
}
