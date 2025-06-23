package com.google.udmi.util;

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Generic utility to log messages to Google Sheets. This class extends {@link OutputStream} and
 * redirects output to a specified sheet in a Google Spreadsheet.
 */
public class SheetsOutputStream extends OutputStream implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(SheetsOutputStream.class);
  private static final long DEFAULT_SYNC_TIME = 2000;
  final long syncTime;
  private long lastWriteMillis = 0;
  private final String outputSheetTitle;
  private final SpreadsheetManager spreadsheetManager;
  final StringBuilder buffer = new StringBuilder();
  PrintStream originalSystemOut;
  PrintStream originalSystemErr;

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
    this.outputSheetTitle = outputSheetTitle;
    this.spreadsheetManager = new SpreadsheetManager(applicationName, spreadsheetId);
    this.syncTime = syncTime;
    this.spreadsheetManager.addNewSheet(outputSheetTitle);
  }

  /**
   * A constructor only for tests - does not use gcloud credentials.
   */
  @VisibleForTesting
  public SheetsOutputStream(SpreadsheetManager mockSpreadsheetManager, String outputSheetTitle,
      long syncTime) throws IOException {
    this.outputSheetTitle = outputSheetTitle;
    this.spreadsheetManager = mockSpreadsheetManager;
    this.syncTime = syncTime;
    this.spreadsheetManager.addNewSheet(outputSheetTitle);
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

  public void appendToSheet() {
    String content = buffer.toString();
    if (content.trim().isEmpty()) {
      buffer.setLength(0); // Clear buffer even if nothing to write
      return;
    }
    try {
      List<List<Object>> values = Arrays.stream(content.split("\\n"))
          .filter(line -> !line.trim().isEmpty()) // Filter out empty lines
          .map(line -> Collections.singletonList((Object) line))
          .toList();

      if (!values.isEmpty()) {
        spreadsheetManager.appendToSheet(outputSheetTitle, values);
      }
      buffer.setLength(0);
    } catch (IOException e) {
      LOGGER.error("Error appending to sheet", e);
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

  @Override
  public void close()  {
    stopStream();
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