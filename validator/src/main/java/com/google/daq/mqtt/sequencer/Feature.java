package com.google.daq.mqtt.sequencer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Feature designation for line-item tests.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Feature {
  String IMPLICIT_CATEGORY = "";
  Stage DEFAULT_STAGE = Stage.ALPHA;
  int DEFAULT_SCORE = 5;

  String category() default IMPLICIT_CATEGORY;

  // Implicit category value for shorthand.
  String value() default IMPLICIT_CATEGORY;

  /**
   * Default value is REQUIRED which should be the end-state for all tests.
   *
   * @return annotated release stage of this test
   */
  Stage stage() default Stage.ALPHA;

  int score() default DEFAULT_SCORE;

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
