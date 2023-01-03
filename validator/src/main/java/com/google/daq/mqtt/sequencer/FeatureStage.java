package com.google.daq.mqtt.sequencer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Release stage for a particular feature test.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface FeatureStage {

  /**
   * Default value is REQUIRED which should be the end-state for all tests.
   *
   * @return annotated release stage of this test
   */
  Stage value() default Stage.REQUIRED;

  /**
   * Enum of allowed stages.
   */
  enum Stage {
    ALPHA,
    BETA,
    PREVIEW,
    REQUIRED
  }
}
