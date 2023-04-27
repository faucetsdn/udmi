package com.google.bos.udmi.service.core;

import static com.google.udmi.util.JsonUtil.writeFile;
import static org.apache.commons.io.FileUtils.deleteDirectory;

import com.google.bos.udmi.service.messaging.impl.LocalMessagePipeTest;
import com.google.bos.udmi.service.messaging.impl.MessageDispatcherImpl;
import com.google.udmi.util.CleanDateFormat;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.SetupUdmiConfig;

public abstract class ProcessorTestBase extends LocalMessagePipeTest {

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

  protected void initializeTestInstance() {
    try {
      writeVersionDeployFile();
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

  public static void writeVersionDeployFile() throws IOException {
    File deployFile = new File(ReflectProcessor.DEPLOY_FILE);
    deleteDirectory(deployFile.getParentFile());
    deployFile.getParentFile().mkdirs();
    SetupUdmiConfig deployedVersion = new SetupUdmiConfig();
    deployedVersion.deployed_at = TEST_TIMESTAMP;
    deployedVersion.deployed_by = TEST_USER;
    deployedVersion.udmi_functions = TEST_FUNCTIONS;
    deployedVersion.udmi_version = TEST_VERSION;
    writeFile(deployedVersion, deployFile);
  }

  @NotNull
  protected abstract Class<? extends UdmisComponent> getProcessorClass();

  protected void terminateAndWait() {
    getReverseDispatcher().terminate();
    getTestDispatcher().awaitShutdown();
    getTestDispatcher().terminate();
    getReverseDispatcher().awaitShutdown();
  }

  private void resultHandler(Object message) {
    captured.add(message);
  }
}
