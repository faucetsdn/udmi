package com.google.bos.udmi.service.messaging;

import com.google.bos.udmi.service.messaging.MessageBase.Bundle;
import udmi.schema.Envelope;

public class MessageContinuation {

  public final Envelope envelope;
  private final Object message;
  private final MessageBase messageBase;

  public MessageContinuation(MessageBase messageBase, Envelope envelope, Object message) {
    this.messageBase = messageBase;
    this.envelope = envelope;
    this.message = message;
  }

  public void reprocess() {
    Bundle bundle = new Bundle();
    bundle.message = message;
    bundle.envelope = envelope;
    messageBase.processMessage(bundle);
  }
}
