package com.google.daq.mqtt.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Simple tests for sampler class.
 */
public class ImpulseRunningAverageTest {

  private static final double ASSERT_DELTA = 0.000001;

  @Test
  public void sampleTest() {
    ImpulseRunningAverage impulseRunningAverage = new ImpulseRunningAverage("Message test");
    impulseRunningAverage.update(1, 1);
    assertEquals("initial result", 1.0, impulseRunningAverage.get(), ASSERT_DELTA);
    impulseRunningAverage.update(1, 1);
    assertEquals("one tick result", 1.0, impulseRunningAverage.get(), ASSERT_DELTA);
    impulseRunningAverage.update(1, 0);
    assertEquals("one null result", 0.8, impulseRunningAverage.get(), ASSERT_DELTA);
    impulseRunningAverage.update(1, 0);
    assertEquals("two null result", 0.64, impulseRunningAverage.get(), ASSERT_DELTA);
    impulseRunningAverage.update(0, 0);
    assertEquals("null sample", 0.64, impulseRunningAverage.get(), ASSERT_DELTA);
    impulseRunningAverage.update(2, 1);
    assertEquals("two delay", 0.6096, impulseRunningAverage.get(), ASSERT_DELTA);
  }
}