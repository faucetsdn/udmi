package com.google.bos.udmi.service.messaging;

import com.google.bos.udmi.service.messaging.MessagePipe.MessageConsumer;
import udmi.schema.Envelope;

public interface MessageHandler {

  <T> void registerHandler(Class<T> clazz, MessageConsumer<T> consumer);

  void messageLoop();

  <T> void publishMessage(Envelope envelope, T message);


}
