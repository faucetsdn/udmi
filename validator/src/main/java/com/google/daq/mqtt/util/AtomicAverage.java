package com.google.daq.mqtt.util;

import java.util.concurrent.atomic.AtomicInteger;

public class AtomicAverage extends RunningAverageBase {

  AtomicInteger value = new AtomicInteger();

  public AtomicAverage(String name) {
    super(name);
  }

  @Override
  public double provider() {
    return value.get();
  }

  public Integer incrementAndGet() {
    int raw = value.incrementAndGet();
    update();
    return raw;
  }

  public Integer decrementAndGet() {
    int raw = value.decrementAndGet();
    update();
    return raw;
  }
}
