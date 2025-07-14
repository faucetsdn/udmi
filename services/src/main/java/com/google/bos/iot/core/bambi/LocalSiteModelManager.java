package com.google.bos.iot.core.bambi;

import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.JsonUtil.asLinkedHashMap;
import static com.google.udmi.util.JsonUtil.flattenNestedMap;
import static com.google.udmi.util.JsonUtil.nestFlattenedJson;
import static com.google.udmi.util.JsonUtil.writeFile;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    Map<String, Object> siteModelMap = asLinkedHashMap(new File(filePath.toUri()));
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
    JsonNode jsonNode = nestFlattenedJson(flattenedData, "\\.");
    File file = new File(filePath);
    file.getParentFile().mkdirs();
    writeFile(jsonNode, new File(filePath));
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
    merge(siteMetadataOnDisk, newSiteMetadata);
    writeSiteMeta(siteMetadataOnDisk);
  }

  /**
   * Merge input cloud IoT config with the cloud_iot_config.json file available on disk.
   * If relevant file does not exist, a new one is created.
   *
   * @param newCloudIotConfig Map of data to merge
   */
  public void mergeCloudIotConfigOnDisk(Map<String, String> newCloudIotConfig) {
    Map<String, String> cloudIotConfigOnDisk = getCloudIotConfig();
    merge(cloudIotConfigOnDisk, newCloudIotConfig);
    writeCloudIotConfig(cloudIotConfigOnDisk);
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
      merge(deviceMetadataOnDisk, entry.getValue());
      writeDeviceMetadata(deviceMetadataOnDisk, deviceId);
    }
  }

  private void merge(Map<String, String> metadataOnDisk, Map<String, String> newMetadata) {
    for (Entry<String, String> entry : newMetadata.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (Objects.equals(value, "__DELETE__")) {
        metadataOnDisk.remove(key);
      } else if (!value.isEmpty() && !value.equals(metadataOnDisk.getOrDefault(key, ""))) {
        populateMap(key, value, metadataOnDisk);
      }
    }
  }

  /**
   * Populates the map, expanding comma-separated values for specific keys
   * while preserving the order of elements.
   *
   * @param key the key to add or update
   * @param newValue the new value for the key
   * @param map the map to populate (should be an instance of LinkedHashMap or
   *     another order-preserving map)
   */
  private void populateMap(String key, String newValue, Map<String, String> map) {
    if ("gateway.proxy_ids".equals(key) || "system.tags".equals(key) || "tags".equals(key)) {
      if (map.containsKey(key)) {
        Map<String, String> tempMap = new LinkedHashMap<>();
        String[] arrayValues = newValue.split(",");

        for (Map.Entry<String, String> entry : map.entrySet()) {
          if (entry.getKey().equals(key)) {
            for (int i = 0; i < arrayValues.length; i++) {
              tempMap.put(key + "." + i, arrayValues[i].trim());
            }
          } else {
            tempMap.put(entry.getKey(), entry.getValue());
          }
        }
        map.clear();
        map.putAll(tempMap);
      } else {
        String[] arrayValues = newValue.split(",");
        for (int i = 0; i < arrayValues.length; i++) {
          map.put(key + "." + i, arrayValues[i].trim());
        }
      }
    } else {
      map.put(key, newValue);
    }
  }
}
