package com.google.bos.udmi.service.core;

import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.MessageDispatcher.HandlerSpecification;
import com.google.bos.udmi.service.pod.ContainerBase;
import java.util.List;
import org.jetbrains.annotations.TestOnly;
import udmi.schema.MessageConfiguration;

public abstract class UdmisComponent extends ContainerBase {

  protected MessageDispatcher dispatcher;

  public static <T extends UdmisComponent> T create(Class<T> clazz, MessageConfiguration config) {
    try {
      T object = clazz.getDeclaredConstructor().newInstance();
      object.dispatcher = MessageDispatcher.from(config);
      object.activate();
      return object;
    } catch (Exception e) {
      throw new RuntimeException("While instantiating class " + clazz.getName());
    }
  }

  void publish(Object message) {
    dispatcher.publish(message);
  }

  protected abstract List<HandlerSpecification> getMessageHandlers();

  protected void activate() {
    dispatcher.registerHandlers(getMessageHandlers());
    dispatcher.activate();
  }

  @TestOnly
  MessageDispatcher getDispatcher() {
    return dispatcher;
  }
}
