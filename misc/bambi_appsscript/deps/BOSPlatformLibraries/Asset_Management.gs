function generateQRCodePayload() {
  // Get the active spreadsheet and selection
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const activeRange = ss.getActiveRange();

  // Check if the selection is in a single column
  if (activeRange.getNumColumns() !== 1) {
    Browser.msgBox("Please select cells in a single column.");
    return;
  }

  // Prompt the user for the source column (letter)
  const ui = SpreadsheetApp.getUi();
  const response = ui.prompt("Enter the source column letter (e.g., 'A', 'B', etc.):");

  // Get the user's input
  const sourceColumnLetter = response.getResponseText().toUpperCase();
  if (!sourceColumnLetter.match(/^[A-Z]$/)) {
    Browser.msgBox("Invalid column letter. Please enter a single letter from A to Z.");
    return;
  }

  // Get the source column index (0-based)
  const sourceColumnIndex = sourceColumnLetter.charCodeAt(0) - 65;

  // Get the selected range values
  const selectedValues = activeRange.getValues();

  // Get the source range (same rows, specified column)
  const sourceRange = activeRange.getSheet().getRange(
    activeRange.getRow(),
    sourceColumnIndex + 1,
    activeRange.getNumRows()
  );
  const sourceValues = sourceRange.getValues();

  // Generate GUIDs and concatenate with source values
  const updatedValues = selectedValues.map((row, index) => {
    const guid = Utilities.getUuid();
    const sourceValue = sourceValues[index][0]; // Get the value from the source column
    var qrCode = `{"asset":{"guid":"uuid://${guid}","name":"${sourceValue}"}}`;
    // return [`${sourceValue}_${guid}`]; // Concatenate with GUID
    return [qrCode];
  });

  // Set the updated values in the selected range
  activeRange.setValues(updatedValues);
}

function generateQRCodesForSelection() {
  // Get the active spreadsheet and selection
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const activeRange = ss.getActiveRange();

  // Validate selection: Check if only one column is selected
  if (activeRange.getNumColumns() !== 1) {
    Browser.msgBox("Please select cells in a single column.");
    return;
  }

  // Get the selected cell values
  const selectedValues = activeRange.getValues();

  // // Create a new sheet for the QR codes
  // const qrCodeSheet = ss.insertSheet("QR Codes");

  const sheetName = "QR Codes";

  // Check if a sheet with the given name already exists
  let sheet = ss.getSheetByName(sheetName);

  // If the sheet doesn't exist, create it
  if (!sheet) {
    sheet = ss.insertSheet(sheetName);
    console.log(`Sheet "${sheetName}" created.`);
  } else {
    console.log(`Sheet "${sheetName}" already exists.`);
  }

  // Generate QR codes and add them to the new sheet
  selectedValues.forEach((row, rowIndex) => {
    const cellValue = row[0];
    const qrCodeBlob = generateQRCodeBlob(cellValue);

    // Insert the QR code image into the new sheet
    qrCodeSheet.insertImage(qrCodeBlob, 1, rowIndex + 1);
  });

  // Show a success message
  Browser.msgBox("QR codes generated successfully!");
}

function generateQRCodesWithLabels() {
  // Get the active spreadsheet and selection
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const activeRange = ss.getActiveRange();

  // Validate selection: Check if only one column is selected
  if (activeRange.getNumColumns() !== 1) {
    Browser.msgBox("Please select cells in a single column.");
    return;
  }

  // Prompt the user for the source column (letter) for the label text
  const ui = SpreadsheetApp.getUi();
  const labelTextColumnResponse = ui.prompt(
    "Enter the source column letter for the label text (e.g., 'A', 'B', etc.):"
  );
  const labelTextColumnLetter = labelTextColumnResponse.getResponseText().toUpperCase();
  if (!labelTextColumnLetter.match(/^[A-Z]$/)) {
    Browser.msgBox("Invalid column letter. Please enter a single letter from A to Z.");
    return;
  }
  const labelTextColumnIndex = labelTextColumnLetter.charCodeAt(0) - 65;

  // Prompt for the folder URL to save the QR code images
  const folderUrlResponse = ui.prompt(
    "Enter the Google Drive folder URL where you want to save the QR code images:"
  );
  const folderUrl = folderUrlResponse.getResponseText();
  const folderId = getIdFromUrl(folderUrl); // Extract folder ID from URL
  if (!folderId) {
    Browser.msgBox("Invalid folder URL.");
    return;
  }

  // Get the selected range values
  const selectedValues = activeRange.getValues();

  // Get the source range for label text (same rows, specified column)
  const labelTextSourceRange = activeRange.getSheet().getRange(
    activeRange.getRow(),
    labelTextColumnIndex + 1,
    activeRange.getNumRows()
  );
  const labelTextSourceValues = labelTextSourceRange.getValues();

  // Create a new sheet for the QR codes if it doesn't exist
  const qrCodeSheet = createSheetIfNotExists(ss.getId(), "QR Codes");

  // Generate QR codes using QuickChart.io and add them to the new sheet with labels
  selectedValues.forEach((row, rowIndex) => {
    const cellValue = row[0];
    const labelText = labelTextSourceValues[rowIndex][0];
    const qrCodeUrl = generateQRCodeUrlQuickChart(cellValue, labelText);
    const qrCodeBlob = UrlFetchApp.fetch(qrCodeUrl).getBlob();

    // Insert the QR code image into the new sheet
    qrCodeSheet.insertImage(qrCodeBlob, 1, rowIndex + 1);

    // Save the QR code image to Google Drive
    const fileName = `${labelText}.png`;
    saveBlobToFile(qrCodeBlob, folderId, fileName);
  });

  // Show a success message
  Browser.msgBox("QR codes with labels generated and saved successfully!");
}

function generateQRCodeBlob(text) {
  // // Construct the QR code URL using the Charts service
  // const qrCodeUrl = `https://chart.googleapis.com/chart?chs=200x200&cht=qr&chl=${encodeURIComponent(text)}`;

  // // Fetch the QR code image as a blob
  // return UrlFetchApp.fetch(qrCodeUrl).getBlob();

  // Construct the QuickChart.io URL
  const baseUrl = "https://quickchart.io/qr";
  const params = {
    text: text,
    size: 200, // Adjust size as needed

    // Add more customization options if required (see QuickChart.io docs)
  };
  const queryString = Object.entries(params)
    .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
    .join("&");

  return `${baseUrl}?${queryString}`;
}

function generateQRCodeUrlQuickChart(text, label) {
  // Construct the QuickChart.io URL with the label
  const baseUrl = "https://quickchart.io/qr";
  const params = {
    text: text,
    size: 300, // You can adjust the size as needed
    caption: label, // Add the label as bottom text
    captionFontSize: 20,
  };
  const queryString = Object.entries(params)
    .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
    .join("&");

  return `${baseUrl}?${queryString}`;
}

function createSheetIfNotExists(spreadsheetId, sheetName) {
  // Get the spreadsheet
  const ss = SpreadsheetApp.openById(spreadsheetId);

  // Check if a sheet with the given name already exists
  let sheet = ss.getSheetByName(sheetName);

  // If the sheet doesn't exist, create it
  if (!sheet) {
    sheet = ss.insertSheet(sheetName);
  }

  return sheet; // Return the sheet object
}

function saveBlobToFile(blob, folderId, fileName) {
  try {
    // Get the folder
    const folder = DriveApp.getFolderById(folderId);

    // Create the file in the folder
    folder.createFile(blob.setName(fileName));
    console.log(`File "${fileName}" saved to folder.`);
  } catch (error) {
    console.error("Error saving file:", error);
  }
}
