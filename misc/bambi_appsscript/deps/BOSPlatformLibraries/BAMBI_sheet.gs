/* BOS Platform Libraries
   BAMBI sheet functions
   2025 fanselmo@google.com
*/

function findCellByValue(sheet, values, targetValue) {
  for (let row = 0; row < values.length; row++) {
    for (let col = 0; col < values[row].length; col++) {
      if (values[row][col] === targetValue) {
        return sheet.getRange(row + 1, col + 1);
      }
    }
  }
  return null;
}

function getSheetByName(sheetName) {
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet(); // Get the active spreadsheet
  var sheet = spreadsheet.getSheetByName(sheetName); // Retrieve the sheet by its name

  if (sheet) {
    // Sheet found, you can work with it here
    console.log("Sheet found:", sheet.getName());
  } else {
    // Sheet not found
    console.log("Sheet not found with name:", sheetName);
  }

  return sheet; // Return the sheet object or null if not found
}

function getOrCreateSheet(sheetName) {
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = spreadsheet.getSheetByName(sheetName);

  if (!sheet) {
    // Sheet doesn't exist, so create it
    sheet = spreadsheet.insertSheet(sheetName);
    console.log("Created new sheet:", sheetName);
  } else {
    console.log("Sheet already exists:", sheetName);
  }

  return sheet;
}

function removeItemsByName(array, namesToRemove) {
  return array.filter(item => !namesToRemove.includes(item));
}

function getDotNotationKeys(obj, prefix = '') {
  let keys = [];

  for (const key in obj) {
    if (obj.hasOwnProperty(key)) {
      const newKey = prefix ? `${prefix}.${key}` : key;

      // if (typeof obj[key] === 'object' && obj[key] !== null) {
      if (typeof obj[key] === 'object') {
        keys = keys.concat(getDotNotationKeys(obj[key], newKey));
      } else {
        keys.push(newKey);
      }
    }
  }

  return removeItemsByName(keys, ["timestamp", "version", "hash", "upgraded_from", "config.static_file", "blocked", "detail", "credentials",
     "updated_time", "last_event_time", "last_state_time", "last_config_time", "last_error_time", "last_config_ack", "operation", "metadata_str", "family"
     ]);
}

function transposeFirstRowToFirstColumnAndClear() {
  const sheet = SpreadsheetApp.getActiveSheet();
  const lastColumn = sheet.getLastColumn();

  // Get the values from the first row
  const sourceRange = sheet.getRange(1, 1, 1, lastColumn);
  const sourceValues = sourceRange.getValues();

  // Transpose the 2D array
  const transposedValues = sourceValues[0].map((_, colIndex) => sourceValues.map(row => row[colIndex]));

  // Clear the original source range (first row)
  sourceRange.clearContent();

  // Paste the transposed values into the first column
  const destinationRange = sheet.getRange(1, 1, transposedValues.length, 1);
  destinationRange.setValues(transposedValues);

}

function showAlertFromFunction(messageText) {
  Browser.msgBox(messageText, Browser.Buttons.OK);
}

function formatHeaderAndResize(sheet) {
  // const sheet = SpreadsheetApp.getActive().getActiveSheet();
  // sheet.activate();

  // const filter = sheet.getFilter();
  // if (filter) {
  //   filter.remove();
  //   console.log("Removed filter from sheet:", sheet.getName());
  // }

  // const range = SpreadsheetApp.getActive().getActiveSheet().getDataRange(); // Get all data in the range
  const range = sheet.getDataRange(); // Get all data in the range
  // const myMessage = "This is a dynamic alert message!";
  const values = range.getValues();
  const name = sheet.getName();
  const stringResult = name + values.map(row => row.join(",")).join("\n"); // Join rows with commas, columns with newlines

  const headerRange = sheet.getRange(1, 1, 1, range.getNumColumns()); // First row only

  // Make header row bold
  headerRange.setFontWeight('bold');

  // Add filters to header row
  sheet.getFilter()?.remove(); // Remove existing filter if any
  sheet.setFrozenRows(1); // Freeze header
  range.createFilter();

  // Auto-resize columns
  sheet.autoResizeColumns(1, range.getNumColumns());
}


function createHeaderColumnFromJSON(sheetName, jsonData) {

  const sheet = getOrCreateSheet(sheetName);
  sheet.clear();

  const headers = getDotNotationKeys(jsonData);

  console.log(headers);

  // Set the headers in the first row of the sheet
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]);

  // Transpose the first row to the first column
  transposeFirstRowToFirstColumnAndClear();

  const headerRange = sheet.getRange(1, 1, headers.length, 1); // First column only

  // Make header column bold
  headerRange.setFontWeight('bold');

  sheet.autoResizeColumns(1, 1);

  const numColumns = sheet.getLastColumn();

  for (let i = 1; i <= numColumns; i++) {
    const columnWidth = sheet.getColumnWidth(i);
    sheet.setColumnWidth(i, columnWidth * 1.1);
  }

}

function createHeaderRowFromJSON(sheetName, jsonData) {

  const sheet = getOrCreateSheet(sheetName);
  sheet.clear();

  const headers = getDotNotationKeys(jsonData);

  console.log(headers);

  // Set the headers in the first row of the sheet
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]);

  formatHeaderAndResize(sheet);

  const numColumns = sheet.getLastColumn();

  for (let i = 1; i <= numColumns; i++) {
    const columnWidth = sheet.getColumnWidth(i);
    sheet.setColumnWidth(i, columnWidth * 1.1);
  }

}

function getRangeByColumnValue(sheet, columnNumber, searchValue) {
  const range = sheet.getDataRange();
  const values = range.getValues();

  const matchingRows = [];
  for (let i = 0; i < values.length; i++) {
    if (values[i][columnNumber - 1] === searchValue) {
      matchingRows.push(i + 1); // Row index starts from 1 in Google Sheets
    }
  }

  if (matchingRows.length > 0) {
    const firstRow = Math.min(...matchingRows);
    const lastRow = Math.max(...matchingRows);
    const range = sheet.getRange(firstRow, columnNumber, lastRow - firstRow + 1, 1);
    return range;
  } else {
    return null;
  }
}

function convertPointRowsToDictionary(sheet, searchColumn, searchValue, prependToHeader) {
  const range = sheet.getDataRange();
  const values = range.getValues();
  const headers = values[0];

  const rowData = {};
  for (let i = 1; i < values.length; i++) { // Start from 1 to skip the header row
    if (values[i][searchColumn - 1] === searchValue) {

      for (let j = 0; j < headers.length; j++) {
        if (headers[j] != "points_template_name" && headers[j] != "point_name" && values[i][j] !== "") {
          const point_name = values[i][1];
          rowData[prependToHeader+point_name+"."+headers[j]] = values[i][j];
        }
      }
    }
  }

  return rowData;
}

