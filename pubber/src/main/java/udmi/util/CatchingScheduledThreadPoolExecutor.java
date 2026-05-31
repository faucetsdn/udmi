package udmi.util;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
    Throwable exception = t;
    if (exception == null && r instanceof Future<?>) {
      try {
        Future<?> future = (Future<?>) r;
        if (future.isDone()) {
          future.get();
        }
      } catch (ExecutionException ee) {
        exception = ee.getCause();
      } catch (Exception e) {
        exception = e;
      }
    }
    final Throwable exceptionToLog = exception;
    ifNotNullThen(exceptionToLog,
        () -> LOG.error("Exception during scheduled execution", exceptionToLog));
    super.afterExecute(r, null);
  }
}
