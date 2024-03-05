package com.google.bos.udmi.service.pod;

import udmi.schema.Envelope;

/**
 * Simple interface for components that can process distributed messages.
 */
public interface SimpleHandler {

  /**
   * Process a given input message.
   */
  void processMessage(Envelope envelope, Object message);
}
