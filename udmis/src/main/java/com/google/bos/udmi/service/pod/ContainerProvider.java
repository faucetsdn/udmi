package com.google.bos.udmi.service.pod;

public interface ContainerProvider {

  void activate();

  void shutdown();

  void debug(String format, Object... args);
}
