package com.google.daq.mqtt.sequencer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import udmi.schema.Level;

/**
 * Way for a test to indicate a default log level that will stabilize the test output.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface DefaultLogLevel {

  /**
   * The default logLevel to use for this test (to provide consistency when necessary).
   */
  Level value();
}
