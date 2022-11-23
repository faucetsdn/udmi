package com.google.daq.mqtt.util;

import java.util.List;
import java.util.function.Predicate;

public class SamplingRange {
  protected Double tolerance = 0.0;
  public Integer sampleRate;
  public Integer sampleLimit;

  public SamplingRange(Integer sampleLimitSec, Integer sampleRateSec) {
    sampleRate = sampleRateSec;
    sampleLimit = sampleLimitSec;
  }

  public SamplingRange(int sampleLimitSec, int sampleRateSec, double acceptableTolerance) {
    sampleRate = sampleRateSec;
    sampleLimit = sampleLimitSec;
    tolerance = acceptableTolerance;
  }

  public SamplingRange(int sampleLimitSec, int sampleRateSec, int acceptableTolerance) {
    sampleRate = sampleRateSec;
    sampleLimit = sampleLimitSec;
    tolerance = (double) acceptableTolerance;
  }

  public boolean valuesInRange(List<Long> vector){
    Predicate<Long> inRange = x -> (
        x > sampleLimit - tolerance
        && x < sampleRate + tolerance
    );
    return (vector.stream().filter(inRange).count() > 0);

  }

}

