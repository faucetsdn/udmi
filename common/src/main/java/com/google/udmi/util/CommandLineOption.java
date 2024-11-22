package com.google.udmi.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate a command-line argument.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CommandLineOption {

  String NO_VALUE = "";

  /**
   * Set the text help description.
   */
  String description();

  /**
   * Short form argument.
   */
  String short_form() default NO_VALUE;

  /**
   * Long form argument.
   */
  String long_form() default NO_VALUE;

  /**
   * Set the argument name.
   */
  String arg_name() default NO_VALUE;
}
