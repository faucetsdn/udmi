package com.google.bos.udmi.service.core;

import static com.google.bos.udmi.service.core.StateProcessor.IOT_ACCESS_COMPONENT;
import static com.google.bos.udmi.service.messaging.impl.MessagePipeTestBase.REFLECT_REGISTRY;
import static com.google.udmi.util.JsonUtil.writeFile;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.mockito.Mockito.mock;

import com.google.bos.udmi.service.access.IotAccessBase;
import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.bos.udmi.service.messaging.impl.MessagePipeTestBase;
import com.google.bos.udmi.service.messaging.impl.MessageTestBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.udmi.util.CleanDateFormat;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Envelope;
import udmi.schema.SetupUdmiConfig;

/**
 * Base class for functional processor tests.
 */
public abstract class ProcessorTestBase extends MessageTestBase {

  public static final String TEST_USER = "giraffe@safari.com";
  public static final Date TEST_TIMESTAMP = CleanDateFormat.cleanDate();
  public static final long ASYNC_PROCESSING_DELAY_MS = 2000;
  protected final List<Object> captured = new ArrayList<>();
  protected IotAccessBase provider;
  private ProcessorBase processor;

  /**
   * Write a deployment file for testing.
   */
  public static void writeVersionDeployFile() {
    File deployFile = new File(UdmiServicePod.DEPLOY_FILE);
    try {
      deleteDirectory(deployFile.getParentFile());
      deployFile.getParentFile().mkdirs();
      SetupUdmiConfig deployedVersion = new SetupUdmiConfig();
      deployedVersion.deployed_at = TEST_TIMESTAMP;
      deployedVersion.deployed_by = TEST_USER;
      deployedVersion.udmi_version = TEST_VERSION;
      writeFile(deployedVersion, deployFile);
    } catch (Exception e) {
      throw new RuntimeException("While writing deploy file " + deployFile.getAbsolutePath(), e);
    }
  }

  protected int getDefaultCount() {
    return getMessageCount(Object.class);
  }

  protected int getExceptionCount() {
    return getMessageCount(Exception.class);
  }

  protected int getMessageCount(Class<?> clazz) {
    return processor.getMessageCount(clazz);
  }

  protected ProcessorBase getProcessor(EndpointConfiguration config,
      Class<? extends ProcessorBase> clazz) {
    return ProcessorBase.create(clazz, config);
  }

  protected <T extends ProcessorBase> T initializeTestInstance(Class<T> clazz) {
    instantiateTestInstance(clazz);
    //noinspection unchecked
    return (T) processor;
  }

  protected Bundle makeMessageBundle(Object message) {
    return new Bundle(makeTestEnvelope(false), message);
  }

  protected Envelope makeReflectEnvelope(boolean includeProject) {
    Envelope envelope = new Envelope();
    envelope.deviceRegistryId = REFLECT_REGISTRY;
    envelope.deviceId = TEST_REGISTRY;
    envelope.projectId = includeProject ? TEST_NAMESPACE : null;
    return envelope;
  }

  protected Envelope makeTestEnvelope(boolean includeProject) {
    return MessagePipeTestBase.makeTestEnvelope(false);
  }

  protected void terminateAndWait() {
    // Put termination markers into the pipe.
    getTestDispatcher().terminate();

    // Wait for all outstanding messages to be processed.
    getTestDispatcher().awaitShutdown();

    // Now put termination markers into the reversed pipe.
    getReverseDispatcher().terminate();

    // And wait for all of those outstanding messages to be processed.
    getReverseDispatcher().awaitShutdown();

    provider.shutdown();
    processor.shutdown();
  }

  private void activateReverseProcessor() {
    MessageDispatcherImpl reverseDispatcher = getReverseDispatcher();
    reverseDispatcher.registerHandler(Object.class, this::resultHandler);
    reverseDispatcher.activate();
  }

  private void initializeProcessorInstance(Class<? extends ProcessorBase> clazz) {
    EndpointConfiguration config = new EndpointConfiguration();
    config.protocol = Protocol.LOCAL;
    config.hostname = TEST_NAMESPACE;
    config.recv_id = TEST_SOURCE;
    config.send_id = TEST_DESTINATION;
    processor = getProcessor(config, clazz);
    setTestDispatcher(processor.getDispatcher());
    provider = mock(IotAccessBase.class);
    UdmiServicePod.putComponent(IOT_ACCESS_COMPONENT, () -> provider);
    processor.activate();
    provider.activate();
  }

  private void instantiateTestInstance(Class<? extends ProcessorBase> clazz) {
    try {
      UdmiServicePod.resetForTest();
      initializeProcessorInstance(clazz);
      activateReverseProcessor();
      getReverseDispatcher();
    } catch (Exception e) {
      throw new RuntimeException("While initializing test instance", e);
    }
  }

  private void resultHandler(Object message) {
    synchronized (captured) {
      captured.add(message);
    }
  }
}
