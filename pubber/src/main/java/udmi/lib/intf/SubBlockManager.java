package udmi.lib.intf;

import java.util.Date;
import java.util.concurrent.ScheduledFuture;

/**
 * Interface for providing main manager functionalities.
 */
public interface SubBlockManager extends ManagerLog {

  void updateState(Object state);

  ScheduledFuture<?> scheduleFuture(Date futureTime, Runnable futureTask);

  void error(String message);

  void updateInterval(Integer sampleRateSec);

  void periodicUpdate();

  void stop();

  void shutdown();

  String getDeviceId();

  ManagerHost getHost();

  int incrementEventCount();
}
