package com.google.daq.mqtt.util;

import static java.lang.String.format;

/**
 * Basic class to gather and manage time statistics.
 */
public class ImpulseRunningAverage extends RunningAverageBase {

  private static final double TICK_VALUE = 1.0;

  public ImpulseRunningAverage(String name) {
    super(name);
  }

  @Override
  protected double provider() {
    return TICK_VALUE;
  }

  @Override
  public String getMessage() {
    return format("%s is %.2f/s", getName(), timeGet());
  }

  @Override
  protected double getImpulse(double value) {
    return value;
  }

  @Override
  protected double getDelta(double value) {
    return running;
  }

  @Override
  protected double getBaseline(double value) {
    return 0;
  }
}
