package com.google.bos.iot.core.bambi;

import static com.google.bos.iot.core.bambi.model.BambiSiteModel.DEVICE_ID;
import static com.google.bos.iot.core.bambi.model.BambiSiteModel.POINTS_TEMPLATE_NAME;
import static com.google.bos.iot.core.bambi.model.BambiSiteModel.POINT_NAME;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sync site model from a BAMBI spreadsheet to disk.
 */
public class LocalDiskSync {

  private static final Logger LOGGER = LoggerFactory.getLogger(LocalDiskSync.class);
  private final BambiSiteModelManager bambiSiteModelManager;
  private final LocalSiteModelManager localSiteModelManager;

  /**
   * Sync site model from a BAMBI spreadsheet to disk.
   *
   * @param spreadsheetId alphanumeric id of a spreadsheet obtained from its URL
   * @param pathToSiteModel absolute path to the latest site model
   */
  public LocalDiskSync(String spreadsheetId, String pathToSiteModel) {
    LOGGER.info("requested LocalDiskSync from spreadsheetId {} to pathToSiteModel {}",
        spreadsheetId, pathToSiteModel);
    bambiSiteModelManager = new BambiSiteModelManager(spreadsheetId);
    localSiteModelManager = new LocalSiteModelManager(pathToSiteModel);
  }

  /**
   * Execute the sync operation.
   * Data from the spreadsheet will be merged with the site model on disk.
   * If relevant files do not exist, they will be created.
   */
  public void execute() {
    localSiteModelManager.mergeSiteMetadataOnDisk(bambiSiteModelManager.getSiteMetadata());
    localSiteModelManager.mergeCloudIotConfigOnDisk(bambiSiteModelManager.getCloudIotConfig());

    Map<String, Map<String, String>> devicesData = bambiSiteModelManager.getAllDevicesMetadata();
    devicesData.replaceAll((k, v) -> removeExtraKeys(v));
    localSiteModelManager.mergeAllDevicesMetadataOnDisk(devicesData);
  }

  private Map<String, String> removeExtraKeys(Map<String, String> dataFromBambi) {
    Map<String, String> data = new LinkedHashMap<>();
    for (Entry<String, String> entry : dataFromBambi.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (!value.isEmpty() && !key.endsWith(DEVICE_ID) && !key.endsWith(POINT_NAME)
          && !key.endsWith(POINTS_TEMPLATE_NAME)) {
        data.put(key, dataFromBambi.get(key));
      }
    }
    return data;
  }

  /**
   * Utility to merge a site model from a BAMBI spreadsheet with a site model on disk.
   *
   * @param args command line args
   */
  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.println(
          "Usage: LocalDiskSync <spreadsheetId> <pathToSiteModel>");
      System.exit(1);
    }
    long start = Instant.now().getEpochSecond();
    LocalDiskSync localDiskSync = new LocalDiskSync(args[0], args[1]);
    localDiskSync.execute();
    LOGGER.info("Sync completed in {} seconds", Instant.now().getEpochSecond() - start);
  }
}
