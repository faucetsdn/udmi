package com.google.daq.mqtt.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Simple tests for sampler class.
 */
public class TimeStatisticsTest {

  private static final double ASSERT_DELTA = 0.000001;

  @Test
  public void sampleTest() {
    TimeStatistics timeStatistics = new TimeStatistics("Message test");
    timeStatistics.sample(1, 1);
    assertEquals("initial result", 1.0, timeStatistics.get(), ASSERT_DELTA);
    timeStatistics.sample(1, 1);
    assertEquals("one tick result", 1.0, timeStatistics.get(), ASSERT_DELTA);
    timeStatistics.sample(0, 1);
    assertEquals("one null result", 0.8, timeStatistics.get(), ASSERT_DELTA);
    timeStatistics.sample(0, 1);
    assertEquals("two null result", 0.64, timeStatistics.get(), ASSERT_DELTA);
    timeStatistics.sample(0, 0);
    assertEquals("null sample", 0.64, timeStatistics.get(), ASSERT_DELTA);
  }
}