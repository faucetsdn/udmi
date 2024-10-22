package udmi.lib.client;

import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import udmi.lib.intf.ManagerHost;
import udmi.lib.intf.ManagerLog;
import udmi.schema.PubberConfiguration;
import udmi.schema.PubberOptions;

/**
 * Interface for providing main manager functionalities.
 *
 */
public interface SubblockManager extends ManagerLog {

  void updateState(Object state);

  ScheduledFuture<?> scheduleFuture(Date futureTime, Runnable futureTask);

  void error(String message);

  void updateInterval(Integer sampleRateSec);

  void periodicUpdate();

  void stop();

  void shutdown();

  String getDeviceId();

  PubberOptions getOptions();

  PubberConfiguration getConfig();

  ManagerHost getHost();
}
