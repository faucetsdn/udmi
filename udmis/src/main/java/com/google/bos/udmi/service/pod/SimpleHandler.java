package com.google.bos.udmi.service.pod;

import udmi.schema.Envelope;

public interface SimpleHandler {

  /**
   * Process a given input message.
   */
  void processMessage(Envelope envelope, Object message);
}
