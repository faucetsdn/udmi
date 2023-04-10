package com.google.bos.udmi.service.pod;

/**
 * Baseline functions that are useful for any other component. No real functionally, rather
 * convenience and abstraction to keep the main component code more clear.
 */
public class ContainerBase {

  public void info(String message) {
    // TODO: Implement facilities for other loggers, including structured-to-cloud.
    System.out.println(getClass().getSimpleName() + ": " + message);
  }
}
