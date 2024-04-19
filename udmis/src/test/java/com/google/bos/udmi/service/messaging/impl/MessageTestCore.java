package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.JsonUtil.writeFile;
import static org.apache.commons.io.FileUtils.deleteDirectory;

import com.google.bos.udmi.service.core.ProcessorTestBase;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.SetupUdmiConfig;

/**
 * Core functions and constants for testing anything message related.
 */
public abstract class MessageTestCore {

  public static final String TEST_DEVICE = "bacnet-3104810";
  public static final String TEST_GATEWAY = "manager";
  public static final String TEST_REGION = "us-central1";
  public static final String TEST_REGISTRY = "TEST_REGISTRY";
  public static final String TEST_PROJECT = "TEST_PROJECT";
  public static final String TEST_POINT = "test_point";
  public static final String TEST_NUMID = "7239821792187321";
  protected static final String TEST_NAMESPACE = "test-namespace";
  protected static final String TEST_SOURCE = "message_from";
  protected static final String TEST_DESTINATION = "message_to";
  protected static final String TEST_VERSION = "1.4.1";
  protected static final String TEST_REF = "g123456789";

  {
    // Write this first so other static code picks up this data.
    writeVersionDeployFile();
  }

  /**
   * Write a deployment file for testing.
   */
  public static void writeVersionDeployFile() {
    File deployFile = new File(UdmiServicePod.DEPLOY_FILE);
    try {
      deleteDirectory(deployFile.getParentFile());
      deployFile.getParentFile().mkdirs();
      SetupUdmiConfig deployedVersion = new SetupUdmiConfig();
      deployedVersion.deployed_at = ProcessorTestBase.TEST_TIMESTAMP;
      deployedVersion.deployed_by = ProcessorTestBase.TEST_USER;
      deployedVersion.udmi_version = TEST_VERSION;
      deployedVersion.udmi_ref = TEST_REF;
      writeFile(deployedVersion, deployFile);
    } catch (Exception e) {
      throw new RuntimeException("While writing deploy file " + deployFile.getAbsolutePath(), e);
    }
  }

  protected void augmentConfig(@NotNull EndpointConfiguration configuration, boolean reversed) {
    configuration.protocol = Protocol.LOCAL;
  }

  protected void debug(String message) {
    System.err.println(message);
  }

  protected EndpointConfiguration getMessageConfig(boolean reversed) {
    EndpointConfiguration config = new EndpointConfiguration();
    config.hostname = TEST_NAMESPACE;
    config.recv_id = reversed ? TEST_DESTINATION : TEST_SOURCE;
    config.send_id = reversed ? TEST_SOURCE : TEST_DESTINATION;
    augmentConfig(config, reversed);
    return config;
  }
}
