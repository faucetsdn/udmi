package com.google.daq.mqtt.mapping;

import static com.google.udmi.util.SiteModel.MOCK_PROJECT;

import org.junit.Test;
import udmi.schema.ExecutionConfiguration;

public class MappingAgentTest {

  private static final String CONFIG_SOURCE = "../tests/sites/mapping/testing_placeholder.json";

  @Test
  public void initiate_discovery() {
    MappingAgent mappingAgent = new MappingAgent(getExecutionConfig());
    mappingAgent.process();
  }

  private ExecutionConfiguration getExecutionConfig() {
    ExecutionConfiguration executionConfiguration = new ExecutionConfiguration();
    executionConfiguration.project_id = MOCK_PROJECT;
    executionConfiguration.src_file = CONFIG_SOURCE;
    return executionConfiguration;
  }
}