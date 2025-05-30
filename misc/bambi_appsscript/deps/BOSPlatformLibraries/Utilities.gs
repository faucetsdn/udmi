/* BOS Platform Libraries
   Utilities functions
   2024 fanselmo@google.com
*/

function getCurrentDateTimeISO() {
  const now = new Date();
  const isoTimestamp = now.toISOString();
  return isoTimestamp;
}

function getDevicesTable(folderUrl) {
  const folderId = getIdFromUrl(folderUrl);
  const folder = DriveApp.getFolderById(folderId);
  const folders = folder.getFolders();

  while (folders.hasNext()) {
    const devicesFolder = folders.next();
    if (devicesFolder.getName() === "devices") {
       return getFolderContentTable(devicesFolder.getId());
    }
  }
}

function getDevices(folderUrl) {
  const folderId = getIdFromUrl(folderUrl);
  const folder = DriveApp.getFolderById(folderId);
  const folders = folder.getFolders();

  const deviceInfo = [];

  while (folders.hasNext()) {
    const devicesFolder = folders.next();
    if (devicesFolder.getName() === "devices") {
      const devices = devicesFolder.getFolders();
      while (devices.hasNext()) {
        const device = devices.next();
        // deviceInfo.push({
        //   name: device.getName(),
        //   id: device.getId(),
        //   type: "folder"
        // });
        deviceInfo.push(device);
      }
    }
  }

  return deviceInfo;
}

// function getDevicesMetadata(folderUrl) {
//   const folderId = getIdFromUrl(folderUrl);
//   const folder = DriveApp.getFolderById(folderId);
//   const folders = folder.getFolders();

//   const deviceInfo = [];

//   while (folders.hasNext()) {
//     const device = folders.next();
//     deviceInfo.push({
//       name: folder.getName(),
//       id: folder.getId(),
//       type: "folder"
//     });
//   }

//   return deviceInfo;
// }


function getFolderContentTable(folderUrl) {
  const folderId = getIdFromUrl(folderUrl);
  const folder = DriveApp.getFolderById(folderId);
  // const folder = DriveApp.getFileById(folderUrl.split('/').pop());
  const files = folder.getFiles();
  const folders = folder.getFolders();

  const fileInfo = [];
  while (files.hasNext()) {
    const file = files.next();
    fileInfo.push({
      name: file.getName(),
      id: file.getId(),
      type: "file"
    });
  }

  while (folders.hasNext()) {
    const folder = folders.next();
    fileInfo.push({
      name: folder.getName(),
      id: folder.getId(),
      type: "folder"
    });
  }

  const ui = HtmlService.createHtmlOutputFromFile('folder');
  SpreadsheetApp.getUi().showSidebar(ui);

  const html = `
    <table>
      <thead>
        <tr>
          <th>Name</th>
          <th>ID</th>
          <th>Type</th>
        </tr>
      </thead>
      <tbody>
        ${getFileFolderInfoHtml(fileInfo)}
      </tbody>
    </table>
  `;

  return html;

}

function getFileInfoHtml(files) {
  let html = '';
  while (files.hasNext()) {
    const file = files.next();
    html += `
      <tr>
        <td>${file.getName()}</td>
        <td>${file.getId()}</td>
      </tr>
    `;
  }
  return html;
}

function getFileFolderInfoHtml(fileInfo) {
  let html = '';
  fileInfo.forEach(item => {
    html += `
      <tr>
        <td>${item.name}</td>
        <td>${item.id}</td>
        <td>${item.type}</td>
      </tr>
    `;
  });
  return html;
}

function isArray(input) {
  if (Object.prototype.toString.call(input) === '[object Array]') {
    return true;
  } else {
    return false;
  }
}

function getFirstDictionaryItem(dictionary) {
  const keys = Object.keys(dictionary);
  if (keys.length > 0) {
    const firstKey = keys[0];
    return dictionary[firstKey];
  } else {
    return null;
  }
}

function convertArrayToDictionary(array) {
  const dictionary = {};
  for (const item of array) {
    dictionary[item] = "example_string";
  }
  return dictionary;
}

function convertArrayToDictionaryWithChildren(array, childSchema) {
  const dictionary = {};
  for (const item of array) {
    if (childSchema != "") {
      dictionary[item] = childSchema;
    } else {
      dictionary[item] = "example_string";
    }
  }
  return dictionary;
}


function convertCommaSeparatedStringToList(jsonObject, key) {
  try {
    // Check if the key exists and the value is a string
    if (key in jsonObject && typeof jsonObject[key] === 'string') {
      // Split the comma-separated string into an array
      const stringList = jsonObject[key].split(",");

      // Trim whitespace from each element in the array
      const trimmedList = stringList.map(item => item.trim());

      // Update the JSON object with the new list
      jsonObject[key] = trimmedList;
    }

    return jsonObject;
  } catch (error) {
    console.error("Error converting string to list:", error);
    return null;
  }
}

function convertListToCommaSeparatedString(jsonObject, key) {
  try {
    // Check if the key exists and the value is an array
    if (key in jsonObject && Array.isArray(jsonObject[key])) {
      // Join the list elements with commas
      const commaSeparatedString = jsonObject[key].join(",");

      // Update the JSON object with the new string
      jsonObject[key] = commaSeparatedString;
    }

    return jsonObject;
  } catch (error) {
    console.error("Error converting list to string:", error);
    return null;
  }
}

function writeJSONToFile(jsonObject, folderId, fileName) {
  try {
    // Get the folder
    const folder = DriveApp.getFolderById(folderId);

    // Search for existing file with the same name
    const files = folder.getFilesByName(fileName);

    let file;
    if (files.hasNext()) {
      // If a file with the same name exists, get it
      file = files.next();
      // Update the content of the existing file with the prettified JSON string
      const jsonString = JSON.stringify(jsonObject, null, 2); // 2 spaces for indentation
      file.setContent(jsonString);
    } else {
      // If no file with the same name exists, create a new one
      const jsonString = JSON.stringify(jsonObject, null, 2); // 2 spaces for indentation
      file = folder.createFile(fileName, jsonString);
    }

    // Log the file URL
    console.log('File updated/created successfully with URL: ' + file.getUrl());
  } catch (error) {
    console.error("Error writing JSON to file:", error);
  }
}

function flattenJSON(obj, path = "", result = {}) {
  for (const key in obj) {
    const currentPath = path ? `${path}.${key}` : key;
    if (typeof obj[key] === 'object') {
      flattenJSON(obj[key], currentPath, result);
    } else {
      result[currentPath] = obj[key];
    }
  }
  return result;
}

function unflattenJSON(flattenedJSON) {
  const result = {};
  for (const key in flattenedJSON) {
    const keys = key.split('.');
    let current = result;
    for (let i = 0; i < keys.length - 1; i++) {
      const key = keys[i];
      if (!current[key]) {
        current[key] = {};
      }
      current = current[key];
    }
    current[keys[keys.length - 1]] = flattenedJSON[key];
  }
  return result;
}

function getJSONContentFromURL(fileURL) {
  try {
    // Fetch the file content from the URL
    const response = UrlFetchApp.fetch(fileURL);
    const jsonString = response.getContentText();

    return jsonString; // Return the JSON content as a string
  } catch (error) {
    console.error("Error getting JSON content:", error);
    return null;
  }
}

function findFileByName(folderUrl, fileName) {
  try {
    // Get the folder ID from the URL
    const folderId = getIdFromUrl(folderUrl);
    const folder = DriveApp.getFolderById(folderId);

    // Search for the file in the folder and its children
    return searchForFile(folder, fileName);
  } catch (error) {
    console.error("Error finding file:", error);
    return null;
  }
}

function getJSONContentFromDriveFileURL(fileURL) {
  try {
    // Get the file ID from the URL
    const fileId = getIdFromUrl(fileURL);

    // Get the file using DriveApp
    const file = DriveApp.getFileById(fileId);

    // Get the file content as a string
    const jsonContent = file.getBlob().getDataAsString();

    return jsonContent;
  } catch (error) {
    console.error("Error getting JSON content:", error);
    return null;
  }
}

function getIdFromUrl(url) {
  // Regular expression to extract the ID from the URL
  const match = url.match(/[-\w]{25,}/);
  return match ? match[0] : null;
}

function searchForFileInSingleFolder(folder, fileName) {
  // Search for the file in the current folder
  const files = folder.getFilesByName(fileName);
  if (files.hasNext()) {
    return files.next();
  }

  // File not found
  return null;
}

function getFolderInsideFolder(parentFolderUrl, childFolderName) {
  const parentFolderId = getIdFromUrl(parentFolderUrl);
  const parentFolder = DriveApp.getFolderById(parentFolderId);

  const folders = parentFolder.getFoldersByName(childFolderName);
  if (folders.hasNext()) {
    return folders.next();
  } else {
    const newFolder = parentFolder.createFolder(childFolderName);
    return newFolder;
  }
}

function getFileFromFolder(folderUrl, fileName) {
  const folderId = folderUrl.split('/').pop();
  const folder = DriveApp.getFolderById(folderId);

  const files = folder.getFilesByName(fileName);
  if (files.hasNext()) {
    return files.next();
  } else {
    return null;
  }
}

function searchForFile(folder, fileName) {
  // Search for the file in the current folder
  const files = folder.getFilesByName(fileName);
  if (files.hasNext()) {
    return files.next();
  }

  // If not found, search in subfolders recursively
  const subfolders = folder.getFolders();
  while (subfolders.hasNext()) {
    const subfolder = subfolders.next();
    const file = searchForFile(subfolder, fileName);
    if (file) {
      return file;
    }
  }

  // File not found
  return null;
}

function findRowByValue(sheet, searchColumn, searchValue) {
  const range = sheet.getDataRange();
  const values = range.getValues();  

  for (let i = 0; i < values.length; i++) {
    if (values[i][searchColumn - 1] === searchValue) {
      return i + 1; // Row index starts from 1 in Google Sheets
    }
  }
  return -1; // Not found
}

function findRowByTwoValues(sheet, searchColumn1, searchColumn2, searchValue1, searchValue2) {
  const range = sheet.getDataRange();
  const values = range.getValues();  

  for (let i = 0; i < values.length; i++) {
    if ((values[i][searchColumn1 - 1] === searchValue1) && values[i][searchColumn2 - 1] === searchValue2) {
      return i + 1; // Row index starts from 1 in Google Sheets
    }
  }
  return -1; // Not found
}

function objectToString(obj) {
  if (typeof obj !== 'object') {
    return obj.toString();
  }

  const keys = Object.keys(obj);
  let str = "";
  for (const key of keys) {
    str += `${key}: ${objectToString(obj[key])}, `;
  }
  return `{${str.slice(0, -2)}}`;
}

function removeSheetName(stringWithDots) {
  const parts = stringWithDots.split('.');
  parts.shift(); // Remove the first element
  return parts.join('.');
}

function removePointName(stringWithDots) {
  const parts = stringWithDots.split('.');
  parts.shift(); // Remove the first element
  parts.shift(); // Remove the second element
  parts.shift(); // Remove the third element (point name)
  return parts.join('.');
}

function createEmptyArray(size) {
  const array = [];
  for (let i = 0; i < size; i++) {
    array.push("");
  }
  return array;
}

function generateUUIDItem() {
  var sheet = SpreadsheetApp.getActiveSheet();
  var activeCell = sheet.getActiveCell();
  if (activeCell.getValue() == ""){
    activeCell.setValue(uuid());
  }
}

function fillSelectedWithUUIDs() {
  let curSheet = SpreadsheetApp.getActiveSheet();
  let curSelection = curSheet.getSelection();
  let curRange = curSelection.getActiveRange();

  let ui = SpreadsheetApp.getUi();

  if (curRange.getNumColumns() !== 1) {
    ui.alert(`Range must only contain one column.`);
    return;
  }

  for (let i = 0; i < curRange.getNumRows(); i++) {
    let curCell = curRange.getCell(1 + i, 1);
    if (curCell.getValue() !== "") {
      ui.alert(`ERROR: Cannot overwrite value in cell (${curCell.getA1Notation()})`);
      return;
    }
  }

  for (let i = 0; i < curRange.getNumRows(); i++) {
    curRange.getCell(1 + i, 1).setValue("uuid://"+Utilities.getUuid())
  }

  ui.alert(`Added ${curRange.getNumRows()} UUIDs`);
}

function onEdit(e) {
  if (e.range.getFormula().toUpperCase()  == "=UUID(TRUE)") {
    e.range.setValue("uuid://"+Utilities.getUuid());
  }
}

function uuid() {
  return "uuid://"+Utilities.getUuid();
}

function normaliseSelectedCells() {
  const sheet = SpreadsheetApp.getActiveSheet();
  const range = sheet.getActiveRange();
  const values = range.getValues();

  for (let i = 0; i < values.length; i++) {
    for (let j = 0; j < values[i].length; j++) {
      if (typeof values[i][j] === 'string') {
        let cellValue = values[i][j].toLowerCase();
        cellValue = cellValue.replace(/-/g, "_"); // Replace hyphens with underscores
        cellValue = cellValue.replace(/_+$/, ""); // Remove trailing underscores
        cellValue = cellValue.replace(/__/g, "_"); // Replace double underscores with single underscore
        values[i][j] = cellValue;
      }
    }
  }

  range.setValues(values);
}

function validateSelectionDeviceIDs() {
  const sheet = SpreadsheetApp.getActiveSheet();
  const selection = sheet.getActiveRange();

  // Check if only one column is selected
  if (selection.getNumColumns() !== 1) {
    Browser.msgBox("Error", "Please select device entries from only one column.", Browser.Buttons.OK);
    return false;
  }

  // Get the column index of the selection
  const selectionColumn = selection.getColumn();

  // Get the header row
  const headerRow = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];

  // Check if the selected column has the header "device_id"
  if (headerRow[selectionColumn - 1] !== "device_id") {
    Browser.msgBox("Error", "Please select device entries from the column with the header 'device_id'.", Browser.Buttons.OK);
    return false;
  }

  return true;
}

function sortRowsAlphabetically(sheet) {

  const range = sheet.getDataRange();
  const values = range.getValues();

  // Extract header row
  const headerRow = values.shift();

  // Sort data (excluding header row)
  values.sort((a, b) => {
    if (a[0] === null || b[0] === null) {
      return 0; // Handle null values
    }
    return a[0].localeCompare(b[0]);
  });

  // Add header row back to the data
  values.unshift(headerRow);

  // Write sorted data back to the sheet
  range.setValues(values);
}

function checkIfStringInValues(searchString, values) {

  for (let row = 0; row < values.length; row++) {
    for (let col = 0; col < values[row].length; col++) {
      if (String(values[row][col]) === searchString) {
      // if (String(values[row][col]).includes(searchString)) {
        return true; // String found
      }
    }
  }

  return false; // String not found
}