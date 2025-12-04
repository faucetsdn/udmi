package com.google.daq.mqtt.mapping;

import static com.google.daq.mqtt.TestCommon.GATEWAY_ID;
import static com.google.daq.mqtt.TestCommon.REGISTRY_ID;
import static com.google.udmi.util.SiteModel.MOCK_PROJECT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.common.collect.ImmutableList;
import com.google.daq.mqtt.util.IotMockProvider.MockAction;
import com.google.udmi.util.SiteModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import udmi.schema.CloudModel;
import udmi.schema.CloudModel.Auth_type;
import udmi.schema.ExecutionConfiguration;
import udmi.schema.Metadata;

/**
 * Test the mapping agent.
 */
public class MappingAgentTest {

  private static final String CONFIG_SOURCE = "../tests/sites/mapping/testing_placeholder.json";
  private static final String testNumId = "987654321";
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

  private Map<String, Map<String, Object>> createDevicesMap(String deviceId, String numId) {
    Map<String, Map<String, Object>> devices = new HashMap<>();
    Map<String, Object> deviceData = new HashMap<>();

    if (numId != null) {
      deviceData.put("num_id", numId);
    }
    devices.put(deviceId, deviceData);
    return devices;
  }

  @Test
  public void stitchProperties_happyPath() {
    MappingAgent mappingAgent = new MappingAgent(getExecutionConfig());
    mappingAgent.stitchProperties(createDevicesMap(GATEWAY_ID, testNumId));

    SiteModel siteModel = mappingAgent.getSiteModel();
    Metadata metadata = siteModel.getMetadata(GATEWAY_ID);

    assertNotNull("Cloud model should have been created.", metadata.cloud);
    assertEquals("The num_id was not stitched correctly.", testNumId, metadata.cloud.num_id);
  }

  @Test
  public void stitchProperties_deviceNotInSiteModel() {
    MappingAgent mappingAgent = new MappingAgent(getExecutionConfig());
    String unknownTest = "unknown-device";
    mappingAgent.stitchProperties(createDevicesMap(unknownTest, "987654321"));

    SiteModel siteModel = mappingAgent.getSiteModel();
    Metadata metadata = siteModel.getMetadata(unknownTest);
    assertNull(metadata);
  }

  @Test
  public void stitchProperties_preservesExistingCloudData() {
    MappingAgent mappingAgent = new MappingAgent(getExecutionConfig());

    // Manually add existing cloud data to the site model before the test.
    SiteModel siteModel = mappingAgent.getSiteModel();
    Metadata metadata = siteModel.getMetadata(GATEWAY_ID);
    metadata.cloud = new CloudModel();
    metadata.cloud.auth_type = Auth_type.RS_256; // Some pre-existing data
    siteModel.updateMetadata(GATEWAY_ID, metadata);

    Map<String, Map<String, Object>> devices = createDevicesMap(GATEWAY_ID, testNumId);
    mappingAgent.stitchProperties(devices);

    Metadata updatedMetadata = siteModel.getMetadata(GATEWAY_ID);
    assertNotNull(updatedMetadata.cloud);
    assertEquals("The num_id was not stitched correctly.", testNumId, updatedMetadata.cloud.num_id);
    assertEquals("Existing cloud data should be preserved.", "RS256", updatedMetadata.cloud.auth_type.toString());
  }
}
