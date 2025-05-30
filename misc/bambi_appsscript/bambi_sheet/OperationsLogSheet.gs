function createOperationLogSheet() {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  sheet = spreadsheet.insertSheet(OPERATIONS_LOG_SHEET);
  sheet.getRange(1, 1, 1, 2).setValues([["operation", "timestamp"]]);
  sheet.getRange(1, 1, 1, 2).setFontWeight("bold");
  sheet.setColumnWidth(1, 300);
  sheet.setColumnWidth(2, 300);
  logOperation(OperationType.OPERATIONS_LOG_SHEET_ADDED);
}

function createOperationLogsSheetOnOpen() {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  const sheetName = OPERATIONS_LOG_SHEET;

  let sheet = spreadsheet.getSheetByName(sheetName);
  if (!sheet) {
    createOperationLogSheet();
  } else {
    return;
  }
}

function overwriteOperationLogsSheet() {
  const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  const sheetName = OPERATIONS_LOG_SHEET;

  let sheet = spreadsheet.getSheetByName(sheetName);
  if (sheet) {
    const ui = SpreadsheetApp.getUi();
    const response = ui.alert(
      'The sheet "' + sheetName + '" already exists. Do you want to overwrite it?',
      ui.ButtonSet.YES_NO
    );
    if (response == ui.Button.YES) {
      spreadsheet.deleteSheet(sheet);
      createOperationLogSheet();
    } else {
      return;
    }
  } else {
    createOperationLogSheet();
  }
}

function logOperation(operationName) {
  try{
    const spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
    const sheet = spreadsheet.getSheetByName(OPERATIONS_LOG_SHEET);

    if (!sheet) {
      console.error(`Sheet '${sheetName}' not found!`);
      SpreadsheetApp.getUi().alert(`Sheet '${sheetName}' not found! Please add using the BAMBI menu`, SpreadsheetApp.getUi().ButtonSet.OK);
      return;
    }

    const lastRow = sheet.getLastRow();
    const nextRow = lastRow + 1;
    console.log(`Writing to row ${nextRow}`);
    const timestamp = new Date();
    const rowData = [operationName, timestamp];
    sheet.getRange(nextRow, 1, 1, rowData.length).setValues([rowData]);
    sheet.getRange(nextRow, 2).setNumberFormat('yyyy-MM-dd HH:mm:ss');

    console.log(`Logged operation: '${operationName}' at ${timestamp} to row ${nextRow}`);
    if (spreadsheet.getSheetByName(BAMBI_CONFIG_SHEET)) {
      updateStatus(operationName + ` [${timestamp}]`);
    }
  } catch (error) {
    console.error('An error occurred while logging the operation:', error);
    SpreadsheetApp.getUi().alert('An error occurred while logging the operation. Check the script editor for details.', SpreadsheetApp.getUi().ButtonSet.OK);
  }
}
