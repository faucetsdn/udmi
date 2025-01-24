package udmi.util;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread executor wrapper that does a better job of exposing exceptions during execution.
 */
public class CatchingScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

  public static final Logger LOG =
      LoggerFactory.getLogger(CatchingScheduledThreadPoolExecutor.class);

  /**
   * Create a new executor.
   *
   * @param corePoolSize number of threads to utilize
   */
  public CatchingScheduledThreadPoolExecutor(int corePoolSize) {
    super(corePoolSize);
  }

  protected void afterExecute(Runnable r, Throwable t) {
    ifNotNullThen(t, () -> LOG.error("Exception during scheduled execution", t));
    super.afterExecute(r, null);
  }
}
