function createBAMBIMenu() {
  var ui = SpreadsheetApp.getUi();
  const BAMBImenu = ui.createMenu('BAMBI');

  const configMenu = BAMBImenu.addSubMenu(
    ui.createMenu('Define Config')
    .addItem('Add BAMBI Config Sheet', 'overwriteBambiConfigSheet')
    .addItem('Add Operations Log Sheet', 'overwriteOperationLogsSheet')
  );
  BAMBImenu.addSeparator();
  const templatesSubMenu = BAMBImenu.addSubMenu(ui.createMenu('Add Templates')
    .addItem('Add Cloud IoT Configuration Template', 'addCloudIoTConfig')
    .addItem('Add Site Metadata Template', 'addSiteMetadata')
    .addItem('Add Device Metadata Templates', 'addDeviceMetadata')
  );

  const importSubMenu = BAMBImenu.addSubMenu(ui.createMenu('Import Site Model')
    .addItem('From Source Repository', 'requestImport')
    .addSubMenu(ui.createMenu('From Google Drive')
      .addItem('Import Cloud IoT Configuration', 'BOSPlatformLibraries.importCloudIoTConfig')
      .addItem('Import Site Metadata', 'BOSPlatformLibraries.importSiteMetadata')
      .addItem('Import Device IDs', 'BOSPlatformLibraries.importDeviceIDs')
      .addItem('Import Device Metadata', 'BOSPlatformLibraries.importDeviceMetadata')
    )
  );

  const exportSubMenu = BAMBImenu.addSubMenu(ui.createMenu('Export Site Model')
    .addItem('To Source Repository', 'requestMerge')
    .addSubMenu(ui.createMenu('To Google Drive')
      .addItem('Export Cloud IoT Configuration', 'BOSPlatformLibraries.exportCloudIoTConfig')
      .addItem('Export Site Metadata', 'BOSPlatformLibraries.exportSiteMetadata')
      .addItem('Export Device Metadata', 'BOSPlatformLibraries.exportDeviceMetadata')
    )
  );

  const utilitySubMenu = BAMBImenu.addSubMenu(ui.createMenu('Utility functions')
      .addItem('Format Header and Resize', 'BOSPlatformLibraries.formatHeaderAndResize')
      .addItem('Generate UUID', 'BOSPlatformLibraries.fillSelectedWithUUIDs')
      .addItem('Normalise Point Name Cells', 'BOSPlatformLibraries.normaliseSelectedCells')
      .addItem('Sort Sheet Alphabetically', 'BOSPlatformLibraries.sortSheet'));
  BAMBImenu.addSeparator();
  BAMBImenu.addItem('About', 'BOSPlatformLibraries.showBAMBIAboutPopup');
  BAMBImenu.addToUi();
}

function addCloudIoTConfig() {
  BOSPlatformLibraries.addCloudIoTConfig();
  logOperation(OperationType.ADD_CLOUD_IOT_CONFIG_TEMPLATE);
}

function addSiteMetadata() {
  BOSPlatformLibraries.addSiteMetadata();
  logOperation(OperationType.ADD_SITE_METADATA_TEMPLATE);
}

function addDeviceMetadata() {
  BOSPlatformLibraries.addDeviceMetadata();
  logOperation(OperationType.ADD_DEVICE_TEMPLATES);
}
