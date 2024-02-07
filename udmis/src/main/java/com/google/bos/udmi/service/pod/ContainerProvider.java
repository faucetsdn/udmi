package com.google.bos.udmi.service.pod;

/**
 * Simple interface for representing all containers.
 */
public interface ContainerProvider {

  void activate();

  void shutdown();

  void debug(String format, Object... args);
}
