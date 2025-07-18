package com.google.bos.iot.core.bambi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.bos.iot.core.bambi.model.BambiSheetTab;
import com.google.bos.iot.core.bambi.model.BambiSiteModel;
import com.google.udmi.util.SpreadsheetManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for BambiSiteModelManager.java.
 */
@RunWith(MockitoJUnitRunner.class)
public class BambiSiteModelManagerTest {

  // Default headers for BambiSiteModel internal state
  private static final List<Object> SITE_METADATA_HEADERS_LIST = Arrays.asList("site_key1",
      "site_key2");
  private static final List<Object> CLOUD_IOT_HEADERS_LIST = List.of("cloud_iot_key1");
  private static final List<Object> SYSTEM_HEADERS_LIST = Arrays.asList(BambiSiteModel.DEVICE_ID,
      "os", "arch");
  private static final List<Object> CLOUD_HEADERS_LIST = Arrays.asList(BambiSiteModel.DEVICE_ID,
      "region");
  private static final List<Object> GATEWAY_HEADERS_LIST = Arrays.asList(BambiSiteModel.DEVICE_ID,
      "model");
  private static final List<Object> LOCALNET_HEADERS_LIST = Arrays.asList(BambiSiteModel.DEVICE_ID,
      "ip_address");
  private static final List<Object> POINTSET_HEADERS_LIST = Arrays.asList(BambiSiteModel.DEVICE_ID,
      BambiSiteModel.POINTS_TEMPLATE_NAME, "location");
  private static final List<Object> POINTS_HEADERS_LIST = Arrays.asList(
      BambiSiteModel.POINTS_TEMPLATE_NAME, BambiSiteModel.POINT_NAME, "units", "ref");
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  @Mock
  private SpreadsheetManager mockSpreadsheetManager;
  private BambiSiteModelManager bambiSiteModelManager;
  @Captor
  private ArgumentCaptor<List<List<Object>>> sheetDataCaptor;

  /**
   * Setup mock spreadsheet manager.
   */
  @Before
  public void setUp() throws Exception {
    when(mockSpreadsheetManager.getSheetRecords(
        BambiSheetTab.SITE_METADATA.getName()))
        .thenReturn(createKeyValueSheetFromHeaders(SITE_METADATA_HEADERS_LIST));
    when(mockSpreadsheetManager.getSheetRecords(
        BambiSheetTab.CLOUD_IOT_CONFIG.getName()))
        .thenReturn(createKeyValueSheetFromHeaders(CLOUD_IOT_HEADERS_LIST));
    when(mockSpreadsheetManager.getSheetRecords(BambiSheetTab.SYSTEM.getName()))
        .thenReturn(createTableSheetWithHeadersOnly(SYSTEM_HEADERS_LIST));
    when(mockSpreadsheetManager.getSheetRecords(BambiSheetTab.CLOUD.getName()))
        .thenReturn(createTableSheetWithHeadersOnly(CLOUD_HEADERS_LIST));
    when(mockSpreadsheetManager.getSheetRecords(BambiSheetTab.GATEWAY.getName()))
        .thenReturn(createTableSheetWithHeadersOnly(GATEWAY_HEADERS_LIST));
    when(
        mockSpreadsheetManager.getSheetRecords(BambiSheetTab.LOCALNET.getName()))
        .thenReturn(createTableSheetWithHeadersOnly(LOCALNET_HEADERS_LIST));
    when(
        mockSpreadsheetManager.getSheetRecords(BambiSheetTab.POINTSET.getName()))
        .thenReturn(createTableSheetWithHeadersOnly(POINTSET_HEADERS_LIST));
    when(mockSpreadsheetManager.getSheetRecords(BambiSheetTab.POINTS.getName()))
        .thenReturn(createTableSheetWithHeadersOnly(POINTS_HEADERS_LIST));
  }

  private List<List<Object>> createKeyValueSheetFromHeaders(List<Object> headers) {
    List<List<Object>> sheetData = new ArrayList<>();
    for (Object header : headers) {
      sheetData.add(Arrays.asList(header, "default_value_for_" + header));
    }
    return sheetData;
  }

  private List<List<Object>> createTableSheetWithHeadersOnly(List<Object> headers) {
    List<List<Object>> sheetData = new ArrayList<>();
    sheetData.add(new ArrayList<>(headers));
    return sheetData;
  }


  @Test
  public void constructor_successfulInitializationUsingMockManager() throws IOException {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);

    assertNotNull(bambiSiteModelManager.getBambiSiteModel());
    verify(mockSpreadsheetManager, times(1)).getSheetRecords(
        BambiSheetTab.SITE_METADATA.getName());
    verify(mockSpreadsheetManager, times(1)).getSheetRecords(
        BambiSheetTab.POINTS.getName());
    assertEquals("default_value_for_site_key1",
        bambiSiteModelManager.getSiteMetadata().get("site_key1"));
  }

  @Test
  public void constructor_getSheetRecordsThrowsException_isHandled()
      throws IOException {
    when(mockSpreadsheetManager.getSheetRecords(BambiSheetTab.SYSTEM.getName()))
        .thenThrow(new IOException("Failed to read system sheet"));

    BambiSiteModelManager manager = new BambiSiteModelManager(mockSpreadsheetManager);
    assertTrue(manager.getAllDevicesMetadata().isEmpty());
  }

  @Test
  public void getBambiSiteModel_returnsInitializedModel() {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);
    assertNotNull(bambiSiteModelManager.getBambiSiteModel());
  }

  @Test
  public void getSiteMetadata_delegatesToBambiSiteModel() {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);
    assertNotNull(bambiSiteModelManager.getSiteMetadata());
    assertEquals("default_value_for_site_key1",
        bambiSiteModelManager.getSiteMetadata().get("site_key1"));
  }

  @Test
  public void getCloudIotConfig_delegatesToBambiSiteModel() {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);
    assertNotNull(bambiSiteModelManager.getCloudIotConfig());
    assertEquals("default_value_for_cloud_iot_key1",
        bambiSiteModelManager.getCloudIotConfig().get("cloud_iot_key1"));
  }

  @Test
  public void getAllDevicesMetadata_delegatesToBambiSiteModel() throws IOException {
    List<List<Object>> systemSheetWithData = new ArrayList<>();
    systemSheetWithData.add(SYSTEM_HEADERS_LIST);
    systemSheetWithData.add(Arrays.asList("dev1", "linux", "x64"));
    when(mockSpreadsheetManager.getSheetRecords(BambiSheetTab.SYSTEM.getName()))
        .thenReturn(systemSheetWithData);

    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);
    assertNotNull(bambiSiteModelManager.getAllDevicesMetadata());
    assertTrue(bambiSiteModelManager.getAllDevicesMetadata().containsKey("dev1"));
    assertEquals("linux",
        bambiSiteModelManager.getAllDevicesMetadata().get("dev1").get("system.os"));
  }


  @Test
  public void writeSiteMetadata_writesCorrectData() throws IOException {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);

    Map<String, String> newSiteMeta = new LinkedHashMap<>();
    newSiteMeta.put("site_key1", "new_val1");
    newSiteMeta.put("site_key2", "new_val2");

    bambiSiteModelManager.writeSiteMetadata(newSiteMeta);

    verify(mockSpreadsheetManager).clearValuesFromRange(
        BambiSheetTab.SITE_METADATA.getName());
    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.SITE_METADATA.getName()), sheetDataCaptor.capture());

    List<List<Object>> writtenData = sheetDataCaptor.getValue();
    assertEquals(2, writtenData.size());
    assertEquals(Arrays.asList("site_key1", "new_val1"), writtenData.get(0));
    assertEquals(Arrays.asList("site_key2", "new_val2"), writtenData.get(1));
  }

  @Test
  public void writeSiteMetadata_usesEmptyStringForMissingKeysInNewData() throws IOException {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);

    Map<String, String> newSiteMeta = new LinkedHashMap<>();
    newSiteMeta.put("site_key1", "new_val1");

    bambiSiteModelManager.writeSiteMetadata(newSiteMeta);
    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.SITE_METADATA.getName()), sheetDataCaptor.capture());

    List<List<Object>> writtenData = sheetDataCaptor.getValue();
    assertEquals(Arrays.asList("site_key1", "new_val1"), writtenData.get(0));
    assertEquals(Arrays.asList("site_key2", ""), writtenData.get(1));
  }


  @Test
  public void writeSiteMetadata_ioExceptionOnClear_handled() throws IOException {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);
    doThrow(new IOException("Clear failed")).when(mockSpreadsheetManager)
        .clearValuesFromRange(BambiSheetTab.SITE_METADATA.getName());

    bambiSiteModelManager.writeSiteMetadata(new HashMap<>());

    verify(mockSpreadsheetManager, times(1))
        .clearValuesFromRange(BambiSheetTab.SITE_METADATA.getName());

    verify(mockSpreadsheetManager, never()).writeToRange(anyString(), anyList());
  }

  @Test
  public void writeCloudIotConfig_writesCorrectData() throws IOException {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);

    Map<String, String> newCloudIotConfig = new LinkedHashMap<>();
    newCloudIotConfig.put("cloud_iot_key1", "new_cloud_val");

    bambiSiteModelManager.writeCloudIotConfig(newCloudIotConfig);

    verify(mockSpreadsheetManager).clearValuesFromRange(
        BambiSheetTab.CLOUD_IOT_CONFIG.getName());
    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.CLOUD_IOT_CONFIG.getName()), sheetDataCaptor.capture());

    List<List<Object>> writtenData = sheetDataCaptor.getValue();
    assertEquals(1, writtenData.size());
    assertEquals(Arrays.asList("cloud_iot_key1", "new_cloud_val"), writtenData.get(0));
  }


  @Test
  public void writeDevicesMetadata_writesCorrectDataForAllSheets() throws IOException {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);

    Map<String, String> dev1Meta = new HashMap<>();
    dev1Meta.put("system." + BambiSiteModel.DEVICE_ID, "dev1");
    dev1Meta.put("system.os", "LinuxOS");
    dev1Meta.put("system.arch", "x86_64");
    dev1Meta.put("cloud.region", "us-east1");
    dev1Meta.put("gateway.model", "GW100");
    dev1Meta.put("localnet.ip_address", "192.168.1.10");
    dev1Meta.put("pointset.location", "Server Room");
    dev1Meta.put("pointset.points.temp_sensor.point_name", "temp_sensor");
    dev1Meta.put("pointset.points.temp_sensor.units", "C");
    dev1Meta.put("pointset.points.temp_sensor.ref", "temp.ref");
    dev1Meta.put("pointset.points.pressure_sensor.units", "[kPa,psi]");

    Map<String, Map<String, String>> deviceToMetadataMap = new HashMap<>();
    deviceToMetadataMap.put("dev1", dev1Meta);

    bambiSiteModelManager.writeDevicesMetadata(deviceToMetadataMap);

    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.SYSTEM.getName()), sheetDataCaptor.capture());
    List<List<Object>> systemData = sheetDataCaptor.getValue();
    assertEquals(SYSTEM_HEADERS_LIST, systemData.get(0));
    assertEquals(Arrays.asList("dev1", "LinuxOS", "x86_64"), systemData.get(1));

    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.CLOUD.getName()), sheetDataCaptor.capture());
    List<List<Object>> cloudData = sheetDataCaptor.getValue();
    assertEquals(CLOUD_HEADERS_LIST, cloudData.get(0));
    assertEquals(Arrays.asList("dev1", "us-east1"), cloudData.get(1));

    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.GATEWAY.getName()), sheetDataCaptor.capture());
    List<List<Object>> gatewayData = sheetDataCaptor.getValue();
    assertEquals(GATEWAY_HEADERS_LIST, gatewayData.get(0));
    assertEquals(Arrays.asList("dev1", "GW100"), gatewayData.get(1));

    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.LOCALNET.getName()), sheetDataCaptor.capture());
    List<List<Object>> localnetData = sheetDataCaptor.getValue();
    assertEquals(LOCALNET_HEADERS_LIST, localnetData.get(0));
    assertEquals(Arrays.asList("dev1", "192.168.1.10"), localnetData.get(1));

    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.POINTSET.getName()), sheetDataCaptor.capture());
    List<List<Object>> pointsetData = sheetDataCaptor.getValue();
    assertEquals(POINTSET_HEADERS_LIST, pointsetData.get(0));
    assertEquals(Arrays.asList("dev1", "dev1_template", "Server Room"), pointsetData.get(1));

    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.POINTS.getName()), sheetDataCaptor.capture());
    List<List<Object>> pointsData = sheetDataCaptor.getValue();
    assertEquals(POINTS_HEADERS_LIST, pointsData.get(0));

    boolean foundTempSensor = false;
    boolean foundPressureSensor = false;
    for (int i = 1; i < pointsData.size(); i++) {
      List<Object> row = pointsData.get(i);
      if ("temp_sensor".equals(row.get(1))) {
        assertEquals(Arrays.asList("dev1_template", "temp_sensor", "C", "temp.ref"), row);
        foundTempSensor = true;
      } else if ("pressure_sensor".equals(row.get(1))) {
        assertEquals(Arrays.asList("dev1_template", "pressure_sensor", "kPa,psi", ""), row);
        foundPressureSensor = true;
      }
    }
    assertTrue("Temperature sensor point data not found or incorrect", foundTempSensor);
    assertTrue("Pressure sensor point data not found or incorrect", foundPressureSensor);
    assertEquals(3, pointsData.size());
  }

  @Test
  public void writeDevicesMetadata_emptyDeviceMap_writesOnlyHeaders() throws IOException {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);
    bambiSiteModelManager.writeDevicesMetadata(new HashMap<>());

    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.SYSTEM.getName()), sheetDataCaptor.capture());
    List<List<Object>> systemData = sheetDataCaptor.getValue();
    assertEquals(1, systemData.size());
    assertEquals(SYSTEM_HEADERS_LIST, systemData.get(0));

    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.POINTS.getName()), sheetDataCaptor.capture());
    List<List<Object>> pointsData = sheetDataCaptor.getValue();
    assertEquals(1, pointsData.size());
    assertEquals(POINTS_HEADERS_LIST, pointsData.get(0));
  }


  @Test
  public void writeDevicesMetadata_ioExceptionOnWrite_isHandled() throws IOException {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);
    doThrow(new IOException("Write to Gateway failed")).when(mockSpreadsheetManager)
        .writeToRange(eq(BambiSheetTab.GATEWAY.getName()), anyList());

    Map<String, Map<String, String>> deviceToMetadataMap = new HashMap<>();
    Map<String, String> dev1Meta = new HashMap<>();
    dev1Meta.put("gateway.model", "TestModel");
    deviceToMetadataMap.put("dev1", dev1Meta);

    assertDoesNotThrow(() -> bambiSiteModelManager.writeDevicesMetadata(deviceToMetadataMap));
  }

  @Test
  public void buildDataRow_handlesListLikeValuesCorrectly() throws IOException {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);

    Map<String, Map<String, String>> deviceToMetadataMap = new HashMap<>();
    Map<String, String> dev1Meta = new HashMap<>();
    dev1Meta.put("system.os", "[Linux,Ubuntu]");
    dev1Meta.put("system.arch", "[]");
    deviceToMetadataMap.put("dev1", dev1Meta);

    bambiSiteModelManager.writeDevicesMetadata(deviceToMetadataMap);

    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.SYSTEM.getName()), sheetDataCaptor.capture());
    List<List<Object>> systemData = sheetDataCaptor.getValue();
    assertEquals(Arrays.asList("dev1", "Linux,Ubuntu", ""), systemData.get(1));
  }

  @Test
  public void computePointsAndPointsetsAsTableData_handlesMalformedPointKey() throws IOException {
    bambiSiteModelManager = new BambiSiteModelManager(mockSpreadsheetManager);

    Map<String, Map<String, String>> deviceToMetadataMap = new HashMap<>();
    Map<String, String> dev1Meta = new HashMap<>();
    dev1Meta.put("pointset.points.temp.units", "C");
    dev1Meta.put("pointset.points.keyonly", "some_value");
    deviceToMetadataMap.put("dev1", dev1Meta);

    bambiSiteModelManager.writeDevicesMetadata(deviceToMetadataMap);

    verify(mockSpreadsheetManager).writeToRange(
        eq(BambiSheetTab.POINTS.getName()), sheetDataCaptor.capture());
    List<List<Object>> pointsData = sheetDataCaptor.getValue();
    assertEquals(3, pointsData.size()); // Header + 1 valid point + 1 point with no properties
    boolean foundTempPoint = false;
    boolean foundKeyOnlyPoint = false;
    for (int i = 1; i < pointsData.size(); i++) {
      if ("temp".equals(pointsData.get(i).get(1))) {
        assertEquals(Arrays.asList("dev1_template", "temp", "C", ""), pointsData.get(i));
        foundTempPoint = true;
      }
      if ("keyonly".equals(pointsData.get(i).get(1))) {
        assertEquals(Arrays.asList("dev1_template", "keyonly", "", ""), pointsData.get(i));
        foundKeyOnlyPoint = true;
      }
    }
    assertTrue("Valid temp point not found", foundTempPoint);
    assertTrue("Key only point not found", foundKeyOnlyPoint);
  }
}