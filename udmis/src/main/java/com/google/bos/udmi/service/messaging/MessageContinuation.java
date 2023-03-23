package com.google.bos.udmi.service.messaging;

import com.google.bos.udmi.service.messaging.MessageBase.Bundle;
import udmi.schema.Envelope;

/**
 * Represents a continuation of a received message. This allows access to ancillary message data
 * (through the envelope), and the ability to reprocess ths message after modification.
 */
public class MessageContinuation {

  public final Envelope envelope;
  private final Object message;
  private final MessageBase messageBase;

  MessageContinuation(MessageBase messageBase, Envelope envelope, Object message) {
    this.messageBase = messageBase;
    this.envelope = envelope;
    this.message = message;
  }

  /**
   * Reprocess the given message. This call is synchronous, so the follow-on will be processed in
   * the same thread.
   */
  public void reprocess() {
    Bundle bundle = new Bundle();
    bundle.message = message;
    bundle.envelope = envelope;
    messageBase.processMessage(bundle);
  }
}
