package com.google.daq.mqtt.validator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CommandLineOption {

  String NO_VALUE = "";

  String short_form() default NO_VALUE;

  String long_form() default NO_VALUE;
}
