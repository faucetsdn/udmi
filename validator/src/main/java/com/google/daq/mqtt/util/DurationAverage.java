package com.google.daq.mqtt.util;

import static com.google.udmi.util.Common.SEC_TO_MS;
import static com.google.udmi.util.JsonUtil.getNowInstant;
import static java.time.Duration.between;

import java.time.Duration;
import java.time.Instant;

public class DurationAverage extends ImpulseRunningAverage {

  public DurationAverage(String name) {
    super(name);
  }

  @Override
  public String getMessage() {
    return messageBase() + "s";
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

  @Override
  protected double provider() {
    return 0.0;
  }

  public TimedSegment getNewSegment() {
    return new TimedSegment() {
      final Instant startTime = getNowInstant();

      @Override
      public void close() {
        provide(startTime);
      }
    };
  }

  public interface TimedSegment extends AutoCloseable {
    @Override
    void close();
  }
}
