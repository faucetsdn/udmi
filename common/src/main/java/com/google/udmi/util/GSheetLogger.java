package com.google.udmi.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

public class GSheetLogger {
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final List<String> SCOPES =
      Collections.singletonList(SheetsScopes.SPREADSHEETS);
  private final String applicationName;
  private final String spreadsheetId;
  private final String outputSheetTitle;
  private final Sheets sheetsService;

  public GSheetLogger(String applicationName, String spreadsheetId, String outputSheetTitle)
      throws GeneralSecurityException, IOException {
    this.applicationName = applicationName;
    this.spreadsheetId = spreadsheetId;
    this.outputSheetTitle = outputSheetTitle;
    this.sheetsService = createSheetsService();
  }

  private Sheets createSheetsService() throws GeneralSecurityException, IOException {
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials())
        .setApplicationName(this.applicationName)
        .build();
  }

  private static HttpRequestInitializer getCredentials() throws IOException {
    GoogleCredentials credential = GoogleCredentials.getApplicationDefault().createScoped(SCOPES);
    return new HttpCredentialsAdapter(credential);
  }


  public void log(String message) throws IOException {
    addNewSheet(this.outputSheetTitle);

    String range = this.outputSheetTitle + "!A1"; // Append to A1 of the new sheet
    ValueRange body = new ValueRange()
        .setValues(List.of(List.of(message)));
    AppendValuesResponse result = sheetsService.spreadsheets().values()
        .append(spreadsheetId, range, body)
        .setValueInputOption("RAW")
        .setInsertDataOption("INSERT_ROWS")
        .execute();

    System.out.printf("%d cells appended.", result.getUpdates().getUpdatedCells());
  }

  private void addNewSheet(String sheetTitle) throws IOException {
    BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
        .setRequests(List.of(new Request().setAddSheet(new AddSheetRequest()
            .setProperties(new SheetProperties().setTitle(sheetTitle)))));
    sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
  }

  public static void main(String[] args) throws IOException, GeneralSecurityException {
    if (args.length != 4) {
      System.err.println("Usage: GSheetLogger <applicationName> <spreadsheetId> <sheetTitle> <message>");
      System.exit(1);
    }

    String applicationName = args[0];
    String spreadsheetId = args[1];
    String sheetTitle = args[2];
    String message = args[3];

    GSheetLogger logger = new GSheetLogger(applicationName, spreadsheetId, sheetTitle);
    logger.log(message);
  }
}