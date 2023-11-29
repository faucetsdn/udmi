package com.google.daq.mqtt.sequencer.sequences;

import com.google.daq.mqtt.sequencer.SequenceBase.Capabilities;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import udmi.schema.FeatureEnumeration.FeatureStage;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Repeatable(AllCapabilities.class)
public @interface Capability {

  /**
   * Define the capability that this annotation describes.
   */
  Capabilities value();

  /**
   * Default value is ALPHA for initial test development.
   */
  FeatureStage stage() default FeatureStage.STABLE;

}
