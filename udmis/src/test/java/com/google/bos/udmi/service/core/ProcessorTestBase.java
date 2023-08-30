package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.StateProcessor.IOT_ACCESS_COMPONENT;
import static org.mockito.Mockito.mock;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.messaging.impl.MessagePipeTestBase;
import com.google.bos.udmi.service.messaging.impl.MessageTestBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.udmi.util.CleanDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Envelope;

/**
 * Base class for functional processor tests.
 */
public abstract class ProcessorTestBase extends MessageTestBase {

  public static final String TEST_USER = "giraffe@safari.com";
  public static final Date TEST_TIMESTAMP = CleanDateFormat.cleanDate();
  public static final long ASYNC_PROCESSING_DELAY_MS = 2000;
  protected final List<Object> captured = new ArrayList<>();
  private ProcessorBase processor;
  protected IotAccessBase provider;

  protected int getDefaultCount() {
    return getMessageCount(Object.class);
  }

  protected int getExceptionCount() {
    return getMessageCount(Exception.class);
  }

  protected int getMessageCount(Class<?> clazz) {
    return processor.getMessageCount(clazz);
  }

  protected void initializeTestInstance() {
    try {
      UdmiServicePod.resetForTest();
      createProcessorInstance();
      activateReverseProcessor();
      getReverseDispatcher();
    } catch (Exception e) {
      throw new RuntimeException("While initializing test instance", e);
    }
  }

  protected Bundle makeMessageBundle(Object message) {
    return new Bundle(makeTestEnvelope(), message);
  }

  protected Envelope makeTestEnvelope() {
    return MessagePipeTestBase.makeTestEnvelope();
  }

  private void activateReverseProcessor() {
    MessageDispatcherImpl reverseDispatcher = getReverseDispatcher();
    reverseDispatcher.registerHandler(Object.class, this::resultHandler);
    reverseDispatcher.activate();
  }

  private void createProcessorInstance() {
    EndpointConfiguration config = new EndpointConfiguration();
    config.protocol = Protocol.LOCAL;
    config.hostname = TEST_NAMESPACE;
    config.recv_id = TEST_SOURCE;
    config.send_id = TEST_DESTINATION;
    processor = ProcessorBase.create(getProcessorClass(), config);
    setTestDispatcher(processor.getDispatcher());
    provider = mock(IotAccessBase.class);
    UdmiServicePod.putComponent(IOT_ACCESS_COMPONENT, () -> provider);
    processor.activate();
    provider.activate();
  }

  @NotNull
  protected abstract Class<? extends ProcessorBase> getProcessorClass();

  protected void terminateAndWait() {
    getTestDispatcher().terminate();
    getReverseDispatcher().terminate();
    getTestDispatcher().awaitShutdown();
    getReverseDispatcher().awaitShutdown();
    provider.shutdown();
    processor.shutdown();
  }

  private void resultHandler(Object message) {
    captured.add(message);
  }
}
