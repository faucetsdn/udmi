package com.google.daq.mqtt.util;

import static java.lang.Double.NaN;
import static java.lang.Double.isNaN;
import static java.lang.String.format;
import static java.time.Duration.between;

import java.time.Instant;
import java.util.function.Supplier;

public class RunningAverageBase {

  private static final double DEFAULT_ALPHA = 0.8;

  protected final double alpha;
  protected final String name;
  private Instant previous;
  protected double running = Double.NaN;

  public RunningAverageBase(String name) {
    this.name = name;
    this.alpha = DEFAULT_ALPHA;
  }

  public String getName() {
    return name;
  }

  public String getMessage() {
    return format("%s is %.2f", getName(), timeGet());
  }

  /**
   * Record a time-interval sample with the given impulse.
   */
  public synchronized void update() {
    Instant currentTimestamp = Instant.now();
    try {
      if (previous == null) {
        previous = currentTimestamp;
      }
      update(between(previous, currentTimestamp).toMillis() / 1000.0);
    } finally {
      previous = currentTimestamp;
    }
  }

  public synchronized void update(double deltaSec) {
    update(deltaSec, provider());
  }

  protected double provider() {
    return 0;
  }

  public synchronized void update(double deltaSec, double value) {
    if (isNaN(running)) {
      running = value;
      return;
    }
    double delta = getDelta(value);
    double remainder = delta * Math.pow(alpha, deltaSec);
    double impulse = (1.0 - alpha) * getImpulse(value);
    double baseline = getBaseline(value);
    running = baseline + remainder + impulse;
  }

  public double get() {
    return running;
  }

  protected double getBaseline(double value) {
    return value;
  }

  protected double getDelta(double value) {
    return running - value;
  }

  protected double getImpulse(double value) {
    return 0;
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
