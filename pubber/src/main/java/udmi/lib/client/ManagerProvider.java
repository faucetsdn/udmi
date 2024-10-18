package udmi.lib.client;

import daq.pubber.ManagerHost;
import daq.pubber.ManagerLog;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import udmi.schema.PubberConfiguration;
import udmi.schema.PubberOptions;

/**
 * Interface for providing main manager functionalities.
 *
 */
public interface ManagerProvider extends ManagerLog {

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
