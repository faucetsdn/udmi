package com.google.bos.iot.core.bambi;

import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sync site model from disk to the BAMBI spreadsheet.
 */
public class BambiSync {

  private static final Logger LOGGER = LoggerFactory.getLogger(BambiSync.class);
  private final BambiSiteModelManager bambiSiteModelManager;
  private final LocalSiteModelManager localSiteModelManager;

  /**
   * Sync site model from disk to the BAMBI spreadsheet.
   *
   * @param spreadsheetId alphanumeric id of a spreadsheet obtained from its URL
   * @param pathToSiteModel absolute path to the latest site model
   */
  public BambiSync(String spreadsheetId, String pathToSiteModel) {
    LOGGER.info("requested BambiSync from pathToSiteModel {} to spreadsheetId {}",
        pathToSiteModel, spreadsheetId);
    this.bambiSiteModelManager = new BambiSiteModelManager(spreadsheetId);
    this.localSiteModelManager = new LocalSiteModelManager(pathToSiteModel);
  }

  /**
   * Execute the sync operation.
   * Existing data from the spreadsheet will be overwritten with the site model from disk.
   */
  public void execute() {
    bambiSiteModelManager.writeSiteMetadata(localSiteModelManager.getSiteMetadata());
    bambiSiteModelManager.writeCloudIotConfig(localSiteModelManager.getCloudIotConfig());
    bambiSiteModelManager.writeDevicesMetadata(localSiteModelManager.getAllDeviceMetadata());
  }

  /**
   * Utility to sync a site model from disk to a BAMBI spreadsheet (where the
   * json templates have already been populated).
   *
   * @param args command line args
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.println(
          "Usage: BambiSync <spreadsheetId> <pathToSiteModel>");
      System.exit(1);
    }
    long start = Instant.now().getEpochSecond();
    BambiSync bambiSync = new BambiSync(args[0], args[1]);
    bambiSync.execute();
    LOGGER.info("Sync completed in {} seconds", Instant.now().getEpochSecond() - start);
  }
}
