/* BOS Platform Libraries
   BAMBI UI (BOS Automated Management Building Interface)
   BOS Asset Management
   Francesco Anselmo fanselmo@google.com 2025
*/

var html = HtmlService.createHtmlOutput("Metadata templates:<br><br>");

function createAssetManagementMenu() {
  var ui = SpreadsheetApp.getUi();
  const AMmenu = ui.createMenu('BOS Asset Management');
  const generateSubMenu = AMmenu.addSubMenu(ui.createMenu('Generate')
    .addItem('Generate QR Code Payload', 'BOSPlatformLibraries.generateQRCodePayload')
    .addItem('Generate QR Codes', 'BOSPlatformLibraries.generateQRCodesForSelection')
    .addItem('Generate QR Codes with Labels', 'BOSPlatformLibraries.generateQRCodesWithLabels')
  );

    AMmenu.addSeparator();
    AMmenu.addItem('About', 'BOSPlatformLibraries.showAMAboutPopup');
    AMmenu.addToUi();
}

function createBAMBIMenu() {
  var ui = SpreadsheetApp.getUi();
  const BAMBImenu = ui.createMenu('BAMBI');
  const templatesSubMenu = BAMBImenu.addSubMenu(ui.createMenu('Add Templates')
    .addItem('Add Cloud IoT Configuration Template', 'BOSPlatformLibraries.addCloudIoTConfig')
    .addItem('Add Site Metadata Template', 'BOSPlatformLibraries.addSiteMetadata')
    .addItem('Add Device Metadata Templates', 'BOSPlatformLibraries.addDeviceMetadata')
  );

    // BAMBImenu.addSeparator();
  const importSubMenu = BAMBImenu.addSubMenu(ui.createMenu('Import from Google Drive')
      .addItem('Import Cloud IoT Configuration from Google Drive', 'BOSPlatformLibraries.importCloudIoTConfig')
      .addItem('Import Site Metadata from Google Drive', 'BOSPlatformLibraries.importSiteMetadata')
      .addItem('Import Device IDs from Google Drive', 'BOSPlatformLibraries.importDeviceIDs')
      .addItem('Import Device Metadata from Google Drive', 'BOSPlatformLibraries.importDeviceMetadata')
      );

    // BAMBImenu.addSeparator();
  const exportSubMenu = BAMBImenu.addSubMenu(ui.createMenu('Export to Google Drive')
      .addItem('Export Cloud IoT Configuration to Google Drive', 'BOSPlatformLibraries.exportCloudIoTConfig')
      .addItem('Export Site Metadata to Google Drive', 'BOSPlatformLibraries.exportSiteMetadata')
      .addItem('Export Device Metadata to Google Drive', 'BOSPlatformLibraries.exportDeviceMetadata')
      );

    // BAMBImenu.addSeparator();
  const utilitySubMenu = BAMBImenu.addSubMenu(ui.createMenu('Utility functions')
      .addItem('Format Header and Resize', 'BOSPlatformLibraries.formatHeaderAndResize')
      .addItem('Generate UUID', 'BOSPlatformLibraries.fillSelectedWithUUIDs')
      .addItem('Normalise Point Name Cells', 'BOSPlatformLibraries.normaliseSelectedCells')
      .addItem('Sort Sheet Alphabetically', 'BOSPlatformLibraries.sortSheet'));
    BAMBImenu.addSeparator();
    BAMBImenu.addItem('About', 'BOSPlatformLibraries.showBAMBIAboutPopup');
    BAMBImenu.addToUi();
}

function showAMAboutPopup() {
  var template = HtmlService.createTemplateFromFile('about');
  template.projectTitle = "BOS Asset Management";
  template.version = "0.0.1";
  template.createdBy = "Francesco Anselmo";
  template.eMail = "fanselmo@google.com";
  template.date = "2025-01-13";

  var html = template.evaluate()
      .setWidth(400)
      .setHeight(300);

  SpreadsheetApp.getUi().showModalDialog(html, 'About');
}


function showBAMBIAboutPopup() {
  var template = HtmlService.createTemplateFromFile('about');
  template.projectTitle = "BAMBI UI (BOS Automated Management Building Interface)";
  template.version = "0.0.1";
  template.createdBy = "Francesco Anselmo";
  template.eMail = "fanselmo@google.com";
  template.date = "2025-01-13";

  var html = template.evaluate()
      .setWidth(400)
      .setHeight(300);

  SpreadsheetApp.getUi().showModalDialog(html, 'About');
}

function addCloudIoTConfig() {

  const githubRepoOwner = 'faucetsdn';
  const githubRepoName = 'udmi';

  const jsonFilePath = 'master/schema/configuration_execution.json';
  const mySchema = getUDMISchemaFromGitHub(githubRepoOwner, githubRepoName, jsonFilePath);

  const jsonData = generateJSONFromSchema(githubRepoOwner, githubRepoName, mySchema, 5);
  console.log(jsonData);
  // SpreadsheetApp.getUi().alert(prettyPrintJSON(JSON.stringify(jsonData)));

  var html = HtmlService.createHtmlOutput("<pre>"+prettyPrintJSON(JSON.stringify(jsonData))+"</pre>");
  SpreadsheetApp.getUi().showModalDialog(html, 'Cloud IoT Configuration Example');

  createHeaderColumnFromJSON("cloud_iot_config", jsonData);
}

function addSiteMetadata() {

  // https://raw.githubusercontent.com/pisuke/udmi/site_metadata-update/schema/site_metadata.json

  const githubRepoOwner = 'faucetsdn';
  const githubRepoName = 'udmi';
  const jsonFilePath = 'master/schema/site_metadata.json';

  // const githubRepoOwner = 'pisuke';
  // const githubRepoName = 'udmi';
  // const jsonFilePath = 'site_metadata-update/schema/site_metadata.json';

  const mySchema = getUDMISchemaFromGitHub(githubRepoOwner, githubRepoName, jsonFilePath);

  const jsonData = generateJSONFromSchema(githubRepoOwner, githubRepoName, mySchema, 5);
  console.log(jsonData);
  // SpreadsheetApp.getUi().alert(prettyPrintJSON(JSON.stringify(jsonData)));

  var html = HtmlService.createHtmlOutput("<pre>"+prettyPrintJSON(JSON.stringify(jsonData))+"</pre>");
  SpreadsheetApp.getUi().showModalDialog(html, 'Site Metadata Example');

  createHeaderColumnFromJSON("site_metadata", jsonData);
}

function processMetadata(jsonFilePath, sheetName, keys, dialogName, level) {
  const githubRepoOwner = 'faucetsdn';
  const githubRepoName = 'udmi';

  let mySchema = getUDMISchemaFromGitHub(githubRepoOwner, githubRepoName, jsonFilePath);

  let jsonData = generateJSONFromSchema(githubRepoOwner, githubRepoName, mySchema, level);

  // const keys = { "device_id": "TEST-1" };

  const result = { ...keys, ...jsonData };

  console.log(result);

  // let html = HtmlService.createHtmlOutput("<pre>"+prettyPrintJSON(JSON.stringify(result))+"</pre>");
  // SpreadsheetApp.getUi().showModalDialog(html, dialogName);


  html = HtmlService.createHtmlOutput(html.getContent()+"<pre>"+prettyPrintJSON(JSON.stringify(result))+"</pre>");
  var sidebar = SpreadsheetApp.getUi().showSidebar(html);

  createHeaderRowFromJSON(sheetName, result);
}

function addDeviceMetadata() {

  // // processMetadata("master/schema/metadata.json", "metadata", { "device_id": "TEST-1" }, "Metadata Example", 1) // skip the toplevel as all the device information is in the system block

  processMetadata("master/schema/model_system.json", "system", { "device_id": "TEST-1" }, "Metadata System Example", 5)

  processMetadata("master/schema/model_cloud.json", "cloud", { "device_id": "TEST-1" }, "Metadata Cloud Example", 5)
  processMetadata("master/schema/model_gateway.json", "gateway", { "device_id": "TEST-1" }, "Metadata Gateway Example", 5)

  processMetadata("master/schema/model_localnet.json", "localnet", { "device_id": "TEST-1" }, "Metadata Local Network Example", 5)

  processMetadata("master/schema/model_pointset.json", "pointset", { "device_id": "TEST-1", "points_template_name": "pt1"}, "Metadata Pointset Example", 5)
  processMetadata("master/schema/model_pointset_point.json", "points", { "points_template_name": "pt1", "point_name": "test_sensor" }, "Metadata Points Example", 5)

  // processMetadata("master/schema/model_discovery.json", "discovery", { "device_id": "TEST-1" }, "Metadata Discovery Example", 5) // not needed
  // processMetadata("master/schema/model_testing.json", "testing", { "device_id": "TEST-1" }, "Metadata Testing Example", 5) // not needed
  // processMetadata("master/schema/model_fe atures.json", "features", { "device_id": "TEST-1" }, "Metadata Features Example", 5) // not needed

}

function getCellRowColumnByValue(spreadsheet, sheetName, targetValue) {

  const sheet = spreadsheet.getSheetByName(sheetName);

  // Get all values from the sheet
  const values = sheet.getDataRange().getValues();

  // Iterate through the values to find the target value
  for (let row = 0; row < values.length; row++) {
    for (let col = 0; col < values[row].length; col++) {
      if (values[row][col] === targetValue) {
        // Return the row and column (1-based index)
        return { row: row + 1, col: col + 1 };
      }
    }
  }

  // Return null if the value is not found
  return null;
}

function importCloudIoTConfig() {
  // get GDrive folder from links.folder entry
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  position = getCellRowColumnByValue(spreadsheet, "site_metadata", "links.folder")
  const sheet = spreadsheet.getSheetByName("site_metadata");
  const folderUrl = sheet.getRange(position.row, position.col+1).getValue();

  const folderId = getIdFromUrl(folderUrl);
  const folder = DriveApp.getFolderById(folderId);

  var text = HtmlService.createHtmlOutput('Devices Folder URL: '+folderUrl+'<br><br>Devices Folder ID: '+folderId+'<br>');
  var sidebar = SpreadsheetApp.getUi().showSidebar(text);

  // cloudIoTConfigFileURL = findFileByName(folderUrl, "cloud_iot_config.json");

  try {

    const targetFile = searchForFileInSingleFolder(folder, "cloud_iot_config.json");
    const jsonText = getJSONContentFromDriveFileURL(targetFile.getUrl());

    const headers = getDotNotationKeys(JSON.parse(jsonText));

    // const text = HtmlService.createHtmlOutput(folderUrl+' '+folderId+' '+targetFile.getUrl());
    var text = HtmlService.createHtmlOutput(jsonText+"<br>"+headers.toString());
    var sidebar = SpreadsheetApp.getUi().showSidebar(text);

    const cloud_iot_sheet = spreadsheet.getSheetByName("cloud_iot_config");

    // Get all values from the cloud_iot_config sheet
    const values = cloud_iot_sheet.getDataRange().getValues();

    text = HtmlService.createHtmlOutput(text.getContent()+"<br>"+values.toString());
    sidebar = SpreadsheetApp.getUi().showSidebar(text);
    // sidebar.setTitle('Import Cloud IoT Config output');

    // Flatten the JSON object into dot notation
    const flattenedJSON = flattenJSON(JSON.parse(jsonText));

    // Iterate through the flattened JSON items
    for (const key in flattenedJSON) {
      text = HtmlService.createHtmlOutput(text.getContent() + "<br>"+key+" "+flattenedJSON[key]+"<br>");
      sidebar = SpreadsheetApp.getUi().showSidebar(text);
      const cell = findCellByValue(cloud_iot_sheet, values, key);
      if (cell) {
        // Add the value to the cell to the right
        const adjacentCell = cloud_iot_sheet.getRange(cell.getRow(), cell.getColumn() + 1);
        adjacentCell.setValue(flattenedJSON[key]);
      }
    }
  } catch (error) {

    let html = HtmlService.createHtmlOutput("Error updating cloud_iot_config spreadsheet:<br>" + error + "<br><br>Does the cloud_iot_config.json file exist?");
    SpreadsheetApp.getUi().showModalDialog(html, "Error");
    console.error("Error updating cloud_iot_config spreadsheet:", error);
  }

}

function importSiteMetadata() {
  // get GDrive folder from links.folder entry
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  position = getCellRowColumnByValue(spreadsheet, "site_metadata", "links.folder")
  const sheet = spreadsheet.getSheetByName("site_metadata");
  const folderUrl = sheet.getRange(position.row, position.col+1).getValue();

  const folderId = getIdFromUrl(folderUrl);
  const folder = DriveApp.getFolderById(folderId);

  var text = HtmlService.createHtmlOutput('Devices Folder URL: '+folderUrl+'<br><br>Devices Folder ID: '+folderId+'<br>');
  var sidebar = SpreadsheetApp.getUi().showSidebar(text);

  // cloudIoTConfigFileURL = findFileByName(folderUrl, "cloud_iot_config.json");

  try {

    const targetFile = searchForFileInSingleFolder(folder, "site_metadata.json");
    text = HtmlService.createHtmlOutput(text.getContent()+'<br>'+targetFile.getUrl());

    const jsonText = getJSONContentFromDriveFileURL(targetFile.getUrl());
    const headers = getDotNotationKeys(JSON.parse(jsonText));

    // text = HtmlService.createHtmlOutput(text+"<br>"+jsonText+"<br>"+headers.toString());
    sidebar = SpreadsheetApp.getUi().showSidebar(text);

    const site_metadata_sheet = spreadsheet.getSheetByName("site_metadata");

    // Get all values from the cloud_iot_config sheet
    const values = site_metadata_sheet.getDataRange().getValues();

    text = HtmlService.createHtmlOutput(text.getContent()+"<br>"+values.toString());
    sidebar = SpreadsheetApp.getUi().showSidebar(text);
    // sidebar.setTitle('Import Cloud IoT Config output');

    siteMetadataJSON = convertListToCommaSeparatedString(JSON.parse(jsonText), "tags");

    // Flatten the JSON object into dot notation
    var flattenedJSON = flattenJSON(siteMetadataJSON);

    // Iterate through the flattened JSON items
    for (const key in flattenedJSON) {
      text = HtmlService.createHtmlOutput(text.getContent() + "<br>"+key+" "+flattenedJSON[key]+"<br>");
      sidebar = SpreadsheetApp.getUi().showSidebar(text);
      const cell = findCellByValue(site_metadata_sheet, values, key);
      if (cell) {
        // Add the value to the cell to the right
        const adjacentCell = site_metadata_sheet.getRange(cell.getRow(), cell.getColumn() + 1);
        adjacentCell.setValue(flattenedJSON[key]);
      }
    }
  } catch (error) {
    let html = HtmlService.createHtmlOutput("Error updating site_metadata spreadsheet:<br>" + error +"<br><br>Does the site_metadata.json file exist?");
    SpreadsheetApp.getUi().showModalDialog(html, "Error");
    console.error("Error updating site_metadata spreadsheet:", error);
  }
}

function importDeviceIDs() {
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();

  position = getCellRowColumnByValue(spreadsheet, "site_metadata", "links.folder")
  const site_metadata_sheet = spreadsheet.getSheetByName("site_metadata");

  const device_system_sheet = spreadsheet.getSheetByName("system");
  const system_header_row = device_system_sheet.getRange(1, 1, 1, device_system_sheet.getLastColumn()).getValues()[0];

  const folderUrl = site_metadata_sheet.getRange(position.row, position.col+1).getValue();
  const folderId = getIdFromUrl(folderUrl);
  const folder = DriveApp.getFolderById(folderId);

  var text = HtmlService.createHtmlOutput('Devices Folder URL: '+folderUrl+'<br><br>Devices Folder ID: '+folderId+'<br>');
  var sidebar = SpreadsheetApp.getUi().showSidebar(text);

  var devices = getDevices(folderUrl);

  for (var i = 0; i < devices.length; i++) {
    const device = devices[i];
    const deviceName = device.getName();

    // Show device name in the side bar
    text = HtmlService.createHtmlOutput(text.getContent()+'<br><br>'+deviceName);
    sidebar = SpreadsheetApp.getUi().showSidebar(text);

    var row = findRowByValue(device_system_sheet, 1, deviceName);

    if (row>0) {
      // do nothing
    } else {
      device_system_sheet.getRange(device_system_sheet.getLastRow()+1, 1, 1, 1).setValue(deviceName);
    }
  }

}

function importDeviceMetadata() {
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();

  position = getCellRowColumnByValue(spreadsheet, "site_metadata", "links.folder")
  const site_metadata_sheet = spreadsheet.getSheetByName("site_metadata");

  const device_system_sheet = spreadsheet.getSheetByName("system");
  const system_header_row = device_system_sheet.getRange(1, 1, 1, device_system_sheet.getLastColumn()).getValues()[0];
  // const device_system_values = device_system_sheet.getDataRange().getValues();

  const device_cloud_sheet = spreadsheet.getSheetByName("cloud");
  const cloud_header_row = device_cloud_sheet.getRange(1, 1, 1, device_cloud_sheet.getLastColumn()).getValues()[0];
  // const device_cloud_values = device_cloud_sheet.getDataRange().getValues();

  const device_gateway_sheet = spreadsheet.getSheetByName("gateway");
  const gateway_header_row = device_gateway_sheet.getRange(1, 1, 1, device_gateway_sheet.getLastColumn()).getValues()[0];
  // const device_gateway_values = device_gateway_sheet.getDataRange().getValues();

  const device_localnet_sheet = spreadsheet.getSheetByName("localnet");
  const localnet_header_row = device_localnet_sheet.getRange(1, 1, 1, device_localnet_sheet.getLastColumn()).getValues()[0];
  // const device_localnet_values = device_localnet_sheet.getDataRange().getValues();

  const device_pointset_sheet = spreadsheet.getSheetByName("pointset");
  const pointset_header_row = device_pointset_sheet.getRange(1, 1, 1, device_pointset_sheet.getLastColumn()).getValues()[0];
  // const device_pointset_values = device_pointset_sheet.getDataRange().getValues();

  const device_points_sheet = spreadsheet.getSheetByName("points");
  const points_header_row = device_points_sheet.getRange(1, 1, 1, device_points_sheet.getLastColumn()).getValues()[0];
  // const device_points_values = device_points_sheet.getDataRange().getValues();

  const folderUrl = site_metadata_sheet.getRange(position.row, position.col+1).getValue();
  const folderId = getIdFromUrl(folderUrl);
  const folder = DriveApp.getFolderById(folderId);

  var text = HtmlService.createHtmlOutput('Devices Folder URL: '+folderUrl+'<br><br>Devices Folder ID: '+folderId+'<br>');
  var sidebar = SpreadsheetApp.getUi().showSidebar(text);

  // var folderContent = getFolderContentTable(folderUrl);

  // var folderContent = getDevicesTable(folderUrl);

  // text = HtmlService.createHtmlOutput(text.getContent()+"<br>"+folderContent);
  // sidebar = SpreadsheetApp.getUi().showSidebar(text);

  if (validateSelectionDeviceIDs()) {
    const deviceSelection = device_system_sheet.getActiveRange();
    const devicesIDs = deviceSelection.getValues();

    text = HtmlService.createHtmlOutput(text.getContent()+"<br>Importing selected devices: "+devicesIDs.toString()+"<br>");
    sidebar = SpreadsheetApp.getUi().showSidebar(text);

    var devices = getDevices(folderUrl);
    var points_row_values = {};

    for (var i = 0; i < devices.length; i++) {
      const device = devices[i];

      const deviceName = device.getName();

      // text = HtmlService.createHtmlOutput(text.getContent()+'<br>'+targetFile.getUrl());

      if (checkIfStringInValues(deviceName, devicesIDs)) {

        const targetFile = searchForFileInSingleFolder(device, "metadata.json");

        // Show device name in the side bar
        text = HtmlService.createHtmlOutput(text.getContent()+'<br><br>Importing device: '+deviceName);
        sidebar = SpreadsheetApp.getUi().showSidebar(text);

        try {

          const jsonText = getJSONContentFromDriveFileURL(targetFile.getUrl());
          const headers = getDotNotationKeys(JSON.parse(jsonText));

          // text = HtmlService.createHtmlOutput(text+"<br>"+jsonText+"<br>"+headers.toString());
          // sidebar = SpreadsheetApp.getUi().showSidebar(text);



          // Make sure that fields having lists/arrays are flattened into a comma separated string
          var deviceMetadataJSON = convertListToCommaSeparatedString(JSON.parse(jsonText), "tags");

          if (Object.prototype.hasOwnProperty.call(deviceMetadataJSON, "gateway")) {
            const proxyIDs = convertListToCommaSeparatedString(deviceMetadataJSON["gateway"], "proxy_ids")
            if (proxyIDs != "") {
              deviceMetadataJSON["gateway"]["proxy_ids"] = proxyIDs["proxy_ids"];
            }
          }

          // text = HtmlService.createHtmlOutput(text.getContent()+"<br>"+JSON.stringify(deviceMetadataJSON)+"<br>"+"<br>");
          // sidebar = SpreadsheetApp.getUi().showSidebar(text);

          // Flatten the JSON object into dot notation
          var flattenedJSON = flattenJSON(deviceMetadataJSON);

          // text = HtmlService.createHtmlOutput(text.getContent()+"<br>"+JSON.stringify(flattenedJSON)+"<br>"+"<br>");
          // sidebar = SpreadsheetApp.getUi().showSidebar(text);

          var system_row_values = [];
          system_row_values[0] = deviceName;

          var cloud_row_values = [];
          cloud_row_values[0] = deviceName;

          var gateway_row_values = [];
          gateway_row_values[0] = deviceName;

          var localnet_row_values = [];
          localnet_row_values[0] = deviceName;

          var pointset_row_values = [];
          pointset_row_values[0] = deviceName;
          pointset_row_values[1] = deviceName+"_template";

          // var points_row_values = [];

          const points_template_name = deviceName+"_template";
          // points_row_values[points_template_name] = createEmptyArray(points_header_row.length);
          points_row_values[points_template_name] = {};

          // text = HtmlService.createHtmlOutput(text.getContent()+"<br>empty points_row_values<br>"+points_row_values[points_template_name]+" "+points_header_row.length);
          // sidebar = SpreadsheetApp.getUi().showSidebar(text);

          // Iterate through the flattened JSON items
          for (const key in flattenedJSON) {
            // text = HtmlService.createHtmlOutput(text.getContent() + "<br>"+key+" "+flattenedJSON[key]+"<br>");
            // sidebar = SpreadsheetApp.getUi().showSidebar(text);
            sheet_name = key.split('.')[0];

            if (sheet_name === 'system'){
              var column = system_header_row.indexOf(removeSheetName(key))+1;
              system_row_values[column-1]=flattenedJSON[key];
            } else if (sheet_name === 'cloud') {
              var column = cloud_header_row.indexOf(removeSheetName(key))+1;
              cloud_row_values[column-1]=flattenedJSON[key];
            } else if (sheet_name === 'gateway') {
              var column = gateway_header_row.indexOf(removeSheetName(key))+1;
              gateway_row_values[column-1]=flattenedJSON[key];
            } else if (sheet_name === 'localnet') {
              var column = localnet_header_row.indexOf(removeSheetName(key))+1;
              localnet_row_values[column-1]=flattenedJSON[key];
            } else if (sheet_name === 'pointset') {
              var column = pointset_header_row.indexOf(removeSheetName(key))+1;
              pointset_row_values[column-1]=flattenedJSON[key];
              points = key.split('.')[1];
              if (points === 'points') {
                var point_name = key.split('.')[2];
                var column = points_header_row.indexOf(removePointName(key))+1;
                // text = HtmlService.createHtmlOutput(text.getContent() + "<br>"+point_name+" "+column+" "+points_template_name+"<br>");
                // sidebar = SpreadsheetApp.getUi().showSidebar(text);

                if (points_row_values[points_template_name][point_name] == null) {
                  points_row_values[points_template_name][point_name] = createEmptyArray(points_header_row.length);

                  // text = HtmlService.createHtmlOutput(text.getContent()+"<br>empty points_row_values<br>"+points_row_values[points_template_name]+" "+points_header_row.length);
                  // sidebar = SpreadsheetApp.getUi().showSidebar(text);
                }

                points_row_values[points_template_name][point_name][0]=points_template_name;
                points_row_values[points_template_name][point_name][1]=point_name;
                points_row_values[points_template_name][point_name][column-1]=flattenedJSON[key];

                // text = HtmlService.createHtmlOutput(text.getContent()+"<br>"+column+"<br>"+removePointName(key)+"<br>"+points_row_values[points_template_name][point_name]);
                // sidebar = SpreadsheetApp.getUi().showSidebar(text);
              }
            }
          }

          // text = HtmlService.createHtmlOutput(text.getContent() + "<br>"+objectToString(points_row_values));
          // sidebar = SpreadsheetApp.getUi().showSidebar(text);

          // Find the correct row in each sheet, or add a new one

          // System sheet
          var row = findRowByValue(device_system_sheet, 1, deviceName);
          // text = HtmlService.createHtmlOutput(text.getContent() + "<br>"+system_row_values+"<br>"+headers+"<br>"+system_header_row);
          // sidebar = SpreadsheetApp.getUi().showSidebar(text);
          if (row>0) {
            device_system_sheet.getRange(row, 1, 1, system_row_values.length).setValues([system_row_values]);
          } else {
            device_system_sheet.getRange(device_system_sheet.getLastRow()+1, 1, 1, system_row_values.length).setValues([system_row_values]);
          }

          // Cloud sheet
          var row = findRowByValue(device_cloud_sheet, 1, deviceName);
          // text = HtmlService.createHtmlOutput(text.getContent() + "<br>"+cloud_row_values+"<br>"+headers+"<br>"+cloud_header_row);
          // sidebar = SpreadsheetApp.getUi().showSidebar(text);
          if (row>0) {
            device_cloud_sheet.getRange(row, 1, 1, cloud_row_values.length).setValues([cloud_row_values]);
          } else {
            device_cloud_sheet.getRange(device_cloud_sheet.getLastRow()+1, 1, 1, cloud_row_values.length).setValues([cloud_row_values]);
          }

          // Gateway sheet
          var row = findRowByValue(device_gateway_sheet, 1, deviceName);
          // text = HtmlService.createHtmlOutput(text.getContent() + "<br>"+gateway_row_values+"<br>"+headers+"<br>"+gateway_header_row);
          // sidebar = SpreadsheetApp.getUi().showSidebar(text);
          if (row>0) {
            device_gateway_sheet.getRange(row, 1, 1, gateway_row_values.length).setValues([gateway_row_values]);
          } else {
            device_gateway_sheet.getRange(device_gateway_sheet.getLastRow()+1, 1, 1, gateway_row_values.length).setValues([gateway_row_values]);
          }

          // Localnet sheet
          var row = findRowByValue(device_localnet_sheet, 1, deviceName);
          // text = HtmlService.createHtmlOutput(text.getContent() + "<br>"+localnet_row_values+"<br>"+headers+"<br>"+localnet_header_row);
          // sidebar = SpreadsheetApp.getUi().showSidebar(text);
          if (row>0) {
            device_localnet_sheet.getRange(row, 1, 1, localnet_row_values.length).setValues([localnet_row_values]);
          } else {
            device_localnet_sheet.getRange(device_localnet_sheet.getLastRow()+1, 1, 1, localnet_row_values.length).setValues([localnet_row_values]);
          }

          // Pointset sheet
          var row = findRowByValue(device_pointset_sheet, 1, deviceName);
          // text = HtmlService.createHtmlOutput(text.getContent() + "<br>"+pointset_row_values+"<br>"+headers+"<br>"+pointset_header_row);
          // sidebar = SpreadsheetApp.getUi().showSidebar(text);
          if (row>0) {
            device_pointset_sheet.getRange(row, 1, 1, pointset_row_values.length).setValues([pointset_row_values]);
          } else {
            device_pointset_sheet.getRange(device_pointset_sheet.getLastRow()+1, 1, 1, pointset_row_values.length).setValues([pointset_row_values]);
          }

          // Points sheet
          for (const key in points_row_values[points_template_name]) {

              point = points_row_values[points_template_name][key];
              point_name = point[1];
              var row = findRowByTwoValues(device_points_sheet, 1, 2, points_template_name, point_name);
              // text = HtmlService.createHtmlOutput(text.getContent() + "<br><br>point:"+point+"<br>point_name:"+point_name+"<br>row:"+row+"<br>"+points_header_row);
              // sidebar = SpreadsheetApp.getUi().showSidebar(text);
              if (row>0) {
                device_points_sheet.getRange(row, 1, 1, point.length).setValues([point]);
              } else {
                device_points_sheet.getRange(device_points_sheet.getLastRow()+1, 1, 1, point.length).setValues([point]);
              }

          }

        } catch (error) {
          let html = HtmlService.createHtmlOutput("Error loading device "+deviceName+":<br>" + error + "<br><br>Does the device metadata.json file exist?");
          SpreadsheetApp.getUi().showModalDialog(html, "Error");
          console.error("Error loading device "+deviceName+":", error);
        }

      }

    }

  }

}

function exportCloudIoTConfig() {
  // get GDrive folder from links.folder entry
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  position = getCellRowColumnByValue(spreadsheet, "site_metadata", "links.folder")
  const sheet = spreadsheet.getSheetByName("site_metadata");
  const folderUrl = sheet.getRange(position.row, position.col+1).getValue();

  const folderId = getIdFromUrl(folderUrl);
  const folder = DriveApp.getFolderById(folderId);

  var text = HtmlService.createHtmlOutput('Devices Folder URL: '+folderUrl+'<br><br>Devices Folder ID: '+folderId+'<br>');
  var sidebar = SpreadsheetApp.getUi().showSidebar(text);

  // try {


    const cloud_iot_config_sheet = spreadsheet.getSheetByName("cloud_iot_config");

    // Get all values from the cloud_iot_config sheet
    const cell_values = cloud_iot_config_sheet.getDataRange().getValues();

    // Get the data range (assuming it starts from the first row and column)
    const dataRange = cloud_iot_config_sheet.getDataRange();


    // Get the last row with content
    const lastRow = cloud_iot_config_sheet.getLastRow();


    // Get the values in the first two columns
    const values = cloud_iot_config_sheet.getRange(1, 1, lastRow, 2).getValues(); // 2 columns

    cloud_iot_config_flat = {};

    // Iterate over the values
    values.forEach(row => {
      const key = row[0];
      const value = row[1];

      // Output the values (customize as needed)
      // text = HtmlService.createHtmlOutput(text.getContent()+"<br>"+key+" "+value);
      // sidebar = SpreadsheetApp.getUi().showSidebar(text);
      // console.log(`First column: ${key}, Adjacent value: ${value}`);
      if (value != "") {
        cloud_iot_config_flat[key] = value;
      }
    });

    cloud_iot_config = unflattenJSON(cloud_iot_config_flat);

    cloud_iot_config = convertCommaSeparatedStringToList(cloud_iot_config, "tags");

    text = HtmlService.createHtmlOutput(text.getContent()+"<br>"+JSON.stringify(cloud_iot_config));
    sidebar = SpreadsheetApp.getUi().showSidebar(text);

    writeJSONToFile(cloud_iot_config, folderId, "cloud_iot_config.json")

  // } catch (error) {
  //   let html = HtmlService.createHtmlOutput("Error updating cloud_iot_config spreadsheet:<br>" + error +"<br><br>Does the cloud_iot_config.json file exist?");
  //   SpreadsheetApp.getUi().showModalDialog(html, "Error");
  //   console.error("Error exporting cloud_iot_config spreadsheet:", error);
  // }

}

function exportSiteMetadata() {
  // get GDrive folder from links.folder entry
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  position = getCellRowColumnByValue(spreadsheet, "site_metadata", "links.folder")
  const sheet = spreadsheet.getSheetByName("site_metadata");
  const folderUrl = sheet.getRange(position.row, position.col+1).getValue();

  const folderId = getIdFromUrl(folderUrl);
  const folder = DriveApp.getFolderById(folderId);

  var text = HtmlService.createHtmlOutput('Devices Folder URL: '+folderUrl+'<br><br>Devices Folder ID: '+folderId+'<br>');
  var sidebar = SpreadsheetApp.getUi().showSidebar(text);

  // try {


    const site_metadata_sheet = spreadsheet.getSheetByName("site_metadata");

    // Get all values from the cloud_iot_config sheet
    const cell_values = site_metadata_sheet.getDataRange().getValues();

    // Get the data range (assuming it starts from the first row and column)
    const dataRange = site_metadata_sheet.getDataRange();


    // Get the last row with content
    const lastRow = site_metadata_sheet.getLastRow();

    // Get the values in the first two columns
    const values = site_metadata_sheet.getRange(1, 1, lastRow, 2).getValues(); // 2 columns

    site_metadata_flat = {};

    // Iterate over the values
    values.forEach(row => {
      const key = row[0];
      const value = row[1];

      // Output the values
      // text = HtmlService.createHtmlOutput(text.getContent()+"<br>"+key+" "+value);
      // sidebar = SpreadsheetApp.getUi().showSidebar(text);
      // console.log(`First column: ${key}, Adjacent value: ${value}`);
      site_metadata_flat[key] = value;
    });

    site_metadata = unflattenJSON(site_metadata_flat);

    site_metadata = convertCommaSeparatedStringToList(site_metadata, "tags");

    text = HtmlService.createHtmlOutput(text.getContent()+"<br>"+JSON.stringify(site_metadata));
    sidebar = SpreadsheetApp.getUi().showSidebar(text);

    writeJSONToFile(site_metadata, folderId, "site_metadata.json")



  // } catch (error) {
  //   let html = HtmlService.createHtmlOutput("Error updating site_metadata spreadsheet:<br>" + error +"<br><br>Does the site_metadata.json file exist?");
  //   SpreadsheetApp.getUi().showModalDialog(html, "Error");
  //   console.error("Error exporting site_metadata spreadsheet:", error);
  // }
}

function exportDeviceMetadata() {
  // get GDrive folder from links.folder entry
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  position = getCellRowColumnByValue(spreadsheet, "site_metadata", "links.folder")
  const site_metadata_sheet = spreadsheet.getSheetByName("site_metadata");

  const device_system_sheet = spreadsheet.getSheetByName("system");
  const system_header_row = device_system_sheet.getRange(1, 1, 1, device_system_sheet.getLastColumn()).getValues()[0];
  sortRowsAlphabetically(device_system_sheet);
  const device_system_values = device_system_sheet.getDataRange().getValues();

  const device_cloud_sheet = spreadsheet.getSheetByName("cloud");
  const cloud_header_row = device_cloud_sheet.getRange(1, 1, 1, device_cloud_sheet.getLastColumn()).getValues()[0];
  sortRowsAlphabetically(device_cloud_sheet);
  const device_cloud_values = device_cloud_sheet.getDataRange().getValues();

  const device_gateway_sheet = spreadsheet.getSheetByName("gateway");
  const gateway_header_row = device_gateway_sheet.getRange(1, 1, 1, device_gateway_sheet.getLastColumn()).getValues()[0];
  sortRowsAlphabetically(device_gateway_sheet);
  const device_gateway_values = device_gateway_sheet.getDataRange().getValues();

  const device_localnet_sheet = spreadsheet.getSheetByName("localnet");
  const localnet_header_row = device_localnet_sheet.getRange(1, 1, 1, device_localnet_sheet.getLastColumn()).getValues()[0];
  sortRowsAlphabetically(device_localnet_sheet);
  const device_localnet_values = device_localnet_sheet.getDataRange().getValues();

  const device_pointset_sheet = spreadsheet.getSheetByName("pointset");
  const pointset_header_row = device_pointset_sheet.getRange(1, 1, 1, device_pointset_sheet.getLastColumn()).getValues()[0];
  sortRowsAlphabetically(device_pointset_sheet);
  const device_pointset_values = device_pointset_sheet.getDataRange().getValues();

  const device_points_sheet = spreadsheet.getSheetByName("points");
  const points_header_row = device_points_sheet.getRange(1, 1, 1, device_points_sheet.getLastColumn()).getValues()[0];
  sortRowsAlphabetically(device_points_sheet);
  const device_points_values = device_points_sheet.getDataRange().getValues();

  const folderUrl = site_metadata_sheet.getRange(position.row, position.col+1).getValue();
  const folderId = getIdFromUrl(folderUrl);
  const folder = DriveApp.getFolderById(folderId);

  var text = HtmlService.createHtmlOutput('Devices Folder URL: '+folderUrl+'<br><br>Devices Folder ID: '+folderId+'<br>');
  var sidebar = SpreadsheetApp.getUi().showSidebar(text);

  if (validateSelectionDeviceIDs()) {
    const deviceSelection = device_system_sheet.getActiveRange();
    const devicesIDs = deviceSelection.getValues();

    text = HtmlService.createHtmlOutput(text.getContent()+"<br>Exporting selected devices: "+devicesIDs.toString()+"<br>");
    sidebar = SpreadsheetApp.getUi().showSidebar(text);

    // for (var i = 1; i < 3; i++) {
    for (var i = 1; i < device_system_values.length; i++) {

      const deviceID = device_system_values[i][0];

      if (checkIfStringInValues(deviceID, devicesIDs)) {

        text = HtmlService.createHtmlOutput(text.getContent()+"<br>Exporting device: "+deviceID+"<br>");
        sidebar = SpreadsheetApp.getUi().showSidebar(text);

        const device_system_index = findRowByValue(device_system_sheet, 1, deviceID)-1;
        const device_system = device_system_values[device_system_index];

        const device_cloud_index = findRowByValue(device_cloud_sheet, 1, deviceID)-1;
        const device_cloud = device_cloud_values[device_cloud_index];

        const device_gateway_index = findRowByValue(device_gateway_sheet, 1, deviceID)-1;
        const device_gateway = device_gateway_values[device_gateway_index];

        const device_localnet_index = findRowByValue(device_localnet_sheet, 1, deviceID)-1;
        const device_localnet = device_localnet_values[device_localnet_index];

        const device_pointset_index = findRowByValue(device_pointset_sheet, 1, deviceID)-1;
        const device_pointset = device_pointset_values[device_pointset_index];

        var device_metadata_flat = {};

        // Process system values
        for (var j = 0; j < system_header_row.length; j++) {
          value = device_system[j];
          if (value != "") {
            device_metadata_flat["system."+system_header_row[j]] = value;
          }
        }

        // Process cloud values
        for (var j = 0; j < cloud_header_row.length; j++) {
          value = device_cloud[j];
          if (value != "") {
            device_metadata_flat["cloud."+cloud_header_row[j]] = value;
          }
        }

        // Process gateway values
        for (var j = 0; j < gateway_header_row.length; j++) {
          value = device_gateway[j];
          if (value != "") {
            device_metadata_flat["gateway."+gateway_header_row[j]] = value;
          }
        }

        // Process localnet values
        for (var j = 0; j < localnet_header_row.length; j++) {
          value = device_localnet[j];
          if (value != "") {
            device_metadata_flat["localnet."+localnet_header_row[j]] = value;
          }
        }

        // Process pointset values
        for (var j = 0; j < pointset_header_row.length; j++) {
          value = device_pointset[j];
          if (value != "") {
            device_metadata_flat["pointset."+pointset_header_row[j]] = value;
          }
        }

        // Expand points using the points template
        const points = convertPointRowsToDictionary(device_points_sheet, 1, device_metadata_flat["pointset.points_template_name"], "pointset.points.");

        device_metadata_flat = convertCommaSeparatedStringToList(device_metadata_flat, "system.tags");
        device_metadata_flat = convertCommaSeparatedStringToList(device_metadata_flat, "gateway.proxy_ids");

        // Join device metadata and pointset metadata
        device_metadata_flat = Object.assign({}, device_metadata_flat, points);

        const deviceName = device_metadata_flat["system.device_id"];
        device_metadata_flat["timestamp"] = getCurrentDateTimeISO();
        device_metadata_flat["version"] = "1.5.2";

        // Remove unwanted keys
        delete device_metadata_flat["system.device_id"];
        delete device_metadata_flat["cloud.device_id"];
        delete device_metadata_flat["gateway.device_id"];
        delete device_metadata_flat["localnet.device_id"];
        delete device_metadata_flat["pointset.device_id"];
        delete device_metadata_flat["pointset.points_template_name"];

        var device_metadata = unflattenJSON(device_metadata_flat);

        devicesFolder = getFolderInsideFolder(folderUrl, "devices");
        targetDeviceFolder = getFolderInsideFolder(devicesFolder.getUrl(), deviceName);

        // Show device name in the side bar
        text = HtmlService.createHtmlOutput(text.getContent()+'<br><br>'+targetDeviceFolder);
        sidebar = SpreadsheetApp.getUi().showSidebar(text);

        // var text = HtmlService.createHtmlOutput(text.getContent()+'<br>Points: '+objectToString(points)+'<br>');
        // var sidebar = SpreadsheetApp.getUi().showSidebar(text);

        // text = HtmlService.createHtmlOutput(text.getContent()+"<br>"+"<br>"+JSON.stringify(device_metadata_flat)+"<br>"+"<br>"+JSON.stringify(device_metadata)+"<br>"+"<br>"+JSON.stringify(points)+"<br>"+"<br>"+targetDeviceFolder+"<br>"+"<br>");
        // sidebar = SpreadsheetApp.getUi().showSidebar(text);

        // TODO make sure that we create the folder if doesn't exist
        writeJSONToFile(device_metadata, targetDeviceFolder.getId(), "metadata.json")

      }




    }

  }

}

function sortSheet(){
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getActiveSheet();
  sortRowsAlphabetically(sheet);
}

