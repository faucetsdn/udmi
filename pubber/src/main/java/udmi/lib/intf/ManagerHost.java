package udmi.lib.intf;

import java.util.Date;

/**
 * Collection of methods for how a manager can/should interface with its host class.
 */
public interface ManagerHost extends ManagerLog {

  void update(Object update);

  /**
   * Executes an action while holding this host's state lock, if any.
   */
  default void withStateLock(Runnable action) {
    action.run();
  }

  /**
   * Executes an action if this host's state lock can be acquired immediately without blocking.
   */
  default boolean tryWithStateLock(Runnable action) {
    withStateLock(action);
    return true;
  }

  default void publish(Object message) {
    publish(null, message);
  }

  void publish(String targetId, Object message);

  FamilyProvider getLocalnetProvider(String family);

  Date getStartTime();
}
