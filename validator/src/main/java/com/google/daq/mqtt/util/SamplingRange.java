package com.google.daq.mqtt.util;

import java.util.List;
import java.util.function.Predicate;

/**
 * Methods for setting a sampling range and testing if intervals are within the range.
 */
public class SamplingRange {
  protected Double tolerance = 0.0;
  public Integer sampleRate;
  public Integer sampleLimit;

  /**
   * Instance of SamplingRange with no tolerance.
   *
   * @param sampleLimitSec sample_limit_sec (minimum)
   * @param sampleRateSec sample_rate_Sec (maximum)
   */
  public SamplingRange(Integer sampleLimitSec, Integer sampleRateSec) {
    sampleRate = sampleRateSec;
    sampleLimit = sampleLimitSec;
  }

  /**
   * Instance of SamplingRange with double precision tolerance range.
   *
   * @param sampleLimitSec sample_limit_sec (minimum)
   * @param sampleRateSec sample_rate_Sec (maximum)
   * @param acceptableTolerance double tolerance to accept in range
   */
  public SamplingRange(int sampleLimitSec, int sampleRateSec, double acceptableTolerance) {
    sampleRate = sampleRateSec;
    sampleLimit = sampleLimitSec;
    tolerance = acceptableTolerance;
  }

  /**
   * Instance of SamplingRange with integer tolerance.
   *
   * @param sampleLimitSec sample_limit_sec (minimum)
   * @param sampleRateSec sample_rate_Sec (maximum)
   * @param acceptableTolerance integer tolerance to accept in range
   */
  public SamplingRange(int sampleLimitSec, int sampleRateSec, int acceptableTolerance) {
    sampleRate = sampleRateSec;
    sampleLimit = sampleLimitSec;
    tolerance = (double) acceptableTolerance;
  }

  /**
   * Checks if a given list of time periods falls within the sampling range.
   *
   * @param periods list of periods between events to test
   * @return boolean if periods are within range
   */
  public boolean doesIntersect(List<Long> periods) {
    Predicate<Long> inRange = x -> (
        x > sampleLimit - tolerance
        && x < sampleRate + tolerance
    );
    if (periods.size() == 0) {
      throw new RuntimeException("list of periods empty");
    }
    return (periods.stream().filter(inRange).count() > 0);
  }

  /**
   * String representation of the range
   * Literally "between MINIMUM and MAXIMUM seconds".
   *
   * @return String
   */
  public String toString() {
    return String.format("between %d and %d seconds", sampleLimit, sampleRate);
  }
}

