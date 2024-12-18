package com.google.daq.mqtt.mapping;

import static com.google.daq.mqtt.TestCommon.GATEWAY_ID;
import static com.google.daq.mqtt.TestCommon.REGISTRY_ID;
import static com.google.udmi.util.SiteModel.MOCK_PROJECT;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.util.IotMockProvider.MockAction;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import udmi.schema.ExecutionConfiguration;

/**
 * Test the mapping agent.
 */
public class MappingAgentTest {

  private static final String CONFIG_SOURCE = "../tests/sites/mapping/testing_placeholder.json";

  @Test
  public void initiate_discovery() {
    List<String> argsList = new ArrayList<>(ImmutableList.of("discover"));
    MappingAgent mappingAgent = new MappingAgent(getExecutionConfig());
    mappingAgent.process(argsList);
    List<MockAction> actions = mappingAgent.getMockActions().stream().map(a -> (MockAction) a)
        .toList();
    assertEquals("number of iot operations", 2, actions.size());
    MockAction configAction = actions.get(0);
    MockAction metadataAction = actions.get(1);
  }

  private ExecutionConfiguration getExecutionConfig() {
    ExecutionConfiguration executionConfiguration = new ExecutionConfiguration();
    executionConfiguration.project_id = MOCK_PROJECT;
    executionConfiguration.device_id = GATEWAY_ID;
    executionConfiguration.registry_id = REGISTRY_ID;
    executionConfiguration.src_file = CONFIG_SOURCE;
    executionConfiguration.site_name = REGISTRY_ID;
    return executionConfiguration;
  }
}
