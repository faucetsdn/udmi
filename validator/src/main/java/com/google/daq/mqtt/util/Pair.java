package com.google.daq.mqtt.util;

public class Pair<TA, TB> {

  public Pair(TA valueOne, TB valueTwo) {
    this.valueOne = valueOne;
    this.valueTwo = valueTwo;
  }

  public final TA valueOne;
  public final TB valueTwo;
}
