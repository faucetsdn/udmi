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
import udmi.schema.EndpointConfiguration;

/**
 * Base class for UDMIS components.
 */
public abstract class UdmisComponent extends ContainerBase {

  private final ImmutableList<HandlerSpecification> baseHandlers = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::exceptionHandler)
  );

  protected MessageDispatcher dispatcher;

  /**
   * Create a new instance of the given target class with the provided configuration.
   */
  public static <T extends UdmisComponent> T create(Class<T> clazz, EndpointConfiguration config) {
    try {
      T object = clazz.getDeclaredConstructor().newInstance();
      object.dispatcher = MessageDispatcher.from(config);
      object.activate();
      return object;
    } catch (Exception e) {
      throw new RuntimeException("While instantiating class " + clazz.getName(), e);
    }
  }

  /**
   * Create a new instance of the given target class with the provided configurations.
   */
  public static <T extends UdmisComponent> T create(Class<T> clazz,
      EndpointConfiguration from, EndpointConfiguration to) {
    try {
      T object = clazz.getDeclaredConstructor().newInstance();
      object.dispatcher = MessageDispatcher.from(from, to);
      object.activate();
      return object;
    } catch (Exception e) {
      throw new RuntimeException("While instantiating class " + clazz.getName(), e);
    }
  }

  protected void activate() {
    registerHandlers(baseHandlers);
    registerHandlers();
    dispatcher.activate();
  }

  /**
   * The default message handler. Defaults to ignore unexpected message types, but can be overridden
   * to provide component-specific behavior.
   */
  protected void defaultHandler(Object defaultedMessage) {
  }

  /**
   * The default exception handler. Defaults to simply print out exceptions, but can be overridden
   * to provide component-specific behavior.
   */
  protected void exceptionHandler(Exception e) {
    info("Received processing exception: " + Common.getExceptionMessage(e));
    e.printStackTrace();
  }

  /**
   * Register default component handlers. Can be overridden to change underlying behavior.
   */
  protected void registerHandlers(Collection<HandlerSpecification> messageHandlers) {
    dispatcher.registerHandlers(messageHandlers);
  }

  /**
   * Register component specific handlers. Should be overridden by subclass to change behaviors.
   */
  protected void registerHandlers() {
  }

  public int getMessageCount(Class<?> clazz) {
    return dispatcher.getHandlerCount(clazz);
  }

  <T> void registerHandler(Class<T> clazz, Consumer<T> handler) {
    dispatcher.registerHandler(clazz, handler);
  }

  void publish(Object message) {
    dispatcher.publish(message);
  }

  @TestOnly
  MessageDispatcher getDispatcher() {
    return dispatcher;
  }
}
