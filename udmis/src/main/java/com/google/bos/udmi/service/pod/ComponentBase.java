package com.google.bos.udmi.service.pod;

public class ComponentBase {

  public void info(String message) {
    System.out.println(getClass().getSimpleName() + ": " + message);
  }
}
