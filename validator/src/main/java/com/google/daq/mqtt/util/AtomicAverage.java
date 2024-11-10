package com.google.daq.mqtt.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Running average implementation of an AtomicInteger.
 */
public class AtomicAverage extends RunningAverageBase {

  AtomicInteger value = new AtomicInteger();

  public AtomicAverage(String name) {
    super(name);
  }

  @Override
  public double provider() {
    return value.get();
  }

  /**
   * Increment and get (and average).
   */
  public Integer incrementAndGet() {
    int raw = value.incrementAndGet();
    update();
    return raw;
  }

  /**
   * Decrement and get (and average).
   */
  public Integer decrementAndGet() {
    int raw = value.decrementAndGet();
    update();
    return raw;
  }
}
