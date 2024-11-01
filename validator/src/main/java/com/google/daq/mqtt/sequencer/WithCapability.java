package com.google.daq.mqtt.sequencer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import udmi.schema.FeatureDiscovery.FeatureStage;

/**
 * Singular annotation for a sequence test capability.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Repeatable(AllCapabilities.class)
public @interface WithCapability {

  /**
   * Default value is ALPHA for initial test development.
   */
  FeatureStage stage() default FeatureStage.STABLE;

  /**
   * Specify the capability as a class object.
   */
  Class<? extends Capability> value() default Capability.class;
}
