package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.writeFile;
import static org.apache.commons.io.FileUtils.deleteDirectory;

import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.messaging.impl.MessageTestBase;
import com.google.udmi.util.CleanDateFormat;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.SetupUdmiConfig;

/**
 * Base class for functional processor tests.
 */
public abstract class ProcessorTestBase extends MessageTestBase {

  public static final String TEST_USER = "giraffe@safari.com";
  public static final Date TEST_TIMESTAMP = CleanDateFormat.cleanDate();
  public static final String TEST_FUNCTIONS = "functions-version";
  protected final List<Object> captured = new ArrayList<>();
  private UdmisComponent processor;

  protected int getDefaultCount() {
    return processor.getMessageCount(Object.class);
  }

  protected int getExceptionCount() {
    return processor.getMessageCount(Exception.class);
  }

  protected int getMessageCount(Class<?> clazz) {
    return processor.getMessageCount(clazz);
  }

  protected void initializeTestInstance() {
    try {
      createProcessorInstance();
      activateReverseProcessor();
    } catch (Exception e) {
      throw new RuntimeException("While initializing test instance", e);
    }
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
    processor = UdmisComponent.create(getProcessorClass(), config);
    processor.activate();
    setTestDispatcher(processor.getDispatcher());
  }

  @NotNull
  protected abstract Class<? extends UdmisComponent> getProcessorClass();

  protected void terminateAndWait() {
    getReverseDispatcher().terminate();
    getTestDispatcher().awaitShutdown();
    getTestDispatcher().terminate();
    getReverseDispatcher().awaitShutdown();
    processor.shutdown();
  }

  private void resultHandler(Object message) {
    captured.add(message);
  }
}
