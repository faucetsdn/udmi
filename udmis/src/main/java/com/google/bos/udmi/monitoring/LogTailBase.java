package com.google.bos.udmi.monitoring;

import com.google.bos.udmi.service.pod.ContainerBase;

public class LogTailBase extends ContainerBase {

  // TODO: Move to ContainerBase.
  public void error(String message) {
    System.err.println(getSimpleName() + " E: " + message);
  }

}
