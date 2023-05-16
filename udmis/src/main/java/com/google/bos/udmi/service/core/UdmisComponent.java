package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.messaging.MessageDispatcher.messageHandlerFor;

import com.google.bos.udmi.service.access.IotAccessProvider;
import com.google.bos.udmi.service.messaging.MessageContinuation;
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

  public static final Integer FUNCTIONS_VERSION_MIN = 8;
  public static final Integer FUNCTIONS_VERSION_MAX = 8;
  public static final String UDMI_VERSION = "1.4.1";

  private final ImmutableList<HandlerSpecification> baseHandlers = ImmutableList.of(
      messageHandlerFor(Object.class, this::defaultHandler),
      messageHandlerFor(Exception.class, this::exceptionHandler)
  );

  protected MessageDispatcher dispatcher;
  protected IotAccessProvider provider;

  /**
   * Create a new instance of the given target class with the provided configuration.
   */
  public static <T extends UdmisComponent> T create(Class<T> clazz, EndpointConfiguration config) {
    try {
      T object = clazz.getDeclaredConstructor().newInstance();
      object.dispatcher = MessageDispatcher.from(config);
      return object;
    } catch (Exception e) {
      throw new RuntimeException("While instantiating class " + clazz.getName(), e);
    }
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

  /**
   * Activate this component.
   */
  public void activate() {
    if (dispatcher != null) {
      registerHandlers(baseHandlers);
      registerHandlers();
      dispatcher.activate();
    }
  }

  public int getMessageCount(Class<?> clazz) {
    return dispatcher.getHandlerCount(clazz);
  }

  public void setIotAccessProvider(IotAccessProvider provider) {
    this.provider = provider;
  }

  /**
   * Shutdown the component.
   */
  public void shutdown() {
    if (dispatcher != null) {
      dispatcher.shutdown();
    }
  }

  <T> void registerHandler(Class<T> clazz, Consumer<T> handler) {
    dispatcher.registerHandler(clazz, handler);
  }

  void publish(Object message) {
    dispatcher.publish(message);
  }

  MessageContinuation getContinuation(Object message) {
    return dispatcher.getContinuation(message);
  }

  @TestOnly
  MessageDispatcher getDispatcher() {
    return dispatcher;
  }
}
