package com.google.daq.mqtt.sequencer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import udmi.schema.Bucket;

/**
 * Feature designation for line-item tests.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface
Feature {
  Stage DEFAULT_STAGE = Stage.REQUIRED;
  int DEFAULT_SCORE = 5;

  /**
   * Defines the bucket for this feature, as defined by a named attribute.
   *
   * @return feature bucket using named attribute
   */
  Bucket bucket() default Bucket.UNKNOWN_DEFAULT;

  /**
   * Defines the bucket for this feature, as defined by an implicit attribute.
   *
   * @return feature bucket using implicit argument
   */
  Bucket value() default Bucket.UNKNOWN_DEFAULT;

  /**
   * Default value is REQUIRED which should be the end-state for all tests.
   *
   * @return annotated release stage of this test
   */
  Stage stage() default Stage.REQUIRED;

  /**
   * Defines the feature score for this test, in AU.
   *
   * @return feature score value
   */
  int score() default DEFAULT_SCORE;

  /**
   * Enum of allowed stages.
   */
  enum Stage {
    ALPHA,
    REQUIRED
  }
}
