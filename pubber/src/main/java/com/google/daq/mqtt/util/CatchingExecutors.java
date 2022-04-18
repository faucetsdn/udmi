package com.google.daq.mqtt.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class CatchingExecutors {

  public interface CatchingScheduledExecutorService extends ScheduledExecutorService {

  }

  public static class CatchingExecutor implements CatchingScheduledExecutorService {

  }

  public static CatchingScheduledExecutorService newSingleThreadScheduledExecutor() {
    return new CatchingExecutor();
  }
}
