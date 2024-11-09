package com.google.daq.mqtt.util;

import static org.junit.Assert.assertEquals;

import com.google.common.util.concurrent.AtomicDouble;
import org.junit.Test;

public class RunningAverageTest {

  private static final double ASSERT_DELTA = 0.000001;

  @Test
  public void sampleTest() {
    AtomicDouble targetValue = new AtomicDouble(1.0);
    RunningAverage runningAverage = new RunningAverage("Average test", targetValue::get);
    runningAverage.update(1);
    assertEquals("initial result", 1.0, runningAverage.get(), ASSERT_DELTA);
    runningAverage.update(1);
    assertEquals("one tick result", 1.0, runningAverage.get(), ASSERT_DELTA);
    runningAverage.update(0);
    assertEquals("one immediate result", 1.0, runningAverage.get(), ASSERT_DELTA);
    targetValue.set(2);
    runningAverage.update(0);
    assertEquals("two immediate result", 1.0, runningAverage.get(), ASSERT_DELTA);
    runningAverage.update(1);
    assertEquals("two tick result", 1.2, runningAverage.get(), ASSERT_DELTA);
    runningAverage.update(2);
    assertEquals("two tick twice", 1.488, runningAverage.get(), ASSERT_DELTA);
    targetValue.set(0);
    runningAverage.update(10);
    assertEquals("zero ten", 0.15977278341120008, runningAverage.get(), ASSERT_DELTA);
  }
}