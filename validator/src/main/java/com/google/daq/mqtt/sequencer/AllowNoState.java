package com.google.daq.mqtt.sequencer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicate that test post-processing should apply schema validation. Don't include by
 * default since it can be really slow when applied to all tests!
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface AllowNoState {

}
