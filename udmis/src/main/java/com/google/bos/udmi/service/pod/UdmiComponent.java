package com.google.bos.udmi.service.pod;

import udmi.schema.Level;

/**
 * Simple interface for representing all containers.
 */
public interface UdmiComponent {

  void activate();

  void shutdown();

  void output(Level level, String message);
}
