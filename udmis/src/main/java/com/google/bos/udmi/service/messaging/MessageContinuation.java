package com.google.bos.udmi.service.messaging;

import udmi.schema.Envelope;

public interface MessageContinuation {

  Envelope getEnvelope();
}
