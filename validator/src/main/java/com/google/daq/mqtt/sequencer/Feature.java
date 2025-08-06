package com.google.daq.mqtt.sequencer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import udmi.schema.Bucket;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.FeatureDiscovery.FeatureStage;

/**
 * Feature designation for line-item tests.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Feature {
  FeatureStage DEFAULT_STAGE = FeatureStage.ALPHA;
  int DEFAULT_SCORE = 10;

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
   * Default value is ALPHA for initial test development.
   *
   * @return annotated release stage of this test
   */
  FeatureStage stage() default FeatureStage.ALPHA;

  /**
   * Default value is INVALID indicating there are no facets to process.
   *
   * @return indicator for the facets that need to be processed
   */
  SubFolder facets() default SubFolder.INVALID;

  /**
   * Defines the feature score for this test, in AU.
   *
   * @return feature score value
   */
  int score() default DEFAULT_SCORE;

  /**
   * Indicates if this test can run without state updates.
   */
  boolean nostate() default false;
}
