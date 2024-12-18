package com.google.daq.mqtt.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for the basic running average variant.
 */
public class RunningAverageBaseTest {

  private static final double ASSERT_DELTA = 0.000001;

  @Test
  public void sampleTest() {
    RunningAverageBase runningAverage = new RunningAverageBase("Average test");
    runningAverage.update(1, 1);
    assertEquals("initial result", 1.0, runningAverage.get(), ASSERT_DELTA);
    runningAverage.update(1, 1);
    assertEquals("one tick result", 1.0, runningAverage.get(), ASSERT_DELTA);
    runningAverage.update(0, 1);
    assertEquals("one immediate result", 1.0, runningAverage.get(), ASSERT_DELTA);
    runningAverage.update(0, 2);
    assertEquals("two immediate result", 1.0, runningAverage.get(), ASSERT_DELTA);
    runningAverage.update(1, 2);
    assertEquals("two tick result", 1.2, runningAverage.get(), ASSERT_DELTA);
    runningAverage.update(2, 2);
    assertEquals("two tick twice", 1.488, runningAverage.get(), ASSERT_DELTA);
    runningAverage.update(10, 0);
    assertEquals("zero ten", 0.15977278341120008, runningAverage.get(), ASSERT_DELTA);
  }
}
