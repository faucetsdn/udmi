package com.google.daq.mqtt.validator;

import com.google.udmi.util.ExceptionMap;
import java.util.Arrays;

/**
 * Variant that uses the validator core to test schema examples from files.
 */
public class SchemaTester {

  /**
   * Let's go.
   */
  public static void main(String[] args) {
    try {
      Validator validator = new Validator(Arrays.asList(args));
      validator.messageLoop();
    } catch (ExceptionMap processingException) {
      System.exit(2);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.flush();
      System.exit(-1);
    }
    System.exit(0);
  }
}
