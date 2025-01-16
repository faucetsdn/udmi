package com.google.udmi.util;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;


/**
 * Generic utility to log messages to gSheets. It uses OAuth Client IDs and seeks user consent.
 */
public class GSheetsOutputStream extends OutputStream {

  private static final String LOCAL_CREDENTIALS_FILE_PATH =
      System.getProperty("user.home") + "/credentials.json";
  private static final String LOCAL_TOKENS_DIRECTORY_PATH =
      System.getProperty("user.home") + "/tokens";
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

  private static long lastWriteMillis = 0;

  private final String applicationName;
  private final String spreadsheetId;
  private final String outputSheetTitle;
  private final Sheets sheetsService;
  private final StringBuilder buffer;

  public GSheetsOutputStream(String applicationName, String spreadsheetId, String outputSheetTitle)
      throws GeneralSecurityException, IOException {
    this.applicationName = applicationName;
    this.spreadsheetId = spreadsheetId;
    this.outputSheetTitle = outputSheetTitle;
    this.sheetsService = createSheetsService();
    this.buffer = new StringBuilder();
    addOutputSheet();
  }

  @Override
  public void write(int i) {
    buffer.append((char) i);
    long currentTimeMillis = Instant.now().toEpochMilli();
    if ((char) i == '\n' && currentTimeMillis - lastWriteMillis >= 2000) {
      lastWriteMillis = currentTimeMillis;
      appendToSheet();
    }
  }

  @Override
  public void write(byte @NotNull [] b, int off, int len) {
    buffer.append(new String(b, off, len));
    long currentTimeMillis = Instant.now().toEpochMilli();

    if (buffer.indexOf("\n") != -1 && currentTimeMillis - lastWriteMillis >= 2000) {
      lastWriteMillis = currentTimeMillis;
      appendToSheet();
    }
  }

  private void appendToSheet() {
    try {
      if (buffer.length() > 0) {
        ValueRange appendBody = new ValueRange().setValues(
                Arrays.stream(buffer.toString().trim().split("\\n"))
                    .map(line -> Collections.singletonList((Object) line))
                    .collect(Collectors.toList())
        );

        sheetsService.spreadsheets().values().append(spreadsheetId, outputSheetTitle, appendBody)
            .setValueInputOption("RAW").execute();
        buffer.setLength(0);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private Sheets createSheetsService() throws GeneralSecurityException, IOException {
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
        .setApplicationName(this.applicationName)
        .build();
  }

  private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
      throws IOException {
    // Load client secrets.
    InputStream in = new FileInputStream(LOCAL_CREDENTIALS_FILE_PATH);
    GoogleClientSecrets clientSecrets =
        GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

    // Build flow and trigger user authorization request.
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
        .setDataStoreFactory(
            new FileDataStoreFactory(new java.io.File(LOCAL_TOKENS_DIRECTORY_PATH)))
        .setAccessType("offline")
        .build();
    LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
    return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
  }

  private boolean outputSheetExists() throws IOException {
    Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
    List<Sheet> sheets = spreadsheet.getSheets();
    for (Sheet sheet : sheets) {
      if (sheet.getProperties().getTitle().equalsIgnoreCase(outputSheetTitle)) {
        return true;
      }
    }
    return false;
  }

  private void addOutputSheet() throws IOException {
    if (!outputSheetExists()) {
      BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
          .setRequests(List.of(new Request().setAddSheet(new AddSheetRequest()
              .setProperties(new SheetProperties().setTitle(outputSheetTitle)))));
      sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
    }
  }

  public void startStream() {
    DualOutputStream tee = new DualOutputStream(System.out, this);
    PrintStream printStream = new PrintStream(tee);
    System.setOut(printStream);
    System.setErr(printStream);
  }

  public static void main(String[] args) {
    if (args.length != 3) {
      System.err.println(
          "Usage: GSheetsOutputStream <applicationName> <spreadsheetId> <sheetTitle>");
      System.exit(1);
    }

    String applicationName = args[0];
    String spreadsheetId = args[1];
    String sheetTitle = args[2];

    try {
      GSheetsOutputStream logger = new GSheetsOutputStream(applicationName, spreadsheetId,
          sheetTitle);
      DualOutputStream tee = new DualOutputStream(System.out, logger);
      PrintStream printStream = new PrintStream(tee);

      // If there is piped input, use it, otherwise use standard input
      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

      String line;
      while ((line = reader.readLine()) != null) {
        printStream.println(line);
      }

      logger.appendToSheet();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}