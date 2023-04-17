package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.MessageDispatcher.messageHandlerFor;

import com.google.bos.udmi.service.messaging.MessageDispatcher;
import com.google.bos.udmi.service.messaging.MessageDispatcher.HandlerSpecification;
import com.google.bos.udmi.service.pod.ContainerBase;
import com.google.common.collect.ImmutableList;
import com.google.udmi.util.Common;
import java.util.Collection;
import java.util.function.Consumer;
import org.jetbrains.annotations.TestOnly;
import udmi.schema.MessageConfiguration;

public abstract class UdmisComponent extends ContainerBase {

  private final ImmutableList<HandlerSpecification> BASE_HANDLERS = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::exceptionHandler)
  );

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

  protected void activate() {
    registerHandlers(BASE_HANDLERS);
    registerHandlers();
    dispatcher.activate();
  }

  protected void registerHandlers() {
  }

  protected void defaultHandler(Object defaultedMessage) {
  }

  protected void exceptionHandler(Exception e) {
    info("Received processing exception: " + Common.getExceptionMessage(e));
    e.printStackTrace();
  }

  protected void registerHandlers(Collection<HandlerSpecification> messageHandlers) {
    dispatcher.registerHandlers(messageHandlers);
  }

  <T> void registerHandler(Class<T> clazz, Consumer<T> handler) {
    dispatcher.registerHandler(clazz, handler);
  }

  public int getMessageCount(Class<?> clazz) {
    return dispatcher.getHandlerCount(clazz);
  }

  void publish(Object message) {
    dispatcher.publish(message);
  }

  @TestOnly
  MessageDispatcher getDispatcher() {
    return dispatcher;
  }
}
