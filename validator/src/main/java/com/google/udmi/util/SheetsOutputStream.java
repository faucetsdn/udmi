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
import java.io.BufferedReader;
import java.io.IOException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Generic utility to log messages to Google Sheets. This class extends {@link OutputStream} and
 * redirects output to a specified sheet in a Google Spreadsheet.
 */
public class SheetsOutputStream extends OutputStream {

  private static final Logger LOGGER = LoggerFactory.getLogger(SheetsOutputStream.class);
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
  private static final long DEFAULT_SYNC_TIME = 2000;
  private static final NetHttpTransport HTTP_TRANSPORT;

  static {
    try {
      HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new ExceptionInInitializerError("Error initializing HTTP Transport: " + e.getMessage());
    }
  }

  final long syncTime;
  private long lastWriteMillis = 0;
  private final String applicationName;
  private final String spreadsheetId;
  private final String outputSheetTitle;
  private final Sheets sheetsService;
  final StringBuilder buffer = new StringBuilder();
  private PrintStream originalSystemOut;
  private PrintStream originalSystemErr;

  /**
   * Constructs a new `GSheetsOutputStream` with default sync time.
   *
   * @param applicationName The name of the application using the Google Sheets API.
   * @param spreadsheetId The ID of the Google Spreadsheet.
   * @param outputSheetTitle The title of the sheet where the output will be written.
   * @throws IOException If there's an error creating the Google Sheets service or adding the sheet.
   */
  public SheetsOutputStream(String applicationName, String spreadsheetId, String outputSheetTitle)
      throws IOException {
    this(applicationName, spreadsheetId, outputSheetTitle, DEFAULT_SYNC_TIME);
  }

  /**
   * Constructs a new `GSheetsOutputStream`.
   *
   * @param applicationName The name of the application using the Google Sheets API.
   * @param spreadsheetId The ID of the Google Spreadsheet.
   * @param outputSheetTitle The title of the sheet where the output will be written.
   * @param syncTime The time in milliseconds to wait before syncing the buffer to the sheet.
   * @throws IOException If there's an error creating the Google Sheets service or adding the sheet.
   */
  public SheetsOutputStream(String applicationName, String spreadsheetId, String outputSheetTitle,
      long syncTime)
      throws IOException {
    this.applicationName = applicationName;
    this.spreadsheetId = spreadsheetId;
    this.outputSheetTitle = outputSheetTitle;
    this.sheetsService = createSheetsService();
    this.syncTime = syncTime;
    addOutputSheet();
  }

  @Override
  public void write(int i) {
    buffer.append((char) i);
    syncIfNeeded();
  }

  @Override
  public void write(byte @NotNull [] b, int off, int len) {
    buffer.append(new String(b, off, len));
    syncIfNeeded();
  }

  private void syncIfNeeded() {
    long currentTimeMillis = Instant.now().toEpochMilli();
    if (buffer.indexOf("\n") != -1 && currentTimeMillis - lastWriteMillis >= syncTime) {
      lastWriteMillis = currentTimeMillis;
      appendToSheet();
    }
  }

  void appendToSheet() {
    String content = buffer.toString();
    if (content.trim().isEmpty()) {
      buffer.setLength(0); // Clear buffer even if nothing to write
      return;
    }
    try {
      List<List<Object>> values = Arrays.stream(content.split("\\n"))
          .filter(line -> !line.trim().isEmpty()) // Filter out empty lines
          .map(line -> Collections.singletonList((Object) line))
          .collect(Collectors.toList());

      if (!values.isEmpty()) {
        ValueRange appendBody = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values()
            .append(spreadsheetId, outputSheetTitle, appendBody)
            .setValueInputOption("RAW").execute();
      }
      buffer.setLength(0);
    } catch (IOException e) {
      LOGGER.error("Error appending to sheet", e);
    }
  }

  Sheets createSheetsService() throws IOException {
    GoogleCredentials credential =
        GoogleCredentials.getApplicationDefault().createScoped(SCOPES);
    return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credential))
        .setApplicationName(this.applicationName)
        .build();
  }


  private boolean outputSheetExists() throws IOException {
    Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
    return spreadsheet.getSheets().stream()
        .anyMatch(sheet -> sheet.getProperties().getTitle().equalsIgnoreCase(outputSheetTitle));
  }

  private void addOutputSheet() throws IOException {
    if (!outputSheetExists()) {
      BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest()
          .setRequests(List.of(new Request().setAddSheet(new AddSheetRequest()
              .setProperties(new SheetProperties().setTitle(outputSheetTitle)))));
      try {
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, body).execute();
      } catch (IOException e) {
        throw new IOException("Failed to add output sheet: " + outputSheetTitle, e);
      }
    }
  }

  /**
   * Redirects `System.out` and `System.err` to google sheets.
   */
  public void startStream() {
    if (originalSystemOut == null) {
      originalSystemOut = System.out;
      originalSystemErr = System.err;
      DualOutputStream tee = new DualOutputStream(originalSystemOut, this);
      PrintStream printStream = new PrintStream(tee);
      System.setOut(printStream);
      System.setErr(printStream);
    }
  }

  /**
   * Restores `System.out` and `System.err` to their original streams.
   */
  public void stopStream() {
    if (originalSystemOut != null) {
      System.setOut(originalSystemOut);
      System.setErr(originalSystemErr);
      originalSystemOut = null;
      originalSystemErr = null;

      // flush out
      appendToSheet();
    }
  }

  /**
   * Redirects standard input to Google Sheet output.
   */
  public void startStreamFromInput() {
    try (
        DualOutputStream tee = new DualOutputStream(System.out, this);
        PrintStream printStream = new PrintStream(tee);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))
    ) {
      String line;
      while ((line = reader.readLine()) != null) {
        printStream.println(line);
      }
    } catch (IOException e) {
      throw new RuntimeException("Exception while reading input", e);
    } finally {
      // added to ensure last batch is always appended & to prevent loss in case of exceptions
      appendToSheet();
    }
  }

  /**
   * Main method for the class. Can be used to start the logger from command line.
   *
   * @param args Command line arguments. Required: applicationName, spreadsheetId, sheetTitle.
   *     Optional: syncTime.
   */
  public static void main(String[] args) {
    if (args.length < 3 || args.length > 4) {
      System.err.println(
          "Usage: GSheetsOutputStream <applicationName> <spreadsheetId> <sheetTitle> [<syncTime>]");
      System.exit(1);
    }

    String applicationName = args[0];
    String spreadsheetId = args[1];
    String sheetTitle = args[2];
    long syncTime = DEFAULT_SYNC_TIME;

    if (args.length == 4) {
      try {
        syncTime = Long.parseLong(args[3]);
        if (syncTime <= 0) {
          System.err.println("Sync time should be greater than zero");
          System.exit(1);
        }
      } catch (NumberFormatException e) {
        System.err.println("Invalid sync time format: " + args[3]);
        System.exit(1);
      }
    }

    try (SheetsOutputStream sheetsOutputStream =
        new SheetsOutputStream(applicationName, spreadsheetId, sheetTitle, syncTime)) {
      sheetsOutputStream.startStreamFromInput();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}