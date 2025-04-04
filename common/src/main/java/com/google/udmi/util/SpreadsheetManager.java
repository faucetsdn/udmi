package com.google.udmi.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interact with Google Sheets
 */
public class SpreadsheetManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(SpreadsheetManager.class);
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final NetHttpTransport HTTP_TRANSPORT;

  static {
    try {
      HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new ExceptionInInitializerError("Error initializing HTTP Transport: " + e.getMessage());
    }
  }

  static final List<String> DEFAULT_SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
  private final String applicationName;
  private final Sheets sheetsService;
  private final String spreadSheetId;

  public SpreadsheetManager(String applicationName, String spreadSheetId, List<String> scopes)
      throws IOException {
    this.applicationName = applicationName;
    this.spreadSheetId = spreadSheetId;
    this.sheetsService = createSheetsService(scopes);
  }

  public SpreadsheetManager(String applicationName, String spreadSheetId) throws IOException {
    this(applicationName, spreadSheetId, DEFAULT_SCOPES);
  }

  private Sheets createSheetsService(List<String> scopes) throws IOException {
    GoogleCredentials credential =
        GoogleCredentials.getApplicationDefault().createScoped(scopes);
    return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credential))
        .setApplicationName(this.applicationName)
        .build();
  }

  public boolean checkSheetExists(String sheetName) throws IOException {
    Spreadsheet spreadsheet = this.sheetsService.spreadsheets().get(this.spreadSheetId).execute();
    return spreadsheet.getSheets().stream()
        .anyMatch(sheet -> sheet.getProperties().getTitle().equalsIgnoreCase(sheetName));
  }

  public void addNewSheet(String sheetName) throws IOException {
    if (checkSheetExists(sheetName)) {
      LOGGER.info("Skipping addNewSheet, sheet already exists with name: " + sheetName);
    }
    BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
        .setRequests(List.of(new Request().setAddSheet(new AddSheetRequest()
            .setProperties(new SheetProperties().setTitle(sheetName)))));
    try {
      this.sheetsService.spreadsheets().batchUpdate(this.spreadSheetId, body).execute();
    } catch (IOException e) {
      throw new IOException("Failed to add output sheet: " + sheetName, e);
    }
  }

  /**
   * Appends a new row of values to the end of the specified sheet, starting from the given column.
   *
   * @param sheetName The name of the sheet to append to.
   * @param startColumn The letter of the column to start appending from (e.g., "A", "B").
   * @param values A list of lists of objects representing the data to write. Each inner list
   *     represents a row, and the objects within are converted to their string representation.
   * @throws IOException If an error occurs while communicating with the Google Sheets API.
   */
  public void appendToSheet(String sheetName, String startColumn, List<List<Object>> values)
      throws IOException {
    String range = sheetName + "!" + startColumn + ":" + startColumn;
    ValueRange body = new ValueRange().setValues(values);
    try {
      this.sheetsService.spreadsheets().values()
          .append(this.spreadSheetId, range, body)
          .setValueInputOption("RAW")
          .execute();
    } catch (IOException e) {
      throw new IOException(
          "Failed to append data to sheet: " + sheetName + " starting from column " + startColumn,
          e);
    }
  }

  /**
   * Appends a new row of values to the end of the specified sheet (starting from column A by
   * default).
   *
   * @param sheetName The name of the sheet to append to.
   * @param values A list of lists of objects representing the data to write. Each inner list
   *     represents a row, and the objects within are converted to their string representation.
   * @throws IOException If an error occurs.
   */
  public void appendToSheet(String sheetName, List<List<Object>> values) throws IOException {
    appendToSheet(sheetName, "A", values);
  }

  /**
   * Writes a list of values to a specific range in the spreadsheet.
   *
   * @param range The A1 notation of the range to write to (e.g., "Sheet1!A1:C3").
   * @param values A list of lists of objects representing the data to write. Each inner list
   *     represents a row, and the objects within are converted to their string representation.
   * @throws IOException If an error occurs while communicating with the Google Sheets API.
   */
  public void writeToRange(String range, List<List<Object>> values) throws IOException {
    ValueRange body = new ValueRange().setValues(values);
    try {
      this.sheetsService.spreadsheets().values()
          .update(this.spreadSheetId, range, body)
          .setValueInputOption("RAW")
          .execute();
      LOGGER.info("Successfully wrote data to range: {}", range);
    } catch (IOException e) {
      throw new IOException("Failed to write data to range: " + range, e);
    }
  }

  /**
   * Fetches all records from a specified sheet.
   *
   * @param sheetName The name of the sheet to fetch data from.
   * @return A list of lists of objects representing the rows and columns of the sheet. Returns an
   *     empty list if the sheet is empty or does not exist.
   * @throws IOException If an error occurs while communicating with the Google Sheets API.
   */
  public List<List<Object>> getSheetRecords(String sheetName) throws IOException {
    try {
      ValueRange response = this.sheetsService.spreadsheets().values()
          .get(this.spreadSheetId, sheetName)
          .execute();
      List<List<Object>> values = response.getValues();
      if (values == null || values.isEmpty()) {
        LOGGER.info("No data found in sheet: {}", sheetName);
        return Collections.emptyList();
      }
      return values;
    } catch (IOException e) {
      LOGGER.error("Failed to fetch data from sheet: {}", sheetName, e);
      throw new IOException("Failed to fetch data from sheet: " + sheetName, e);
    }
  }

  public static void main(String[] args) throws IOException {
    String spreadsheetId = "1uTrepQk2eeUxHdcWOTV30xxPkEqSAOh0_uScAZq4gSs";
    String app = "test";

    SpreadsheetManager manager = new SpreadsheetManager(app, spreadsheetId);
    List<List<Object>> records = manager.getSheetRecords("site_metadata");
    records = manager.getSheetRecords("system");
  }

}


