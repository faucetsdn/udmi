package com.google.udmi.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interact with Google Sheets.
 */
public class SpreadsheetManager {

  static final List<String> DEFAULT_SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
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

  private final String applicationName;
  private final Sheets sheetsService;
  private final String spreadSheetId;

  /**
   * Manages a spreadsheet.
   *
   * @param applicationName name of the application accessing the spreadsheet
   * @param spreadSheetId alphanumeric id of a spreadsheet obtained from its URL
   * @param scopes OAuth 2.0 scopes for use with the Google Sheets API.
   * @throws IOException if an error occurs while communicating with the Google Sheets API.
   */
  public SpreadsheetManager(String applicationName, String spreadSheetId, List<String> scopes)
      throws IOException {
    this.applicationName = applicationName;
    this.spreadSheetId = spreadSheetId;
    this.sheetsService = createSheetsService(scopes);
  }

  /**
   * Manages a spreadsheet.
   *
   * @param applicationName name of the application accessing the spreadsheet
   * @param spreadSheetId alphanumeric id of a spreadsheet obtained from its URL
   * @throws IOException if an error occurs while communicating with the Google Sheets API.
   */
  public SpreadsheetManager(String applicationName, String spreadSheetId) throws IOException {
    this(applicationName, spreadSheetId, DEFAULT_SCOPES);
  }

  /**
   * A constructor only for tests - does not use gcloud credentials.
   */
  @VisibleForTesting
  public SpreadsheetManager(String testAppName, String spreadSheetId, Sheets mockSheetsService) {
    this.applicationName = testAppName;
    this.spreadSheetId = spreadSheetId;
    this.sheetsService = mockSheetsService;
  }

  private Sheets createSheetsService(List<String> scopes) throws IOException {
    GoogleCredentials credential =
        GoogleCredentials.getApplicationDefault().createScoped(scopes);
    return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credential))
        .setApplicationName(applicationName)
        .build();
  }

  /**
   * Check if a sheet/tab with the input sheetName already exists in the spreadsheet.
   *
   * @param sheetName name of the sheet
   * @return true if sheet exists, false otherwise
   * @throws IOException if an error occurs while communicating with the Google Sheets API.
   */
  public boolean checkSheetExists(String sheetName) throws IOException {
    Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadSheetId).execute();
    List<Sheet> sheets = spreadsheet.getSheets();
    return sheets != null && sheets.stream()
        .anyMatch(sheet -> sheet.getProperties().getTitle().equalsIgnoreCase(sheetName));
  }

  /**
   * Adds a new sheet/tab to the spreadsheet if it does not already exist.
   *
   * @param sheetName name of the sheet to be created
   * @throws IOException if an error occurs while communicating with the Google Sheets API.
   */
  public void addNewSheet(String sheetName) throws IOException {
    if (checkSheetExists(sheetName)) {
      LOGGER.info("Skipping addNewSheet, sheet already exists with name: " + sheetName);
      return;
    }
    BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
        .setRequests(List.of(new Request().setAddSheet(new AddSheetRequest()
            .setProperties(new SheetProperties().setTitle(sheetName)))));
    try {
      sheetsService.spreadsheets().batchUpdate(spreadSheetId, body).execute();
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
      sheetsService.spreadsheets().values()
          .append(spreadSheetId, range, body)
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
      sheetsService.spreadsheets().values()
          .update(spreadSheetId, range, body)
          .setValueInputOption("RAW")
          .execute();
      LOGGER.info("successfully wrote {} rows to range: {}", values.size(), range);
    } catch (IOException e) {
      throw new IOException("Failed to write data to range: " + range, e);
    }
  }

  /**
   * Clear value from a range in spreadsheet.
   *
   * @param range the A1 notation of the range to write to (e.g., "Sheet1!A1:C3" or simply "Sheet1")
   * @throws IOException If an error occurs while communicating with the Google Sheets API.
   */
  public void clearValuesFromRange(String range) throws IOException {
    try {
      sheetsService.spreadsheets().values()
          .clear(spreadSheetId, range, new ClearValuesRequest()).execute();
    } catch (IOException e) {
      throw new IOException("Could not clear range " + range, e);
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
    LOGGER.info("fetching records from sheet {}", sheetName);
    try {
      ValueRange response = sheetsService.spreadsheets().values()
          .get(spreadSheetId, sheetName)
          .execute();
      List<List<Object>> values = response.getValues();
      if (values == null || values.isEmpty()) {
        LOGGER.info("no data found in sheet: {}", sheetName);
        return Collections.emptyList();
      }
      return values;
    } catch (IOException e) {
      LOGGER.error("failed to fetch data from sheet: {}", sheetName, e);
      throw new IOException("failed to fetch data from sheet: " + sheetName, e);
    }
  }

}


