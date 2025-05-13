package com.google.bos.iot.core.bambi;


import com.google.common.annotations.VisibleForTesting;
import com.google.udmi.util.SpreadsheetManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manage the site model stored in a BAMBI spreadsheet.
 */
public class BambiSiteModelManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(BambiSiteModelManager.class);
  private final SpreadsheetManager spreadsheetManager;
  private final BambiSiteModel bambiSiteModel;

  /**
   * Site Model Manager for a BAMBI spreadsheet.
   *
   * @param spreadsheetId alphanumeric id of a spreadsheet obtained from its URL
   */
  public BambiSiteModelManager(String spreadsheetId) {
    try {
      spreadsheetManager = new SpreadsheetManager("BAMBI", spreadsheetId);
      bambiSiteModel = new BambiSiteModel(
          getRecordsFromSheet(BambiSheet.SITE_METADATA),
          getRecordsFromSheet(BambiSheet.CLOUD_IOT_CONFIG),
          getRecordsFromSheet(BambiSheet.SYSTEM),
          getRecordsFromSheet(BambiSheet.CLOUD),
          getRecordsFromSheet(BambiSheet.GATEWAY),
          getRecordsFromSheet(BambiSheet.LOCALNET),
          getRecordsFromSheet(BambiSheet.POINTSET),
          getRecordsFromSheet(BambiSheet.POINTS)
      );
    } catch (IOException e) {
      throw new RuntimeException("while initializing BambiSiteModelManager: ", e);
    }
  }

  /**
   * Constructor for testing only - does not use gcloud credentials
   */
  @VisibleForTesting
  public BambiSiteModelManager(SpreadsheetManager mockSpreadsheetManager) {
    try {
      spreadsheetManager = mockSpreadsheetManager;
      bambiSiteModel = new BambiSiteModel(
          getRecordsFromSheet(BambiSheet.SITE_METADATA),
          getRecordsFromSheet(BambiSheet.CLOUD_IOT_CONFIG),
          getRecordsFromSheet(BambiSheet.SYSTEM),
          getRecordsFromSheet(BambiSheet.CLOUD),
          getRecordsFromSheet(BambiSheet.GATEWAY),
          getRecordsFromSheet(BambiSheet.LOCALNET),
          getRecordsFromSheet(BambiSheet.POINTSET),
          getRecordsFromSheet(BambiSheet.POINTS)
      );
    } catch (IOException e) {
      throw new RuntimeException("while initializing BambiSiteModelManager: ", e);
    }
  }

  public BambiSiteModel getBambiSiteModel() {
    return bambiSiteModel;
  }

  public Map<String, String> getSiteMetadata() {
    return bambiSiteModel.getSiteMetadata();
  }

  public Map<String, String> getCloudIotConfig() {
    return bambiSiteModel.getCloudIotConfig();
  }

  public Map<String, Map<String, String>> getAllDevicesMetadata() {
    return bambiSiteModel.getAllDevicesMetadata();
  }

  /**
   * Write relevant data to the site_metadata tab in the spreadsheet.
   *
   * @param newSiteMetadata Map of data to write
   */
  public void writeSiteMetadata(Map<String, String> newSiteMetadata) {
    writeKeyValueTypeMetadata(BambiSheet.SITE_METADATA, bambiSiteModel.getSiteMetadataHeaders(),
        newSiteMetadata);
  }

  /**
   * Write relevant data to the cloud_iot_config tab in the spreadsheet.
   *
   * @param newCloudIotConfig Map of data to write
   */
  public void writeCloudIotConfig(Map<String, String> newCloudIotConfig) {
    writeKeyValueTypeMetadata(BambiSheet.CLOUD_IOT_CONFIG,
        bambiSiteModel.getCloudIotConfigHeaders(), newCloudIotConfig);
  }

  private void writeKeyValueTypeMetadata(BambiSheet sheet, List<String> headers,
      Map<String, String> data) {
    LOGGER.info("writing to sheet " + sheet.getName());
    Map<String, String> newData = new LinkedHashMap<>();
    for (String header : headers) {
      newData.put(header, data.getOrDefault(header, ""));
    }
    try {
      spreadsheetManager.clearValuesFromRange(sheet.getName());
      spreadsheetManager.writeToRange(sheet.getName(), asSheetData(newData));
    } catch (IOException e) {
      throw new RuntimeException("while writing to bambi sheet " + sheet.getName(), e);
    }
  }

  /**
   * Write relevant data to system, cloud, gateway, localnet, pointset, and points tabs in the
   * spreadsheet.
   *
   * @param deviceToMetadataMap Mapping of device id to the device metadata
   */
  public void writeDevicesMetadata(Map<String, Map<String, String>> deviceToMetadataMap) {
    Map<BambiSheet, List<List<Object>>> dataToWrite = new HashMap<>();

    List<SheetConfig> simpleTableConfigs = List.of(
        new SheetConfig(BambiSheet.SYSTEM, bambiSiteModel.getSystemDataHeaders()),
        new SheetConfig(BambiSheet.CLOUD, bambiSiteModel.getCloudDataHeaders()),
        new SheetConfig(BambiSheet.GATEWAY, bambiSiteModel.getGatewayDataHeaders()),
        new SheetConfig(BambiSheet.LOCALNET, bambiSiteModel.getLocalnetDataHeaders())
    );

    for (SheetConfig config : simpleTableConfigs) {
      List<List<Object>> sheetData = processSimpleTableData(
          deviceToMetadataMap, config.headers(), config.sheet().getName() + "."
      );
      dataToWrite.put(config.sheet(), sheetData);
    }

    PointsetAndPointsData data = computePointsAndPointsetsAsTableData(
        deviceToMetadataMap,
        bambiSiteModel.getPointsetDataHeaders(),
        bambiSiteModel.getPointsDataHeaders()
    );
    dataToWrite.put(BambiSheet.POINTSET, data.pointsetData());
    dataToWrite.put(BambiSheet.POINTS, data.pointsData());

    try {
      for (Map.Entry<BambiSheet, List<List<Object>>> entry : dataToWrite.entrySet()) {
        LOGGER.info("writing to sheet " + entry.getKey().getName());
        spreadsheetManager.clearValuesFromRange(entry.getKey().getName());
        spreadsheetManager.writeToRange(entry.getKey().getName(), entry.getValue());
      }
    } catch (IOException e) {
      throw new RuntimeException("failed to write device metadata to spreadsheet", e);
    }
  }

  private List<List<Object>> processSimpleTableData(
      Map<String, Map<String, String>> deviceToMetadataMap,
      List<String> headers,
      String keyPrefix) {

    List<List<Object>> sheetData = new ArrayList<>();
    sheetData.add(new ArrayList<>(headers));

    for (Map.Entry<String, Map<String, String>> deviceEntry : deviceToMetadataMap.entrySet()) {
      String deviceId = deviceEntry.getKey();
      Map<String, String> allMetadata = deviceEntry.getValue();

      // Filter metadata and remove prefix from keys
      Map<String, String> relevantMetadata = allMetadata.entrySet().stream()
          .filter(e -> e.getKey().startsWith(keyPrefix))
          .collect(Collectors.toMap(
              e -> e.getKey().substring(keyPrefix.length()),
              Entry::getValue
          ));

      sheetData.add(buildDataRow(deviceId, headers, relevantMetadata));
    }
    return sheetData;
  }

  private List<Object> buildDataRow(String deviceId, List<String> headers,
      Map<String, String> metadata) {
    List<Object> row = new ArrayList<>(headers.size());
    String pointsTemplateNameValue = deviceId + "_template";

    for (String header : headers) {
      Object value = switch (header) {
        case BambiSiteModel.DEVICE_ID -> deviceId;
        case BambiSiteModel.POINTS_TEMPLATE_NAME -> pointsTemplateNameValue;
        default -> {
          String rawValue = metadata.getOrDefault(header, "");
          if (rawValue.length() >= 2 && rawValue.startsWith("[") && rawValue.endsWith("]")) {
            yield rawValue.substring(1, rawValue.length() - 1);
          } else {
            yield rawValue;
          }
        }
      };
      row.add(value);
    }
    return row;
  }

  /**
   * Processes metadata specifically for Pointset and Points sheets. One device results in one
   * pointset row and multiple point rows.
   */
  private PointsetAndPointsData computePointsAndPointsetsAsTableData(
      Map<String, Map<String, String>> deviceToMetadataMap,
      List<String> pointsetDataHeaders,
      List<String> pointsDataHeaders) {
    List<List<Object>> pointsetOutput = new ArrayList<>();
    List<List<Object>> pointsOutput = new ArrayList<>();

    final String pointsetPrefix = BambiSheet.POINTSET.getName() + ".";
    final String pointsPrefix = pointsetPrefix + BambiSheet.POINTS.getName() + ".";

    // Add header rows
    pointsOutput.add(new ArrayList<>(pointsDataHeaders));
    pointsetOutput.add(new ArrayList<>(pointsetDataHeaders));

    for (Map.Entry<String, Map<String, String>> deviceEntry : deviceToMetadataMap.entrySet()) {
      String deviceId = deviceEntry.getKey();
      Map<String, String> allMetadata = deviceEntry.getValue();

      Map<String, String> pointsetMetadata = new HashMap<>();
      Map<String, String> rawPointsSubkeys = new HashMap<>();

      for (Map.Entry<String, String> metaEntry : allMetadata.entrySet()) {
        String key = metaEntry.getKey();
        if (key.startsWith(pointsPrefix)) {
          rawPointsSubkeys.put(key.substring(pointsPrefix.length()), metaEntry.getValue());
        } else if (key.startsWith(pointsetPrefix)) {
          pointsetMetadata.put(key.substring(pointsetPrefix.length()), metaEntry.getValue());
        }
      }

      // --- Process Points Data ---
      Map<String, Map<String, String>> pointsForDevice = new HashMap<>();
      for (Map.Entry<String, String> rawEntry : rawPointsSubkeys.entrySet()) {
        String combinedKey = rawEntry.getKey(); // "pointName.property"
        String value = rawEntry.getValue();

        String[] parts = combinedKey.split("\\.", 2);
        if (parts.length == 2) {
          String pointName = parts[0];
          String propertyName = parts[1];

          Map<String, String> pointMap = pointsForDevice.computeIfAbsent(pointName,
              k -> new HashMap<>());

          pointMap.put(BambiSiteModel.POINT_NAME, pointName);
          pointMap.put(propertyName, value);
        } else {
          LOGGER.error(
              "Warning: Skipping malformed point key: " + combinedKey + " for device " + deviceId);
        }
      }

      // --- Multiple points for a device ---
      for (Map<String, String> singlePointData : pointsForDevice.values()) {
        pointsOutput.add(buildDataRow(deviceId, pointsDataHeaders, singlePointData));
      }

      // --- Process Pointset Data and add a single row ---
      pointsetOutput.add(buildDataRow(deviceId, pointsetDataHeaders, pointsetMetadata));
    }
    return new PointsetAndPointsData(pointsetOutput, pointsOutput);
  }

  private List<List<Object>> getRecordsFromSheet(BambiSheet bambiSheet) throws IOException {
    return spreadsheetManager.getSheetRecords(bambiSheet.getName());
  }

  private List<List<Object>> asSheetData(Map<String, String> map) {
    List<List<Object>> sheetData = new ArrayList<>();
    for (Entry<String, String> entry : map.entrySet()) {
      sheetData.add(List.of(entry.getKey(), entry.getValue()));
    }
    return sheetData;
  }

  enum BambiSheet {
    SITE_METADATA("site_metadata"),
    CLOUD_IOT_CONFIG("cloud_iot_config"),
    SYSTEM("system"),
    CLOUD("cloud"),
    GATEWAY("gateway"),
    LOCALNET("localnet"),
    POINTSET("pointset"),
    POINTS("points");

    private final String name;

    BambiSheet(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  // Helper structure for simple table configurations
  private record SheetConfig(BambiSheet sheet, List<String> headers) { }

  // Helper structure for point and pointset data to be populated in BAMBI
  private record PointsetAndPointsData(List<List<Object>> pointsetData,
                                       List<List<Object>> pointsData) { }
}
