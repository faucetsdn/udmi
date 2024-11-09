package com.google.daq.mqtt.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Simple tests for sampler class.
 */
public class ImpulseRunningAverageTest {

  private static final double ASSERT_DELTA = 0.000001;
  private ImpulseRunningAverage impulseRunningAverage = new ImpulseRunningAverage("Message test");

  @Test
  public void sampleTest() {
    impulseRunningAverage.update(1, 1);
    assertAverage("initial result", 1.0);
    impulseRunningAverage.update(1, 1);
    assertAverage("one tick result", 1.0);
    impulseRunningAverage.update(1, 0);
    assertAverage("one null result", 0.8);
    impulseRunningAverage.update(1, 0);
    assertAverage("two null result", 0.64);
    impulseRunningAverage.update(0, 0);
    assertAverage("null sample", 0.64);
    impulseRunningAverage.update(2, 1);
    assertAverage("two delay", 0.6096);
    impulseRunningAverage.update(100000000000.0, 0);
    assertAverage("reset wait", 0);
    impulseRunningAverage.update(1);
    assertAverage("tick delay 1", 0.2);
    impulseRunningAverage.update(1);
    assertAverage("tick delay 2", 0.36);
    impulseRunningAverage.update(2);
    assertAverage("tick delay 3", 0.4304);
  }

  private void assertAverage(String message, double expected) {
    assertEquals(message, expected, impulseRunningAverage.get(), ASSERT_DELTA);
  }
}