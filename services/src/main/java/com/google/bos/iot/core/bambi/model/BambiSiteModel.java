package com.google.bos.iot.core.bambi.model;

import static com.google.udmi.util.GeneralUtils.isNotEmpty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * Representation for site model fetched from BAMBI sheets UI.
 */
public class BambiSiteModel {

  public static final String DEVICE_ID = "device_id";
  public static final String POINTS_TEMPLATE_NAME = "points_template_name";
  public static final String POINT_NAME = "point_name";
  private final Map<String, String> siteMetadata;
  private final Map<String, String> cloudIotConfig;
  private final List<String> systemHeaders;
  private final List<Map<String, String>> systemData;
  private final List<String> cloudHeaders;
  private final List<Map<String, String>> cloudData;
  private final List<String> gatewayHeaders;
  private final List<Map<String, String>> gatewayData;
  private final List<String> localnetHeaders;
  private final List<Map<String, String>> localnetData;
  private final List<String> pointsetHeaders;
  private final List<Map<String, String>> pointsetData;
  private final List<String> pointsHeaders;
  private final List<Map<String, String>> pointsData;
  private final Map<String, Map<String, String>> allDevicesMetadata;

  /**
   * Initialize Bambi Site Model with the data fetched from sheets.
   */
  public BambiSiteModel(
      List<List<Object>> siteMetadataFromSheet,
      List<List<Object>> cloudIotConfigFromSheet,
      List<List<Object>> systemDataFromSheet,
      List<List<Object>> cloudDataFromSheet,
      List<List<Object>> gatewayDataFromSheet,
      List<List<Object>> localnetDataFromSheet,
      List<List<Object>> pointsetDataFromSheet,
      List<List<Object>> pointsDataFromSheet
  ) {
    siteMetadata = getKeyValueMapFromSheet(siteMetadataFromSheet);
    cloudIotConfig = getKeyValueMapFromSheet(cloudIotConfigFromSheet);

    systemHeaders = getColumnHeadersFromSheetData(systemDataFromSheet);
    systemData = getRowsFromSheet(systemDataFromSheet);

    cloudHeaders = getColumnHeadersFromSheetData(cloudDataFromSheet);
    cloudData = getRowsFromSheet(cloudDataFromSheet);

    gatewayHeaders = getColumnHeadersFromSheetData(gatewayDataFromSheet);
    gatewayData = getRowsFromSheet(gatewayDataFromSheet);

    localnetHeaders = getColumnHeadersFromSheetData(localnetDataFromSheet);
    localnetData = getRowsFromSheet(localnetDataFromSheet);

    pointsetHeaders = getColumnHeadersFromSheetData(pointsetDataFromSheet);
    pointsetData = getRowsFromSheet(pointsetDataFromSheet);

    pointsHeaders = getColumnHeadersFromSheetData(pointsDataFromSheet);
    pointsData = getRowsFromSheet(pointsDataFromSheet);

    allDevicesMetadata = computeDevicesMetadata();
  }

  public Map<String, String> getSiteMetadata() {
    return siteMetadata;
  }

  public List<String> getSiteMetadataHeaders() {
    return getRowHeaders(siteMetadata);
  }

  public Map<String, String> getCloudIotConfig() {
    return cloudIotConfig;
  }

  public List<String> getCloudIotConfigHeaders() {
    return getRowHeaders(cloudIotConfig);
  }

  public List<Map<String, String>> getSystemData() {
    return systemData;
  }

  public List<String> getSystemDataHeaders() {
    return systemHeaders;
  }

  public List<Map<String, String>> getCloudData() {
    return cloudData;
  }

  public List<String> getCloudDataHeaders() {
    return cloudHeaders;
  }

  public List<Map<String, String>> getGatewayData() {
    return gatewayData;
  }

  public List<String> getGatewayDataHeaders() {
    return gatewayHeaders;
  }

  public List<Map<String, String>> getLocalnetData() {
    return localnetData;
  }

  public List<String> getLocalnetDataHeaders() {
    return localnetHeaders;
  }

  public List<Map<String, String>> getPointsetData() {
    return pointsetData;
  }

  public List<String> getPointsetDataHeaders() {
    return pointsetHeaders;
  }

  public List<Map<String, String>> getPointsData() {
    return pointsData;
  }

  public List<String> getPointsDataHeaders() {
    return pointsHeaders;
  }

  public Map<String, Map<String, String>> getAllDevicesMetadata() {
    return allDevicesMetadata;
  }

  public Map<String, String> getDeviceMetadata(String deviceId) {
    return allDevicesMetadata.getOrDefault(deviceId, null);
  }

  private Map<String, Map<String, String>> computeDevicesMetadata() {
    Map<String, Map<String, String>> devicesMetadata = new LinkedHashMap<>();
    populateInDeviceMap(systemData, devicesMetadata, BambiSheetTab.SYSTEM.getName());
    populateInDeviceMap(cloudData, devicesMetadata, BambiSheetTab.CLOUD.getName());
    populateInDeviceMap(gatewayData, devicesMetadata, BambiSheetTab.GATEWAY.getName());
    populateInDeviceMap(localnetData, devicesMetadata, BambiSheetTab.LOCALNET.getName());
    populateInDeviceMap(mergePointsWithPointset(), devicesMetadata,
        BambiSheetTab.POINTSET.getName());
    return devicesMetadata;
  }

  private void populateInDeviceMap(List<Map<String, String>> allDeviceData,
      Map<String, Map<String, String>> deviceMap, String keyPrefix) {
    if (allDeviceData != null) {
      for (Map<String, String> deviceData : allDeviceData) {
        String deviceId = deviceData.getOrDefault(DEVICE_ID, null);
        if (deviceId != null) {
          deviceMap.putIfAbsent(deviceId, new LinkedHashMap<>());
          for (Entry<String, String> cell : deviceData.entrySet()) {
            deviceMap.get(deviceId)
                .put((keyPrefix == null ? "" : keyPrefix + ".") + cell.getKey(), cell.getValue());
          }
        }
      }
    }
  }

  private List<Map<String, String>> mergePointsWithPointset() {
    Map<String, List<Map<String, String>>> pointsTemplates = new LinkedHashMap<>();
    for (Map<String, String> row : pointsData) {
      String templateName = row.get(POINTS_TEMPLATE_NAME);
      if (isNotEmpty(templateName)) {
        pointsTemplates.putIfAbsent(templateName, new ArrayList<>());
        pointsTemplates.get(templateName).add(row);
      } else {
        throw new RuntimeException("Template name must not be empty!");
      }
    }

    List<Map<String, String>> mergedData = new ArrayList<>();
    for (Map<String, String> pointsetDatum : pointsetData) {
      Map<String, String> pointsetRow = new LinkedHashMap<>(pointsetDatum);
      String templateName = pointsetRow.get(POINTS_TEMPLATE_NAME);

      if (isNotEmpty(templateName)) {
        for (Map<String, String> pointRow : pointsTemplates.getOrDefault(templateName,
            new ArrayList<>())) {
          String pointName = pointRow.get(POINT_NAME);
          for (Entry<String, String> cell : pointRow.entrySet()) {
            String header = cell.getKey();
            String val = cell.getValue();
            pointsetRow.put(BambiSheetTab.POINTS.getName() + "." + pointName + "." + header, val);
          }
        }
      }
      mergedData.add(pointsetRow);
    }

    return mergedData;
  }

  private Map<String, String> getKeyValueMapFromSheet(List<List<Object>> sheetData) {
    Map<String, String> record = new LinkedHashMap<>();
    for (List<Object> row : sheetData) {
      if (row == null || row.isEmpty()) {
        continue;
      }
      String key = Objects.toString(row.get(0), "");
      String value = row.size() >= 2 ? Objects.toString(row.get(1), "") : "";
      record.put(key, value);
    }
    return record;
  }

  private List<Map<String, String>> getRowsFromSheet(List<List<Object>> sheetData) {
    if (sheetData.isEmpty()) {
      return null;
    }
    List<Map<String, String>> rows = new ArrayList<>();
    List<String> headers = getColumnHeadersFromSheetData(sheetData);

    for (List<Object> data : sheetData.subList(1, sheetData.size())) {
      Map<String, String> row = new LinkedHashMap<>();
      for (int i = 0; i < Objects.requireNonNull(headers).size(); i++) {
        row.put(headers.get(i), i < data.size() ? Objects.toString(data.get(i)) : "");
      }
      rows.add(row);
    }
    return rows;
  }

  private List<String> getColumnHeadersFromSheetData(List<List<Object>> sheetData) {
    if (sheetData.isEmpty()) {
      return null;
    }
    List<Object> headerObjects = sheetData.get(0);
    if (headerObjects == null || headerObjects.isEmpty()) {
      return null;
    }
    return headerObjects.stream()
        .map(h -> Objects.toString(h, ""))
        .toList();
  }

  private List<String> getRowHeaders(Map<String, String> data) {
    return data.keySet().stream().toList();
  }

}
