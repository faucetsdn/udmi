package com.google.daq.mqtt.util;

import static com.google.udmi.util.Common.SEC_TO_MS;
import static com.google.udmi.util.JsonUtil.getNowInstant;
import static java.time.Duration.between;

import java.time.Duration;
import java.time.Instant;

public class DurationAverage extends RunningAverageBase {

  public DurationAverage(String name) {
    super(name);
  }

  @Override
  public String getMessage() {
    return super.getMessage() + "s";
  }

  public void provide(Instant start) {
    provide(start, getNowInstant());
  }

  public void provide(Instant start, Instant end) {
    provide(between(start, end));
  }

  public void provide(Duration duration) {
    provide(duration.toMillis() / (double) SEC_TO_MS);
  }


}
