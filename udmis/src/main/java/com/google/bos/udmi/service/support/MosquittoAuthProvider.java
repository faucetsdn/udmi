package com.google.bos.udmi.service.support;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

import com.google.bos.udmi.service.pod.ContainerBase;
import java.util.concurrent.TimeUnit;

public class MosquittoAuthProvider implements AuthRef {

  private static final String UDMI_ROOT = System.getenv("UDMI_ROOT");
  private static final String MOSQUCTL_FMT = UDMI_ROOT + "/bin/mosquctl_client %s %s";
  private static final long EXEC_TIMEOUT_SEC = 10;
  private final ContainerBase container;

  public MosquittoAuthProvider(ContainerBase container) {
    this.container = container;
  }

  @Override
  public void revoke(String clientId) {
    mosquctl(clientId, "--");
  }

  private void mosquctl(String clientId, String clientPass) {
    String cmd = format(MOSQUCTL_FMT, clientId, clientPass);
    try {
      container.info("Executing command %s", cmd);
      Process exec = Runtime.getRuntime().exec(cmd);
      exec.waitFor(EXEC_TIMEOUT_SEC, TimeUnit.SECONDS);
      int exitValue = exec.exitValue();
      checkState(exitValue == 0, "exit return code " + exitValue);
    } catch (Exception e) {
      throw new RuntimeException("While executing " + cmd, e);
    }
  }
}
