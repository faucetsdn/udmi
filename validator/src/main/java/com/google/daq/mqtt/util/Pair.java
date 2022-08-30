package com.google.daq.mqtt.util;

/**
 * Simple wrapper class to handle a final pair of typed values.
 *
 * @param <T1> type of first value
 * @param <T2> type of second value
 */
public class Pair<T1, T2> {

  public Pair(T1 valueOne, T2 valueTwo) {
    this.valueOne = valueOne;
    this.valueTwo = valueTwo;
  }

  public final T1 valueOne;
  public final T2 valueTwo;
}
