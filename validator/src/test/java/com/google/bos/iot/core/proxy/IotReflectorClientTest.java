package com.google.bos.iot.core.proxy;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import udmi.schema.ExecutionConfiguration;

/**
 * Unit tests for IotReflectorClient.
 */
public class IotReflectorClientTest {

  @Test
  public void makeReflectConfigurationTest() {
    ExecutionConfiguration iotConfig = new ExecutionConfiguration();
    iotConfig.project_id = "test-project";
    iotConfig.target_provider = "mock";
    iotConfig.udmi_namespace = "test-namespace";

    ExecutionConfiguration reflectConfig =
        IotReflectorClient.makeReflectConfiguration(iotConfig, "test-registry");

    assertEquals("target_provider copied", "mock", reflectConfig.target_provider);
    assertEquals("registry_id set to reflect base", IotReflectorClient.UDMI_REFLECT,
        reflectConfig.registry_id);
    assertEquals("device_id set to registry id", "test-registry", reflectConfig.device_id);
  }
}
