package com.google.daq.mqtt.sequencer;

import udmi.schema.Level;

/**
 * Way for a test to indicate a default log level that will stabilize the test output.
 */
public @interface DefaultLogLevel {

  /**
   * The default logLevel to use for this test (to provide consistency when necessary).
   */
  Level value();
}
