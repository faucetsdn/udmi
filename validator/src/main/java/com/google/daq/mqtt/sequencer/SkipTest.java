package com.google.daq.mqtt.sequencer;

/**
 * Specific exception used to indicate when a test is intentionally skipped (not failed).
 */
public class SkipTest extends RuntimeException {

  public SkipTest(String reason) {
    super(reason);
  }
}
