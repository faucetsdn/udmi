package udmi.util;

import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Thread executor wrapper that does a better job of exposing exceptions during execution.
 */
public class CatchingScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

  /**
   * Create a new executor.
   *
   * @param corePoolSize number of threads to utilize
   */
  public CatchingScheduledThreadPoolExecutor(int corePoolSize) {
    super(corePoolSize);
  }

  protected void afterExecute(Runnable r, Throwable t) {
    if (t != null) {
      System.err.println("Exception during scheduled execution:");
      t.printStackTrace();
    }
    super.afterExecute(r, null);
  }
}
