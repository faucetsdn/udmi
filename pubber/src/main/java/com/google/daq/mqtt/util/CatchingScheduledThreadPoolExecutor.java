package com.google.daq.mqtt.util;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
    super.afterExecute(r, t);
    if (t == null && r instanceof Future<?>) {
      try {
        Future<?> future = (Future<?>) r;
        if (future.isDone()) {
          future.get();
        }
      } catch (CancellationException ce) {
        t = ce;
      } catch (ExecutionException ee) {
        t = ee.getCause();
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
    if (t != null) {
      System.err.println(t);
    }
  }
}
