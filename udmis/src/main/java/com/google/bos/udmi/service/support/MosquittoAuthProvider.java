package com.google.bos.udmi.service.support;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

public class MosquittoAuthProvider implements AuthRef {

  private static final String MOSQUCTL_FMT = "bin/mosquctl_client %s %s";

  public MosquittoAuthProvider() {
  }

  @Override
  public void revoke(String clientId) {
    mosquctl(clientId, "--");
  }

  private void mosquctl(String clientId, String clientPass) {
    String cmd = format(MOSQUCTL_FMT, clientId, clientPass);
    try {
      Process exec = Runtime.getRuntime().exec(cmd);
      int exitValue = exec.exitValue();
      checkState(exitValue == 0, "exit return code " + exitValue);
    } catch (Exception e) {
      throw new RuntimeException("While executing " + cmd, e);
    }
  }
}
