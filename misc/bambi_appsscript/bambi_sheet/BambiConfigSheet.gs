function createBambiConfigSheetOnOpen() {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  const sheetName = BAMBI_CONFIG_SHEET;

  let sheet = spreadsheet.getSheetByName(sheetName);
  if (!sheet) {
    sheet = createBambiConfigSheet();
  } else {
    return;
  }
}

function overwriteBambiConfigSheet() {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  const sheetName = BAMBI_CONFIG_SHEET;

  let sheet = spreadsheet.getSheetByName(sheetName);
  if (sheet) {
    const ui = SpreadsheetApp.getUi();
    const response = ui.alert(
      'The sheet "' + sheetName + '" already exists. Do you want to overwrite it?',
      ui.ButtonSet.YES_NO
    );
    if (response == ui.Button.YES) {
      spreadsheet.deleteSheet(sheet);
      createBambiConfigSheet();
    } else {
      return;
    }
  } else {
    createBambiConfigSheet();
  }
}

function createBambiConfigSheet() {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  const sheetName = BAMBI_CONFIG_SHEET;
  sheet = spreadsheet.insertSheet(sheetName);
  logOperation(OperationType.BAMBI_CONFIG_SHEET_ADDED);

  const title1 = "BAMBI Inputs";
  const headers1 = ["Input Key", "Value"];
  const data1 = [
    ["project_id", ""],
    ["registry_id", ""],
    ["udmi_namespace", DEFAULT_NAMESPACE],
    ["import_branch", ""]
  ];
  let lastRowUsed = createFormattedTableWithTitle(sheet, title1, headers1, data1, 1, 1);

  const title2 = "Metadata [Auto-populated, DO NOT EDIT]";
  const headers2 = ["Key", "Value"];
  const data2 = [
    ["last_status", ""],
    ["proposal_branch", ""],
    ["pr_link", ""]
  ];
  lastRowUsed = createFormattedTableWithTitle(sheet, title2, headers2, data2, lastRowUsed + 3, 1);

  const lastColumnWithData = sheet.getLastColumn();
  for (let i = 1; i <= lastColumnWithData; i++) {
    sheet.autoResizeColumn(i);
  }

  sheet.getRange(3, 1, 4, 1).setBackground("#eeeeee");
  sheet.getRange(3, 1, 4, 1).setFontWeight("bold");

  sheet.getRange(11, 1, 3, 2).setBackground("#eeeeee");
  sheet.getRange(11, 1, 3, 1).setFontWeight("bold");

  sheet.setColumnWidth(1, 300);
  sheet.setColumnWidth(2, 300);

  logOperation(OperationType.BAMBI_CONFIG_TABLES_ADDED);
  Logger.log(`Tables were created in sheet: ${sheetName}`);
}

/**
 * Creates a single formatted table with a merged title on the given sheet.
 * @param {Sheet} sheet The sheet object where the table will be created.
 * @param {string} tableTitle The title to display in a merged cell above the table.
 * @param {Array<string>} headers An array of strings for the table headers.
 * @param {Array<Array<string|number|boolean|Date>>} data A 2D array of data for the table rows.
 * @param {number} startRow The 1-indexed row number where the title of the table should start.
 * @param {number} startColumn The 1-indexed column number where the table should start.
 * @return {number} The last row number used by this table.
 */
function createFormattedTableWithTitle(sheet, tableTitle, headers, data, startRow, startColumn) {
  const numHeaderCols = headers.length;
  const numDataRows = data.length > 0 ? data.length : 0;

  const titleRow = startRow;
  const titleRange = sheet.getRange(titleRow, startColumn, 1, numHeaderCols);
  titleRange.merge();
  titleRange.setValue(tableTitle);
  titleRange.setFontWeight("bold");
  titleRange.setFontSize(12);
  titleRange.setHorizontalAlignment("center");
  titleRange.setVerticalAlignment("middle");
  titleRange.setBackground("#a2b9a7");

  const headerRow = titleRow + 1;
  const headerRange = sheet.getRange(headerRow, startColumn, 1, numHeaderCols);
  headerRange.setValues([headers]);
  headerRange.setFontWeight("bold");
  headerRange.setBackground("#657c6a");
  headerRange.setFontColor("#ffffff");
  headerRange.setHorizontalAlignment("center");
  headerRange.setVerticalAlignment("middle");

  let dataRange;
  let firstDataRowActual = headerRow + 1;
  if (numDataRows > 0) {
    dataRange = sheet.getRange(firstDataRowActual, startColumn, numDataRows, headers.length);
    dataRange.setValues(data);
    dataRange.setHorizontalAlignment("left");
    dataRange.setVerticalAlignment("middle");
  } else {
    firstDataRowActual = headerRow;
  }

  const tableContentRows = 1 + numDataRows;
  const tableContentRange = sheet.getRange(headerRow, startColumn, tableContentRows, numHeaderCols);
  tableContentRange.setBorder(
    true, // top
    true, // left
    true, // bottom
    true, // right
    true, // vertical (inner)
    true, // horizontal (inner)
    "#b7b7b7", // Border color
    SpreadsheetApp.BorderStyle.SOLID_MEDIUM
  );

  const lastRowForThisTable = titleRow + 1 + numDataRows;
  return lastRowForThisTable;
}

function updateStatus(status) {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = spreadsheet.getSheetByName(BAMBI_CONFIG_SHEET);

  sheet.getRange(LAST_STATUS_ROW_IDX, LAST_STATUS_COL_IDX).setValue(status);
}
