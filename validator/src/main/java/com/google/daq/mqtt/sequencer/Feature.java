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
public @interface
Feature {
  String IMPLICIT_CATEGORY = "";
  Stage DEFAULT_STAGE = Stage.REQUIRED;
  int DEFAULT_SCORE = 5;

  /**
   * Defines the category for this feature, as defined by a named attribute.
   *
   * @return feature category using named attribute
   */
  String category() default IMPLICIT_CATEGORY;

  /**
   * Defines the category for this feature, as defined by an implicit attribute.
   *
   * @return feature category using implicit argument
   */
  String value() default IMPLICIT_CATEGORY;

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
