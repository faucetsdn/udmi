package com.google.daq.mqtt.util;

/**
 * Basic class to gather and manage time statistics.
 */
public class ImpulseRunningAverage extends RunningAverage {

  private static final double TICK_VALUE = 1.0;

  public ImpulseRunningAverage(String name) {
    super(name, () -> TICK_VALUE);
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
