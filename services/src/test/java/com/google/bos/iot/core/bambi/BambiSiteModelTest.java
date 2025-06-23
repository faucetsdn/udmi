package com.google.bos.iot.core.bambi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.google.bos.iot.core.bambi.model.BambiSheetTab;
import com.google.bos.iot.core.bambi.model.BambiSiteModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Tests for BambiSiteModel.java.
 */
public class BambiSiteModelTest {

  private static final String PREFIX_SYSTEM = BambiSheetTab.SYSTEM.getName();
  private static final String PREFIX_CLOUD = BambiSheetTab.CLOUD.getName();
  private static final String PREFIX_POINTSET = BambiSheetTab.POINTSET.getName();


  // Helper methods for creating sheet data
  private List<List<Object>> createKeyValueList(Object... keyValuePairs) {
    List<List<Object>> list = new ArrayList<>();
    if (keyValuePairs == null) {
      return list;
    }
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      if (i + 1 < keyValuePairs.length) {
        list.add(Arrays.asList(keyValuePairs[i], keyValuePairs[i + 1]));
      } else {
        list.add(Collections.singletonList(keyValuePairs[i]));
      }
    }
    return list;
  }

  private List<List<Object>> createTableListFromArrays(Object[] headers, Object[]... rows) {
    List<List<Object>> sheet = new ArrayList<>();
    if (headers != null) {
      sheet.add(new ArrayList<>(Arrays.asList(headers)));
    }
    if (rows != null) {
      for (Object[] row : rows) {
        sheet.add(new ArrayList<>(Arrays.asList(row)));
      }
    }
    return sheet;
  }

  private List<List<Object>> emptySheet() {
    return new ArrayList<>();
  }

  private List<List<Object>> headerOnlySheet(String... headers) {
    List<List<Object>> sheet = new ArrayList<>();
    sheet.add(Arrays.asList((Object[]) headers));
    return sheet;
  }


  @Test
  public void constructor_withValidData_parsesAllSheetsCorrectly() {
    // key value type data
    List<List<Object>> siteMetadataSheet = createKeyValueList("siteName", "Test Site", "version",
        "1.0");
    List<List<Object>> cloudIotConfigSheet = createKeyValueList("projectId", "gcp-123",
        "registryId", "reg-abc");

    // table type data
    List<List<Object>> systemSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, "os", "arch"},
        new Object[]{"sys-dev-01", "Linux", "x64"}
    );
    List<List<Object>> cloudSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, "region"},
        new Object[]{"cld-dev-01", "us-central1"}
    );
    List<List<Object>> gatewaySheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, "model_no"},
        new Object[]{"gw-dev-01", "GW-XYZ"}
    );
    List<List<Object>> localnetSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, "ip_addr"},
        new Object[]{"loc-dev-01", "192.168.1.100"}
    );
    List<List<Object>> pointsetSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, BambiSiteModel.POINTS_TEMPLATE_NAME, "location"},
        new Object[]{"ps-dev-01", "temp_sensor_tpl", "Room 101"}
    );
    List<List<Object>> pointsSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.POINTS_TEMPLATE_NAME, BambiSiteModel.POINT_NAME, "unit",
            "precision"},
        new Object[]{"temp_sensor_tpl", "temperature", "C", "0.1"}
    );

    BambiSiteModel model = new BambiSiteModel(
        siteMetadataSheet, cloudIotConfigSheet, systemSheet, cloudSheet,
        gatewaySheet, localnetSheet, pointsetSheet, pointsSheet
    );

    // Assert Site Metadata
    assertEquals("Test Site", model.getSiteMetadata().get("siteName"));
    assertEquals(Arrays.asList("siteName", "version"), model.getSiteMetadataHeaders());

    // Assert Cloud IoT Config
    assertEquals("gcp-123", model.getCloudIotConfig().get("projectId"));
    assertEquals(Arrays.asList("projectId", "registryId"), model.getCloudIotConfigHeaders());

    // Assert System Data
    assertEquals(Arrays.asList(BambiSiteModel.DEVICE_ID, "os", "arch"),
        model.getSystemDataHeaders());
    assertEquals(1, model.getSystemData().size());
    assertEquals("sys-dev-01", model.getSystemData().get(0).get(BambiSiteModel.DEVICE_ID));

    // Assert Cloud Data
    assertEquals(Arrays.asList(BambiSiteModel.DEVICE_ID, "region"), model.getCloudDataHeaders());
    assertEquals("cld-dev-01", model.getCloudData().get(0).get(BambiSiteModel.DEVICE_ID));

    // Assert Gateway Data
    assertEquals(Arrays.asList(BambiSiteModel.DEVICE_ID, "model_no"),
        model.getGatewayDataHeaders());
    assertEquals("gw-dev-01", model.getGatewayData().get(0).get(BambiSiteModel.DEVICE_ID));

    // Assert Localnet Data
    assertEquals(Arrays.asList(BambiSiteModel.DEVICE_ID, "ip_addr"),
        model.getLocalnetDataHeaders());
    assertEquals("loc-dev-01", model.getLocalnetData().get(0).get(BambiSiteModel.DEVICE_ID));

    // Assert Pointset Data
    assertEquals(
        Arrays.asList(BambiSiteModel.DEVICE_ID, BambiSiteModel.POINTS_TEMPLATE_NAME, "location"),
        model.getPointsetDataHeaders());
    assertEquals("ps-dev-01", model.getPointsetData().get(0).get(BambiSiteModel.DEVICE_ID));

    // Assert Points Data
    assertEquals(
        Arrays.asList(BambiSiteModel.POINTS_TEMPLATE_NAME, BambiSiteModel.POINT_NAME, "unit",
            "precision"), model.getPointsDataHeaders());
    assertEquals("temp_sensor_tpl",
        model.getPointsData().get(0).get(BambiSiteModel.POINTS_TEMPLATE_NAME));

    // Assert All Devices Metadata
    assertEquals(5, model.getAllDevicesMetadata().size());
    Map<String, String> psDeviceMeta = model.getDeviceMetadata("ps-dev-01");
    assertNotNull(psDeviceMeta);
    assertEquals("Room 101", psDeviceMeta.get(PREFIX_POINTSET + ".location"));
    assertEquals("C", psDeviceMeta.get(PREFIX_POINTSET + ".points.temperature.unit"));
    assertEquals("0.1", psDeviceMeta.get(PREFIX_POINTSET + ".points.temperature.precision"));
    assertEquals("temperature",
        psDeviceMeta.get(PREFIX_POINTSET + ".points.temperature." + BambiSiteModel.POINT_NAME));
    assertEquals("temp_sensor_tpl", psDeviceMeta.get(
        PREFIX_POINTSET + ".points.temperature." + BambiSiteModel.POINTS_TEMPLATE_NAME));
  }

  @Test
  public void constructor_withMinimalValidSheets_initializesCorrectly() {
    List<List<Object>> emptyKvSheet = emptySheet();
    List<List<Object>> sysHeader = headerOnlySheet(BambiSiteModel.DEVICE_ID, "sys_col");
    List<List<Object>> psHeader = headerOnlySheet(BambiSiteModel.DEVICE_ID,
        BambiSiteModel.POINTS_TEMPLATE_NAME);
    List<List<Object>> ptsHeader = headerOnlySheet(BambiSiteModel.POINTS_TEMPLATE_NAME,
        BambiSiteModel.POINT_NAME);

    BambiSiteModel model = new BambiSiteModel(
        emptyKvSheet, emptyKvSheet, sysHeader, sysHeader,
        sysHeader, sysHeader, psHeader, ptsHeader
    );

    assertTrue(model.getSiteMetadata().isEmpty());
    assertTrue(model.getCloudIotConfig().isEmpty());
    assertTrue(model.getSystemData().isEmpty());
    assertEquals(Arrays.asList(BambiSiteModel.DEVICE_ID, "sys_col"), model.getSystemDataHeaders());
    assertTrue(model.getAllDevicesMetadata().isEmpty());
  }

  @Test
  public void constructor_tableSheetWithEmptyHeaderListIsHandled() {
    List<List<Object>> tableSheetWithEmptyHeaderRowList = new ArrayList<>();
    tableSheetWithEmptyHeaderRowList.add(new ArrayList<>()); // Header row is an empty list

    BambiSiteModel model = new BambiSiteModel(
        emptySheet(), emptySheet(), tableSheetWithEmptyHeaderRowList, headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h")
    );
    assertNull(model.getSystemDataHeaders());
  }

  @Test
  public void constructor_tableSheetWithEmptyCellsInHeader_parsesAsEmptyStrings() {
    List<List<Object>> tableSheetWithEmptyHeaderCells = new ArrayList<>();
    tableSheetWithEmptyHeaderCells.add(Arrays.asList(null, "Header2", ""));

    BambiSiteModel model = new BambiSiteModel(
        emptySheet(), emptySheet(), tableSheetWithEmptyHeaderCells, headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h")
    );
    assertEquals(Arrays.asList("", "Header2", ""), model.getSystemDataHeaders());
  }

  @Test
  public void constructor_emptyTableSheet_throwsIndexOutOfBoundsException() {
    assertDoesNotThrow(() -> new BambiSiteModel(
        emptySheet(), emptySheet(), emptySheet(), // systemData is completely empty
        headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h")
    ));
  }

  @Test
  public void getKeyValueMapFromSheet_handlesVariations() {
    List<List<Object>> dataWithNullRow = new ArrayList<>();
    dataWithNullRow.add(null);
    dataWithNullRow.add(Arrays.asList("key1", "val1"));
    BambiSiteModel model1 = new BambiSiteModel(dataWithNullRow, emptySheet(), headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"),
        headerOnlySheet("h"));
    assertEquals("val1", model1.getSiteMetadata().get("key1"));
    assertEquals(1, model1.getSiteMetadata().size());

    List<List<Object>> dataWithEmptyRow = new ArrayList<>();
    dataWithEmptyRow.add(new ArrayList<>());
    dataWithEmptyRow.add(Arrays.asList("key2", "val2"));
    BambiSiteModel model2 = new BambiSiteModel(dataWithEmptyRow, emptySheet(), headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"),
        headerOnlySheet("h"));
    assertEquals("val2", model2.getSiteMetadata().get("key2"));
    assertEquals(1, model2.getSiteMetadata().size());

    List<List<Object>> dataKeyOnly = createKeyValueList("key3"); // key3, no value
    BambiSiteModel model3 = new BambiSiteModel(dataKeyOnly, emptySheet(), headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"),
        headerOnlySheet("h"));
    assertEquals("", model3.getSiteMetadata().get("key3"));

    List<List<Object>> dataNulls = createKeyValueList(null, "val4", "key5", null);
    BambiSiteModel model4 = new BambiSiteModel(dataNulls, emptySheet(), headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"),
        headerOnlySheet("h"));
    assertEquals("val4", model4.getSiteMetadata().get("")); // null key -> ""
    assertEquals("", model4.getSiteMetadata().get("key5"));   // null value -> ""
  }

  @Test
  public void getRowsFromSheet_handlesFewerColumnsInData() {
    List<List<Object>> fewerColsData = createTableListFromArrays(
        new Object[]{"h1", "h2", "h3"},
        new Object[]{"r1c1", "r1c2"}, new Object[]{"r2c1"}
    );
    BambiSiteModel model = new BambiSiteModel(emptySheet(), emptySheet(), fewerColsData,
        headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"),
        headerOnlySheet("h"));
    assertEquals("", model.getSystemData().get(0).get("h3"));
    assertEquals("", model.getSystemData().get(1).get("h2"));
  }

  @Test
  public void getRowsFromSheet_handlesMoreColumnsInData() {
    List<List<Object>> moreColsData = createTableListFromArrays(
        new Object[]{"h1", "h2"}, new Object[]{"r1c1", "r1c2", "ignored"}
    );
    BambiSiteModel model = new BambiSiteModel(emptySheet(), emptySheet(), moreColsData,
        headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"),
        headerOnlySheet("h"));
    assertEquals(2, model.getSystemData().get(0).size());
    assertFalse(model.getSystemData().get(0).containsKey("ignored")); // Or check specific keys
  }

  @Test
  public void getRowsFromSheet_handlesNoRows() {
    List<List<Object>> dataWithNullCell = createTableListFromArrays(new Object[]{"h1"});
    BambiSiteModel model = new BambiSiteModel(
        emptySheet(), emptySheet(), dataWithNullCell, headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h")
    );
    assertEquals(1, model.getSystemDataHeaders().size());
    assertEquals(0, model.getSystemData().size());
  }


  @Test
  public void mergePointsWithPointset_successfulMerge() {
    List<List<Object>> pointsetSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, BambiSiteModel.POINTS_TEMPLATE_NAME, "unit"},
        new Object[]{"dev1", "tpl_A", "Celsius"}
    );
    List<List<Object>> pointsSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.POINTS_TEMPLATE_NAME, BambiSiteModel.POINT_NAME, "offset",
            "scale"},
        new Object[]{"tpl_A", "temp1", "0.1", "1.0"},
        new Object[]{"tpl_A", "temp2", "0.2", "0.9"}
    );

    BambiSiteModel model = new BambiSiteModel(emptySheet(), emptySheet(), headerOnlySheet("h"),
        headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h"), pointsetSheet, pointsSheet);

    Map<String, String> dev1Meta = model.getDeviceMetadata("dev1");
    assertNotNull(dev1Meta);
    assertEquals("Celsius", dev1Meta.get(PREFIX_POINTSET + ".unit"));
    assertEquals("0.1", dev1Meta.get(PREFIX_POINTSET + ".points.temp1.offset"));
    assertEquals("0.9", dev1Meta.get(PREFIX_POINTSET + ".points.temp2.scale"));
  }

  @Test
  public void mergePointsWithPointset_pointTemplateNotFound_throwsRuntimeException() {
    List<List<Object>> pointsetSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, BambiSiteModel.POINTS_TEMPLATE_NAME},
        new Object[]{"dev1", "tpl_A"}
    );
    List<List<Object>> pointsSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.POINTS_TEMPLATE_NAME, BambiSiteModel.POINT_NAME},
        new Object[]{"tpl_B", "temp1"} // Uses undefined tpl_B
    );

    try {
      new BambiSiteModel(
          emptySheet(), emptySheet(), headerOnlySheet("h"), headerOnlySheet("h"),
          headerOnlySheet("h"), headerOnlySheet("h"), pointsetSheet, pointsSheet
      );
      fail("Expected RuntimeException was not thrown.");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage()
          .contains("points use a template name which is not defined in the pointset: tpl_B"));
    }
  }

  @Test
  public void mergePointsWithPointset_emptyPointsData_mergesPointsetOnly() {
    List<List<Object>> pointsetSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, BambiSiteModel.POINTS_TEMPLATE_NAME, "unit"},
        new Object[]{"dev1", "tpl_A", "Celsius"}
    );
    List<List<Object>> pointsSheetEmpty = headerOnlySheet(BambiSiteModel.POINTS_TEMPLATE_NAME,
        BambiSiteModel.POINT_NAME);

    BambiSiteModel model = new BambiSiteModel(emptySheet(), emptySheet(), headerOnlySheet("h"),
        headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h"), pointsetSheet, pointsSheetEmpty);

    Map<String, String> dev1Meta = model.getDeviceMetadata("dev1");
    assertNotNull(dev1Meta);
    assertEquals("Celsius", dev1Meta.get(PREFIX_POINTSET + ".unit"));
    for (String key : dev1Meta.keySet()) {
      assertFalse("No points data should be present", key.startsWith(PREFIX_POINTSET + ".points."));
    }
  }

  @Test
  public void mergePointsWithPointset_emptyPointsetData_withPoints_throwsRuntimeException() {
    List<List<Object>> pointsetSheetEmpty = headerOnlySheet(BambiSiteModel.DEVICE_ID,
        BambiSiteModel.POINTS_TEMPLATE_NAME);
    List<List<Object>> pointsSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.POINTS_TEMPLATE_NAME, BambiSiteModel.POINT_NAME},
        new Object[]{"tpl_A", "temp1"}
    );

    try {
      new BambiSiteModel(
          emptySheet(), emptySheet(), headerOnlySheet("h"), headerOnlySheet("h"),
          headerOnlySheet("h"), headerOnlySheet("h"), pointsetSheetEmpty, pointsSheet
      );
      fail("Expected RuntimeException was not thrown.");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage()
          .contains("points use a template name which is not defined in the pointset: tpl_A"));
    }
  }

  @Test
  public void mergePointsWithPointset_pointsRowWithEmptyPointName_usesEmptyStringInKey() {
    List<List<Object>> pointsetSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, BambiSiteModel.POINTS_TEMPLATE_NAME},
        new Object[]{"dev1", "tpl1"}
    );
    List<List<Object>> pointsSheetEmptyPointName = createTableListFromArrays(
        new Object[]{BambiSiteModel.POINTS_TEMPLATE_NAME, BambiSiteModel.POINT_NAME, "data_col"},
        new Object[]{"tpl1", "", "value1"} // Point name is an empty string
    );

    BambiSiteModel model = new BambiSiteModel(emptySheet(), emptySheet(), headerOnlySheet("h"),
        headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h"), pointsetSheet, pointsSheetEmptyPointName);
    Map<String, String> dev1Meta = model.getDeviceMetadata("dev1");
    assertNotNull(dev1Meta);
    assertEquals("value1", dev1Meta.get(PREFIX_POINTSET + ".points..data_col"));
    assertEquals("", dev1Meta.get(PREFIX_POINTSET + ".points.." + BambiSiteModel.POINT_NAME));
  }

  @Test(expected = RuntimeException.class)
  public void mergePointsWithPointset_pointsRowWithNullTemplateName_throwsException() {
    List<List<Object>> pointsetSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, BambiSiteModel.POINTS_TEMPLATE_NAME},
        new Object[]{"dev1", "tpl1"}
    );
    List<List<Object>> pointsSheetNullTemplateName = createTableListFromArrays(
        new Object[]{BambiSiteModel.POINTS_TEMPLATE_NAME, BambiSiteModel.POINT_NAME},
        new Object[]{null, "P1"} // Java null in template name cell
    );
    new BambiSiteModel(
        emptySheet(), emptySheet(), headerOnlySheet("h"), headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h"), pointsetSheet, pointsSheetNullTemplateName
    );
  }

  @Test
  public void mergePointsWithPointset_pointsRowWithStringValueNullTemplateName_throwsException() {
    List<List<Object>> pointsetSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, BambiSiteModel.POINTS_TEMPLATE_NAME},
        new Object[]{"dev1", "tpl1"}
    );
    List<List<Object>> pointsSheetStringNullTemplateName = createTableListFromArrays(
        new Object[]{BambiSiteModel.POINTS_TEMPLATE_NAME, BambiSiteModel.POINT_NAME},
        new Object[]{"null", "P1"} // String "null" as template name
    );
    try {
      new BambiSiteModel(
          emptySheet(), emptySheet(), headerOnlySheet("h"), headerOnlySheet("h"),
          headerOnlySheet("h"), headerOnlySheet("h"), pointsetSheet,
          pointsSheetStringNullTemplateName
      );
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage()
          .contains("points use a template name which is not defined in the pointset: null"));
    }
  }


  @Test
  public void computeDevicesMetadata_aggregatesFromAllSheets_withPrefixes() {
    List<List<Object>> systemSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, "os"}, new Object[]{"dev1", "SysOS"});
    List<List<Object>> cloudSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, "cloud_prop"}, new Object[]{"dev1", "CloudVal"});
    List<List<Object>> pointsetSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, BambiSiteModel.POINTS_TEMPLATE_NAME, "ps_prop"},
        new Object[]{"dev1", "tpl", "PsVal"});
    List<List<Object>> pointsSheet = headerOnlySheet(BambiSiteModel.POINTS_TEMPLATE_NAME,
        BambiSiteModel.POINT_NAME);

    BambiSiteModel model = new BambiSiteModel(emptySheet(), emptySheet(), systemSheet, cloudSheet,
        headerOnlySheet(BambiSiteModel.DEVICE_ID), // empty gateway
        headerOnlySheet(BambiSiteModel.DEVICE_ID), // empty localnet
        pointsetSheet, pointsSheet);

    Map<String, String> dev1Meta = model.getDeviceMetadata("dev1");
    assertNotNull(dev1Meta);
    assertEquals("SysOS", dev1Meta.get(PREFIX_SYSTEM + ".os"));
    assertEquals("CloudVal", dev1Meta.get(PREFIX_CLOUD + ".cloud_prop"));
    assertEquals("PsVal", dev1Meta.get(PREFIX_POINTSET + ".ps_prop"));
    assertEquals(1, model.getAllDevicesMetadata().size());
  }

  @Test
  public void populateInDeviceMap_skipsRowsWithoutDeviceIdValueOrColumn() {
    List<List<Object>> systemSheet = createTableListFromArrays(
        new Object[]{BambiSiteModel.DEVICE_ID, "data"},
        new Object[]{"dev1", "val1"},
        new Object[]{"", "val_for_empty_id"},
        new Object[]{"null_string", "val_for_null_string_id"}
    );
    List<List<Object>> cloudSheetNoDeviceIdCol = createTableListFromArrays(
        new Object[]{"other_col", "data"},
        new Object[]{"some_header_val", "val2"}
    );

    BambiSiteModel model = new BambiSiteModel(emptySheet(), emptySheet(), systemSheet,
        cloudSheetNoDeviceIdCol,
        headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"));

    Map<String, Map<String, String>> allMeta = model.getAllDevicesMetadata();
    assertEquals(3, allMeta.size());
    assertNotNull(model.getDeviceMetadata("dev1"));
    assertEquals("val_for_empty_id", model.getDeviceMetadata("").get(PREFIX_SYSTEM + ".data"));
    assertEquals("val_for_null_string_id",
        model.getDeviceMetadata("null_string").get(PREFIX_SYSTEM + ".data"));

    // Verify that no data from cloudSheetNoDeviceIdCol (which lacks DEVICE_ID) made it into allMeta
    for (Map<String, String> deviceData : allMeta.values()) {
      for (String key : deviceData.keySet()) {
        assertFalse("Cloud data from sheet without device_id column should not be present.",
            key.startsWith(PREFIX_CLOUD + "."));
      }
    }
  }

  @Test
  public void getDeviceMetadata_returnsNullForNonExistentDevice() {
    BambiSiteModel model = new BambiSiteModel(emptySheet(), emptySheet(), headerOnlySheet("h"),
        headerOnlySheet("h"),
        headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"), headerOnlySheet("h"));
    assertNull(model.getDeviceMetadata("non-existent-id"));
  }
}