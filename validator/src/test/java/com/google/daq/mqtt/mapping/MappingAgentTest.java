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
import org.junit.BeforeClass;
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
  private static final String TEST_NUM_ID = "987654321";
  private static final String TEST_DEVICE_ID = "test-device-1";
  private static MappingAgent mappingAgent;
  private static SiteModel siteModel;

  @BeforeClass
  public static void setup() {
    mappingAgent = new MappingAgent(getExecutionConfig());
    siteModel = mappingAgent.getSiteModel();
    siteModel.createNewDevice(TEST_DEVICE_ID, new Metadata());
  }

  @Test
  public void initiate_discovery() {
    List<String> argsList = new ArrayList<>(ImmutableList.of("discover"));
    mappingAgent.process(argsList);
    List<MockAction> actions = mappingAgent.getMockActions().stream().map(a -> (MockAction) a)
        .toList();
    assertEquals("number of iot operations", 2, actions.size());
    MockAction configAction = actions.get(0);
    MockAction metadataAction = actions.get(1);
  }

  private static ExecutionConfiguration getExecutionConfig() {
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
    mappingAgent.stitchProperties(createDevicesMap(TEST_DEVICE_ID, TEST_NUM_ID));

    Metadata metadata = siteModel.getMetadata(TEST_DEVICE_ID);

    assertNotNull("Cloud model should have been created.", metadata.cloud);
    assertEquals("The num_id was not stitched correctly.", TEST_NUM_ID,
        metadata.cloud.num_id);
  }

  @Test
  public void stitchProperties_deviceNotInSiteModel() {
    String unknownTest = "unknown-device";
    mappingAgent.stitchProperties(createDevicesMap(unknownTest, TEST_NUM_ID));

    Metadata metadata = siteModel.getMetadata(unknownTest);
    assertNull(metadata);
  }

  @Test
  public void stitchProperties_preservesExistingCloudData() {
    // Manually add existing cloud data to the site model before the test.
    Metadata metadata = siteModel.getMetadata(TEST_DEVICE_ID);
    metadata.cloud = new CloudModel();
    metadata.cloud.auth_type = Auth_type.RS_256; // Some pre-existing data
    siteModel.updateMetadata(TEST_DEVICE_ID, metadata);

    Map<String, Map<String, Object>> devices = createDevicesMap(TEST_DEVICE_ID, TEST_NUM_ID);
    mappingAgent.stitchProperties(devices);

    Metadata updatedMetadata = siteModel.getMetadata(TEST_DEVICE_ID);
    assertNotNull(updatedMetadata.cloud);
    assertEquals("The num_id was not stitched correctly.", TEST_NUM_ID,
        updatedMetadata.cloud.num_id);
    assertEquals("Existing cloud data should be preserved.", "RS256",
        updatedMetadata.cloud.auth_type.toString());
  }
}
