package com.google.bos.udmi.service.messaging;

import udmi.schema.Envelope;
import udmi.schema.UdmiReply;

/**
 * Interface that allows for runtime access to extended properties of a received message.
 */
public interface MessageContinuation {

  Envelope getEnvelope();

  void publish(Object message);
}
