package com.google.daq.mqtt.util;

import static java.lang.Double.NaN;
import static java.lang.Double.isNaN;
import static java.lang.String.format;
import static java.time.Duration.between;

import java.io.PrintStream;
import java.time.Instant;

/**
 * Basic class to gather and manage time statistics.
 */
public class TimeStatistics {

  private static final double DEFAULT_ALPHA = 0.8;
  private static final double DEFAULT_VALUE = 1.0;
  private final double alpha;
  private final String name;
  private Instant previous;
  private double running = Double.NaN;

  public TimeStatistics(String name) {
    this.alpha = DEFAULT_ALPHA;
    this.name = name;
  }

  /**
   * Trigger a time-based sample (so delta from previous time sample).
   */
  public void timeSample() {
    timeSample(DEFAULT_VALUE);
  }

  public String getName() {
    return name;
  }

  public String getMessage() {
    return format("%s rate is %.2f/s", getName(), timeGet());
  }


  /**
   * Record a time-interval sample with the given impulse.
   */
  public synchronized void timeSample(double value) {
    Instant currentTimestamp = Instant.now();
    try {
      if (previous == null) {
        running = value;
        return;
      }
      sample(value, between(previous, currentTimestamp).toMillis() / 1000.0);
    } finally {
      previous = currentTimestamp;
    }
  }

  public void update() {
    timeSample(0);
  }

  public double get() {
    return running;
  }

  public synchronized void sample(double value, double deltaSec) {
    double decay = Math.pow(alpha, deltaSec);
    running = isNaN(running) ? value : (running * decay + value * (1.0 - alpha));
  }

  public double timeGet() {
    // If not yet initialized, then don't update which would default initialize to 0.
    if (previous == null) {
      return NaN;
    }
    update();
    return get();
  }
}
