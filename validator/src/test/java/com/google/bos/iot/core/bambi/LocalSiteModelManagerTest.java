package com.google.bos.iot.core.bambi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for LocalSiteModelManager.java.
 */
public class LocalSiteModelManagerTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String SITE_METADATA_FILE = "site_metadata.json";
  private static final String CLOUD_IOT_CONFIG_FILE = "cloud_iot_config.json";
  private static final String DEVICES_FOLDER = "devices";
  private static final String DEVICE_METADATA_FILE = "metadata.json";
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private LocalSiteModelManager siteModelManager;
  private File siteModelDir;

  @Before
  public void setUp() throws IOException {
    siteModelDir = tempFolder.newFolder("site_model");
    siteModelManager = new LocalSiteModelManager(siteModelDir.getAbsolutePath());
  }

  private void createJsonFile(File directory, String fileName, Map<String, Object> content) {
    File file = new File(directory, fileName);
    file.getParentFile().mkdirs();
    JsonNode jsonNode = OBJECT_MAPPER.valueToTree(content);
    JsonUtil.writeFile(jsonNode, file);
  }

  private void createJsonFile(File directory, String subDir1, String subDir2, String fileName,
      Map<String, Object> content) {
    File file = new File(directory, Paths.get(subDir1, subDir2, fileName).toString());
    file.getParentFile().mkdirs();
    JsonNode jsonNode = OBJECT_MAPPER.valueToTree(content);
    JsonUtil.writeFile(jsonNode, file);
  }

  @Test
  public void constructor_nonExistentPath_throwsIllegalArgumentException() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("site model directory does not exist");
    new LocalSiteModelManager(tempFolder.getRoot().getAbsolutePath() + "/non_existent_dir");
  }

  @Test
  public void constructor_validPath_initializes() {
    assertNotNull(siteModelManager);
  }

  @Test
  public void getSiteMetadata_fileNotFound_returnsEmptyMap() {
    Map<String, String> metadata = siteModelManager.getSiteMetadata();
    assertTrue("Expected empty map when file doesn't exist", metadata.isEmpty());
  }

  @Test
  public void getSiteMetadata_readsAndFlattensJson() {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("site_name", "Test Site");
    Map<String, Object> location = new LinkedHashMap<>();
    location.put("city", "Testville");
    location.put("country", "Testland");
    content.put("location", location);
    createJsonFile(siteModelDir, SITE_METADATA_FILE, content);

    Map<String, String> metadata = siteModelManager.getSiteMetadata();
    assertEquals("Test Site", metadata.get("site_name"));
    assertEquals("Testville", metadata.get("location.city"));
    assertEquals("Testland", metadata.get("location.country"));
    assertEquals(3, metadata.size());
  }

  @Test
  public void getCloudIotConfig_readsAndFlattensJson() {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("project_id", "gcp-123");
    content.put("registry_id", "reg-abc");
    createJsonFile(siteModelDir, CLOUD_IOT_CONFIG_FILE, content);

    Map<String, String> config = siteModelManager.getCloudIotConfig();
    assertEquals("gcp-123", config.get("project_id"));
    assertEquals("reg-abc", config.get("registry_id"));
  }

  @Test
  public void getDeviceMetadata_readsAndFlattensJson() {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("model", "XYZ-1000");
    Map<String, Object> point = new LinkedHashMap<>();
    point.put("units", "Celsius");
    content.put("points.temperature", point);
    String deviceId = "dev-001";
    createJsonFile(siteModelDir, DEVICES_FOLDER, deviceId, DEVICE_METADATA_FILE, content);

    Map<String, String> metadata = siteModelManager.getDeviceMetadata(deviceId);
    assertEquals("XYZ-1000", metadata.get("model"));
    assertEquals("Celsius", metadata.get("points.temperature.units"));
  }

  @Test
  public void getDeviceMetadata_deviceFileNotFound_returnsEmptyMap() {
    Map<String, String> metadata = siteModelManager.getDeviceMetadata("non-existent-dev");
    assertTrue(metadata.isEmpty());
  }

  @Test
  public void getAllDeviceMetadata_readsAllDevices() throws IOException {
    Map<String, Object> dev1Content = new LinkedHashMap<>();
    dev1Content.put("os", "Linux");
    createJsonFile(siteModelDir, DEVICES_FOLDER, "dev-001", DEVICE_METADATA_FILE, dev1Content);

    Map<String, Object> dev2Content = new LinkedHashMap<>();
    dev2Content.put("ip_address", "192.168.1.100");
    createJsonFile(siteModelDir, DEVICES_FOLDER, "dev-002", DEVICE_METADATA_FILE, dev2Content);

    new File(siteModelDir, DEVICES_FOLDER + "/some_file.txt").createNewFile();

    Map<String, Map<String, String>> allMetadata = siteModelManager.getAllDeviceMetadata();
    assertEquals(2, allMetadata.size());
    assertTrue(allMetadata.containsKey("dev-001"));
    assertTrue(allMetadata.containsKey("dev-002"));
    assertEquals("Linux", allMetadata.get("dev-001").get("os"));
    assertEquals("dev-001", allMetadata.get("dev-001").get("device_id"));
    assertEquals("192.168.1.100", allMetadata.get("dev-002").get("ip_address"));
    assertEquals("dev-002", allMetadata.get("dev-002").get("device_id"));
  }

  @Test
  public void getAllDeviceMetadata_noDevicesFolder_returnsEmptyMap() {
    Map<String, Map<String, String>> allMetadata = siteModelManager.getAllDeviceMetadata();
    assertTrue(allMetadata.isEmpty());
  }


  @Test
  public void writeSiteMeta_writesFlattenedDataAsNestedJson() {
    Map<String, String> dataToWrite = new LinkedHashMap<>();
    dataToWrite.put("site_name", "Written Site");
    dataToWrite.put("location.city", "WrittenCity");
    dataToWrite.put("location.zip", "W123");

    siteModelManager.writeSiteMeta(dataToWrite);

    File expectedFile = new File(siteModelDir, SITE_METADATA_FILE);
    assertTrue(expectedFile.exists());

    JsonNode writtenNode = JsonUtil.loadFile(JsonNode.class, expectedFile);
    assertNotNull(writtenNode);
    assertEquals("Written Site", writtenNode.get("site_name").asText());
    assertEquals("WrittenCity", writtenNode.get("location").get("city").asText());
    assertEquals("W123", writtenNode.get("location").get("zip").asText());
  }

  @Test
  public void writeCloudIotConfig_writesFlattenedDataAsNestedJson() {
    Map<String, String> dataToWrite = new LinkedHashMap<>();
    dataToWrite.put("project_id", "gcp-written");
    dataToWrite.put("cloud_region", "us-central1");

    siteModelManager.writeCloudIotConfig(dataToWrite);

    File expectedFile = new File(siteModelDir, CLOUD_IOT_CONFIG_FILE);
    assertTrue(expectedFile.exists());
    JsonNode writtenNode = JsonUtil.loadFile(JsonNode.class, expectedFile);
    assertNotNull(writtenNode);
    assertEquals("gcp-written", writtenNode.get("project_id").asText());
    assertEquals("us-central1", writtenNode.get("cloud_region").asText());
  }

  @Test
  public void writeDeviceMetadata_writesFlattenedDataAsNestedJson() {
    String deviceId = "dev-w01";
    Map<String, String> dataToWrite = new LinkedHashMap<>();
    dataToWrite.put("model", "Model-W");
    dataToWrite.put("firmware.version", "1.2.3");

    siteModelManager.writeDeviceMetadata(dataToWrite, deviceId);

    File expectedFile = new File(siteModelDir,
        Paths.get(DEVICES_FOLDER, deviceId, DEVICE_METADATA_FILE).toString());
    assertTrue(expectedFile.exists());
    JsonNode writtenNode = JsonUtil.loadFile(JsonNode.class, expectedFile);
    assertNotNull(writtenNode);
    assertEquals("Model-W", writtenNode.get("model").asText());
    assertEquals("1.2.3", writtenNode.get("firmware").get("version").asText());
  }

  @Test
  public void writeAllDevicesMetadata_writesAll() {
    Map<String, Map<String, String>> allData = new HashMap<>();
    Map<String, String> dev1Data = new LinkedHashMap<>();
    dev1Data.put("prop1", "val1");
    allData.put("dev-multi-1", dev1Data);

    Map<String, String> dev2Data = new LinkedHashMap<>();
    dev2Data.put("prop2.sub", "val2");
    allData.put("dev-multi-2", dev2Data);

    siteModelManager.writeAllDevicesMetadata(allData);

    File dev1File = new File(siteModelDir,
        Paths.get(DEVICES_FOLDER, "dev-multi-1", DEVICE_METADATA_FILE).toString());
    assertTrue(dev1File.exists());
    JsonNode result = JsonUtil.loadFile(JsonNode.class, dev1File);
    assertNotNull(result);
    assertEquals("val1", result.get("prop1").asText());

    File dev2File = new File(siteModelDir,
        Paths.get(DEVICES_FOLDER, "dev-multi-2", DEVICE_METADATA_FILE).toString());
    assertTrue(dev2File.exists());
    result = JsonUtil.loadFile(JsonNode.class, dev2File);
    assertNotNull(result);
    assertEquals("val2", result.get("prop2").get("sub").asText());
  }

  @Test
  public void mergeSiteMetadataOnDisk_mergesAndWrites() {
    Map<String, Object> initialContent = new LinkedHashMap<>();
    initialContent.put("key1", "old_val1");
    initialContent.put("key2", "old_val2");
    Map<String, Object> nested = new HashMap<>();
    nested.put("sub1", "old_sub_val1");
    initialContent.put("nested", nested);
    createJsonFile(siteModelDir, SITE_METADATA_FILE, initialContent);

    Map<String, String> newData = new LinkedHashMap<>();
    newData.put("key2", "new_val2"); // Update
    newData.put("key3", "new_val3"); // Add
    newData.put("nested.sub1", "__DELETE__"); // Delete nested
    newData.put("key1",
        ""); // Empty value, should not change if old was non-empty, based on current merge logic

    siteModelManager.mergeSiteMetadataOnDisk(newData);

    File expectedFile = new File(siteModelDir, SITE_METADATA_FILE);
    JsonNode mergedNode = JsonUtil.loadFile(JsonNode.class, expectedFile);

    assertNotNull(mergedNode);
    assertEquals("old_val1", mergedNode.get("key1").asText()); // Not changed by empty new value
    assertEquals("new_val2", mergedNode.get("key2").asText());
    assertEquals("new_val3", mergedNode.get("key3").asText());
    assertFalse("Nested key 'nested.sub1' should be deleted",
        mergedNode.has("nested") && mergedNode.get("nested").has("sub1"));
    assertFalse("Original 'nested' object should not exist because no subkeys remain",
        mergedNode.has("nested"));
  }

  @Test
  public void mergeSiteMetadataOnDisk_deleteNonExistentKey_noOp() {
    Map<String, Object> initialContent = new LinkedHashMap<>();
    initialContent.put("key1", "val1");
    createJsonFile(siteModelDir, SITE_METADATA_FILE, initialContent);

    Map<String, String> newData = new LinkedHashMap<>();
    newData.put("non_existent_key", "__DELETE__");

    siteModelManager.mergeSiteMetadataOnDisk(newData);
    JsonNode mergedNode = JsonUtil.loadFile(JsonNode.class,
        new File(siteModelDir, SITE_METADATA_FILE));
    assertNotNull(mergedNode);
    assertEquals("val1", mergedNode.get("key1").asText());
    assertFalse(mergedNode.has("non_existent_key"));
    assertEquals(1, mergedNode.size());
  }

  @Test
  public void mergeCloudIotConfigOnDisk_mergesAndWrites() {
    Map<String, Object> initialContent = new LinkedHashMap<>();
    initialContent.put("project_id", "old_project");
    createJsonFile(siteModelDir, CLOUD_IOT_CONFIG_FILE, initialContent);

    Map<String, String> newData = new LinkedHashMap<>();
    newData.put("project_id", "new_project");
    newData.put("registry_id", "new_registry");

    siteModelManager.mergeCloudIotConfigOnDisk(newData);

    File expectedFile = new File(siteModelDir, CLOUD_IOT_CONFIG_FILE);
    JsonNode mergedNode = JsonUtil.loadFile(JsonNode.class, expectedFile);
    assertNotNull(mergedNode);
    assertEquals("new_project", mergedNode.get("project_id").asText());
    assertEquals("new_registry", mergedNode.get("registry_id").asText());
  }

  @Test
  public void mergeAllDevicesMetadataOnDisk_mergesAndWrites() {
    String devId = "dev-m01";
    Map<String, Object> initialContent = new LinkedHashMap<>();
    initialContent.put("model", "OldModel");
    initialContent.put("status", "active");
    createJsonFile(siteModelDir, DEVICES_FOLDER, devId, DEVICE_METADATA_FILE, initialContent);

    Map<String, String> newDevData = new LinkedHashMap<>();
    newDevData.put("model", "NewModel");
    newDevData.put("location", "RoomA");
    newDevData.put("status", "__DELETE__");
    Map<String, Map<String, String>> newAllData = new HashMap<>();
    newAllData.put(devId, newDevData);

    siteModelManager.mergeAllDevicesMetadataOnDisk(newAllData);

    File expectedFile = new File(siteModelDir,
        Paths.get(DEVICES_FOLDER, devId, DEVICE_METADATA_FILE).toString());
    JsonNode mergedNode = JsonUtil.loadFile(JsonNode.class, expectedFile);
    assertNotNull(mergedNode);
    assertEquals("NewModel", mergedNode.get("model").asText());
    assertEquals("RoomA", mergedNode.get("location").asText());
    assertFalse(mergedNode.has("status"));
  }

  @Test
  public void populateMap_handlesSpecialKeys_gatewayProxyIds() {
    Map<String, String> metadataOnDisk = new LinkedHashMap<>();
    metadataOnDisk.put("gateway.proxy_ids.0", "old_proxy1");

    Map<String, String> newMetadata = new LinkedHashMap<>();
    newMetadata.put("gateway.proxy_ids", "new_proxyA, new_proxyB");

    File siteMetaFile = new File(siteModelDir, SITE_METADATA_FILE);
    JsonUtil.writeFile(JsonUtil.OBJECT_MAPPER.valueToTree(metadataOnDisk), siteMetaFile);

    siteModelManager.mergeSiteMetadataOnDisk(newMetadata);

    JsonNode writtenNode = JsonUtil.loadFile(JsonNode.class, siteMetaFile);
    assertNotNull(writtenNode);
    assertFalse("Old consolidated key gateway.proxy_ids.0 should be removed",
        writtenNode.has("gateway.proxy_ids.0"));
    assertTrue(writtenNode.has("gateway"));
    assertEquals("new_proxyA", writtenNode.get("gateway").get("proxy_ids").get(0).asText());
    assertEquals("new_proxyB", writtenNode.get("gateway").get("proxy_ids").get(1).asText());
  }

  @Test
  public void populateMap_handlesSpecialKeys_systemTags() {
    Map<String, String> metadataOnDisk = new LinkedHashMap<>();
    metadataOnDisk.put("system.tags.0", "tag_old");

    Map<String, String> newMetadata = new LinkedHashMap<>();
    newMetadata.put("system.tags", "tag_new1, tag_new2,tag_new3 ");

    File siteMetaFile = new File(siteModelDir, SITE_METADATA_FILE);
    JsonUtil.writeFile(JsonUtil.OBJECT_MAPPER.valueToTree(metadataOnDisk), siteMetaFile);

    siteModelManager.mergeSiteMetadataOnDisk(newMetadata);

    JsonNode writtenNode = JsonUtil.loadFile(JsonNode.class, siteMetaFile);
    assertNotNull(writtenNode);
    assertFalse(writtenNode.has("system.tags.0"));
    assertTrue(writtenNode.has("system"));
    assertEquals("tag_new1", writtenNode.get("system").get("tags").get(0).asText());
    assertEquals("tag_new2", writtenNode.get("system").get("tags").get(1).asText());
    assertEquals("tag_new3", writtenNode.get("system").get("tags").get(2).asText()); // trim works
  }

  @Test
  public void merge_emptyNewValue_doesNotOverwriteExisting() {
    Map<String, Object> initialContent = new HashMap<>();
    initialContent.put("key1", "value1");
    createJsonFile(siteModelDir, SITE_METADATA_FILE, initialContent);

    Map<String, String> newData = new HashMap<>();
    newData.put("key1", "");

    siteModelManager.mergeSiteMetadataOnDisk(newData);

    JsonNode result = JsonUtil.loadFile(JsonNode.class, new File(siteModelDir, SITE_METADATA_FILE));
    assertNotNull(result);
    assertEquals("value1", result.get("key1").asText()); // Should remain unchanged
  }

  @Test
  public void merge_newValueSameAsOld_noChange() {
    Map<String, Object> initialContent = new HashMap<>();
    initialContent.put("key1", "value1");
    createJsonFile(siteModelDir, SITE_METADATA_FILE, initialContent);

    Map<String, String> newData = new HashMap<>();
    newData.put("key1", "value1"); // Same value

    siteModelManager.mergeSiteMetadataOnDisk(newData);

    JsonNode result = JsonUtil.loadFile(JsonNode.class, new File(siteModelDir, SITE_METADATA_FILE));
    assertNotNull(result);
    assertEquals("value1", result.get("key1").asText());
  }
}