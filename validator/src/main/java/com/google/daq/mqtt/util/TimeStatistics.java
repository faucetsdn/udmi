package com.google.daq.mqtt.util;

import static java.lang.Double.isNaN;
import static java.time.Duration.between;

import java.time.Instant;

/**
 * Basic class to gather and manage time statistics.
 */
public class TimeStatistics {

  private static final double DEFAULT_ALPHA = 0.5;
  private final double alpha;
  private Instant previous;
  private double running = Double.NaN;

  public TimeStatistics() {
    this.alpha = DEFAULT_ALPHA;
  }

  /**
   * Trigger a time-based sample (so delta from previous time sample).
   */
  public void timeSample() {
    Instant currentTimestamp = Instant.now();
    try {
      if (previous == null) {
        return;
      }
      sample(between(previous, currentTimestamp).toMillis());
    } finally {
      previous = currentTimestamp;
    }
  }

  public double get() {
    return running;
  }

  public void sample(double value) {
    running = isNaN(running) ? value : (alpha * value + (1 - alpha) * running);
  }
}
