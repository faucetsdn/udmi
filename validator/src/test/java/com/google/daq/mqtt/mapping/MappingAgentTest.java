package com.google.daq.mqtt.mapping;

import static com.google.udmi.util.SiteModel.MOCK_PROJECT;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.util.IotMockProvider;
import com.google.daq.mqtt.util.IotMockProvider.MockAction;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import udmi.schema.ExecutionConfiguration;

public class MappingAgentTest {

  private static final String CONFIG_SOURCE = "../tests/sites/mapping/testing_placeholder.json";

  @Test
  public void initiate_discovery() {
    ImmutableList<String> argsList = ImmutableList.of("discover");
    MappingAgent mappingAgent = new MappingAgent(getExecutionConfig());
    mappingAgent.process(argsList);
    List<MockAction> actions = mappingAgent.getMockActions().stream().map(a -> (MockAction) a).toList();

  }

  private ExecutionConfiguration getExecutionConfig() {
    ExecutionConfiguration executionConfiguration = new ExecutionConfiguration();
    executionConfiguration.project_id = MOCK_PROJECT;
    executionConfiguration.src_file = CONFIG_SOURCE;
    return executionConfiguration;
  }
}