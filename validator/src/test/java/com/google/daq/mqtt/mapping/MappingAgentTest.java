package com.google.daq.mqtt.mapping;

import static com.google.daq.mqtt.TestCommon.GATEWAY_ID;
import static com.google.daq.mqtt.TestCommon.REGISTRY_ID;
import static com.google.udmi.util.SiteModel.MOCK_PROJECT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.util.IotMockProvider.MockAction;
import com.google.udmi.util.SiteModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.Metadata;

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
  @Test
  public void stitchProperties_happyPath() {
    MappingAgent mappingAgent = new MappingAgent(getExecutionConfig());

    // Create the mock device data that would come from a Pub/Sub message.
    Map<String, Map<String, Object>> devices = new HashMap<>();
    Map<String, Object> deviceData = new HashMap<>();
    String testNumId = "987654321";
    deviceData.put("num_id", testNumId);
    devices.put(GATEWAY_ID, deviceData);

    mappingAgent.stitchProperties(devices);

    // Check that the site model was updated correctly.
    SiteModel siteModel = mappingAgent.getSiteModel();
    Metadata metadata = siteModel.getMetadata(GATEWAY_ID);

    assertNotNull("Cloud model should have been created.", metadata.cloud);
    assertEquals("The num_id was not stitched correctly.", testNumId, metadata.cloud.num_id);
  }
}
