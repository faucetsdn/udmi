package com.google.bos.iot.core.bambi;

import static com.google.bos.iot.core.bambi.Utils.DELETE_MARKER;
import static com.google.bos.iot.core.bambi.Utils.EMPTY_MARKER;
import static com.google.bos.iot.core.bambi.Utils.NON_NUMERIC_HEADERS_REGEX;
import static com.google.bos.iot.core.bambi.Utils.POINTS_KEY_REGEX;
import static com.google.bos.iot.core.bambi.Utils.handleArraysInMap;
import static com.google.bos.iot.core.bambi.Utils.handleExplicitlyEmptyValues;
import static com.google.bos.iot.core.bambi.Utils.removeBracketsFromListValues;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.JsonUtil.asLinkedHashMap;
import static com.google.udmi.util.JsonUtil.flattenNestedMap;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.nestFlattenedJson;
import static com.google.udmi.util.JsonUtil.writeFormattedFile;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manage a site model stored on disk.
 */
public class LocalSiteModelManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocalSiteModelManager.class);
  private static final String DEVICES_FOLDER = "devices";
  private static final String SITE_METADATA_FILE = "site_metadata.json";
  private static final String CLOUD_IOT_CONFIG_FILE = "cloud_iot_config.json";
  private static final String DEVICE_METADATA_FILE = "metadata.json";
  private final String pathToSiteModel;

  /**
   * Site Model Manager for a site model stored on disk.
   *
   * @param pathToSiteModel absolute path to the site model
   */
  public LocalSiteModelManager(String pathToSiteModel) {
    File file = new File(pathToSiteModel);
    if (!file.exists()) {
      throw new IllegalArgumentException("site model directory does not exist " + pathToSiteModel);
    }
    this.pathToSiteModel = pathToSiteModel;
  }

  private Map<String, String> getKeyValueDataFromDisk(String... path) {
    Path filePath = Paths.get(pathToSiteModel, path);
    LOGGER.info("fetching data from file " + filePath.toUri());

    Map<String, Object> siteModelMap = catchToElse(
        () -> asLinkedHashMap(new File(filePath.toUri())), new LinkedHashMap<>());
    return catchToElse(() -> flattenNestedMap(siteModelMap, ".").entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> String.valueOf(entry.getValue()),
            (existing, replacement) -> replacement,
            LinkedHashMap::new
        )), new LinkedHashMap<>());
  }

  private void writeJsonToDisk(Map<String, String> flattenedData, String... paths) {
    URI filePath = Paths.get(pathToSiteModel, paths).toUri();
    LOGGER.info("writing data to file {}", filePath);
    JsonNode jsonNode = nestFlattenedJson(flattenedData, "\\.", NON_NUMERIC_HEADERS_REGEX);
    File file = new File(filePath);
    file.getParentFile().mkdirs();
    writeFormattedFile(jsonNode, new File(filePath));
  }

  public Map<String, String> getSiteMetadata() {
    return getKeyValueDataFromDisk(SITE_METADATA_FILE);
  }

  public Map<String, String> getCloudIotConfig() {
    return getKeyValueDataFromDisk(CLOUD_IOT_CONFIG_FILE);
  }

  public Map<String, String> getDeviceMetadata(String deviceId) {
    return getKeyValueDataFromDisk(DEVICES_FOLDER, deviceId, DEVICE_METADATA_FILE);
  }

  /**
   * Get metadata for all devices from the site model on disk.
   *
   * @return Mapping of device ids to the device metadata
   */
  public Map<String, Map<String, String>> getAllDeviceMetadata() {
    File[] deviceDirectories = new File(
        Paths.get(pathToSiteModel, DEVICES_FOLDER).toUri()).listFiles(File::isDirectory);
    Map<String, Map<String, String>> metadata = new HashMap<>();
    if (deviceDirectories != null) {
      for (File dir : deviceDirectories) {
        Map<String, String> deviceMetadata = getDeviceMetadata(dir.getName());
        deviceMetadata.put("device_id", dir.getName());
        metadata.put(dir.getName(), deviceMetadata);
      }
    }
    return metadata;
  }

  public void writeSiteMeta(Map<String, String> flattenedSiteMetadata) {
    writeJsonToDisk(flattenedSiteMetadata, SITE_METADATA_FILE);
  }

  public void writeCloudIotConfig(Map<String, String> flattenedCloudIotConfig) {
    writeJsonToDisk(flattenedCloudIotConfig, CLOUD_IOT_CONFIG_FILE);
  }

  public void writeDeviceMetadata(Map<String, String> flattenedDeviceMetadata, String deviceId) {
    writeJsonToDisk(flattenedDeviceMetadata, DEVICES_FOLDER, deviceId, DEVICE_METADATA_FILE);
  }

  /**
   * Overwrite the device metadata json files on disk with the input device metadata.
   *
   * @param allDeviceMetadataMap Mapping of device ids to the device metadata
   */
  public void writeAllDevicesMetadata(Map<String, Map<String, String>> allDeviceMetadataMap) {
    for (Entry<String, Map<String, String>> entry : allDeviceMetadataMap.entrySet()) {
      writeDeviceMetadata(entry.getValue(), entry.getKey());
    }
  }

  /**
   * Merge input site metadata with the site_metadata.json file available on disk.
   * If relevant file does not exist, a new one is created.
   *
   * @param newSiteMetadata Map of data to merge
   */
  public void mergeSiteMetadataOnDisk(Map<String, String> newSiteMetadata) {
    Map<String, String> siteMetadataOnDisk = getSiteMetadata();
    writeSiteMeta(merge(siteMetadataOnDisk, newSiteMetadata, false));
  }

  /**
   * Merge input cloud IoT config with the cloud_iot_config.json file available on disk.
   * If relevant file does not exist, a new one is created.
   *
   * @param newCloudIotConfig Map of data to merge
   */
  public void mergeCloudIotConfigOnDisk(Map<String, String> newCloudIotConfig) {
    Map<String, String> cloudIotConfigOnDisk = getCloudIotConfig();
    writeCloudIotConfig(merge(cloudIotConfigOnDisk, newCloudIotConfig, false));
  }

  /**
   * Merge input device metadata in the device metadata json file available on disk.
   * If relevant file does not exist, a new one is created.
   *
   * @param newDevicesMetadata Mapping of device ids to the device metadata
   */
  public void mergeAllDevicesMetadataOnDisk(Map<String, Map<String, String>> newDevicesMetadata) {
    for (Entry<String, Map<String, String>> entry : newDevicesMetadata.entrySet()) {
      String deviceId = entry.getKey();
      Map<String, String> deviceMetadataOnDisk = getDeviceMetadata(deviceId);
      writeDeviceMetadata(mergeDeviceMetadata(deviceMetadataOnDisk, entry.getValue()), deviceId);
    }
  }

  private Map<String, String> merge(Map<String, String> metadataOnDisk,
      Map<String, String> newMetadata, boolean shouldUpdateTimestamp) {
    metadataOnDisk = removeBracketsFromListValues(metadataOnDisk);
    handleExplicitlyEmptyValues(newMetadata);

    for (Entry<String, String> entry : newMetadata.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (Objects.equals(value, DELETE_MARKER)) {
        metadataOnDisk.remove(key);
        updateTimestamp(metadataOnDisk, shouldUpdateTimestamp);
      } else if (Objects.equals(value, EMPTY_MARKER)) {
        String valueOnDisk = metadataOnDisk.get(key);
        if (valueOnDisk == null || !valueOnDisk.isEmpty()) {
          metadataOnDisk.put(key, "");
          updateTimestamp(metadataOnDisk, shouldUpdateTimestamp);
        }
      } else if (!value.isEmpty() && !value.equals(metadataOnDisk.getOrDefault(key, ""))) {
        metadataOnDisk.put(key, value);
        updateTimestamp(metadataOnDisk, shouldUpdateTimestamp);
      }
    }
    return handleArraysInMap(metadataOnDisk);
  }

  private void updateTimestamp(Map<String, String> metadataMap, boolean shouldUpdateTimestamp) {
    if (shouldUpdateTimestamp) {
      metadataMap.put("timestamp", isoConvert(Instant.now()));
    }
  }

  /**
   * Merge device metadata with the received update.
   *
   * @param originalData Device metadata on disk.
   * @param receivedUpdate Received Device metadata update.
   */
  private Map<String, String> mergeDeviceMetadata(Map<String, String> originalData,
      Map<String, String> receivedUpdate) {
    // Merge all data including any renamed points
    Map<String, String> mergedData = merge(originalData, receivedUpdate, true);

    // Remove any outdated points (removed or renamed to something new)
    List<String> keysToRemove = new ArrayList<>();
    for (String key : mergedData.keySet()) {
      if (key.matches(POINTS_KEY_REGEX.pattern()) && !receivedUpdate.containsKey(key)) {
        keysToRemove.add(key);
      }
    }
    for (String key : keysToRemove) {
      mergedData.remove(key);
    }

    return mergedData;
  }
}
