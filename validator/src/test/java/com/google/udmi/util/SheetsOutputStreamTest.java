package com.google.udmi.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.Sheets.Spreadsheets;
import com.google.api.services.sheets.v4.Sheets.Spreadsheets.BatchUpdate;
import com.google.api.services.sheets.v4.Sheets.Spreadsheets.Get;
import com.google.api.services.sheets.v4.Sheets.Spreadsheets.Values;
import com.google.api.services.sheets.v4.Sheets.Spreadsheets.Values.Append;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SheetsOutputStreamTest {

  private final Append mockAppend = mock(Append.class);
  private final AppendValuesResponse mockAppendValuesResponse = mock(AppendValuesResponse.class);
  private final BatchUpdate mockBatchUpdate = mock(BatchUpdate.class);
  private final BatchUpdateSpreadsheetResponse mockBatchUpdateResponse = mock(
      BatchUpdateSpreadsheetResponse.class);
  private final Get mockSpreadsheetsGet = mock(Get.class);
  private final Sheet mockSheet = new Sheet();
  private final Sheets mockSheetsService = mock(Sheets.class);
  private final Spreadsheet mockSpreadSheet = mock(Spreadsheet.class);
  private final Spreadsheets mockSpreadsheets = mock(Spreadsheets.class);
  private final Values mockValues = mock(Values.class);

  private final String applicationName = "TestApp";
  private final String spreadsheetId = "testSpreadsheetId";
  private final String outputSheetTitle = "TestSheet";
  private final long syncTime = 2000;
  private SheetsOutputStream sheetsOutputStream;


  @Before
  public void setup() throws IOException {
    mockSheet.setProperties(
        new SheetProperties().setTitle(outputSheetTitle)
    );

    when(mockSheetsService.spreadsheets()).thenReturn(mockSpreadsheets);
    when(mockSpreadsheets.get(anyString())).thenReturn(mockSpreadsheetsGet);
    when(mockSpreadsheetsGet.execute()).thenReturn(mockSpreadSheet);
    when(mockSpreadSheet.getSheets()).thenReturn(new ArrayList<Sheet>(List.of(mockSheet)));
    when(mockSpreadsheets.batchUpdate(anyString(),
        any(BatchUpdateSpreadsheetRequest.class))).thenReturn(mockBatchUpdate);
    when(mockBatchUpdate.execute()).thenReturn(mockBatchUpdateResponse);
    when(mockSpreadsheets.values()).thenReturn(mockValues);
    when(mockValues.append(any(), any(), any())).thenReturn(mockAppend);
    when(mockAppend.setValueInputOption(anyString())).thenReturn(mockAppend);
    when(mockAppend.execute()).thenReturn(mockAppendValuesResponse);
    sheetsOutputStream = new SheetsOutputStream(applicationName, spreadsheetId, outputSheetTitle,
        syncTime) {
      @Override
      Sheets createSheetsService() {
        return mockSheetsService;
      }
    };
  }

  @Test
  public void testConstructorWithDefaultSyncTime() throws IOException {
    SheetsOutputStream stream = new SheetsOutputStream(applicationName, spreadsheetId,
        outputSheetTitle) {
      @Override
      Sheets createSheetsService() {
        return mockSheetsService;
      }
    };
    assertNotNull(stream);
  }

  @Test
  public void testConstructorWithCustomSyncTime() {
    assertNotNull(sheetsOutputStream);
    assertEquals(syncTime, sheetsOutputStream.syncTime);
  }


  @Test
  public void testWriteSingleCharacter() throws IOException {
    sheetsOutputStream.startStream();
    sheetsOutputStream.write('A');
    assertEquals(sheetsOutputStream.buffer.toString(), "A");
    sheetsOutputStream.stopStream();

    // verify stream was appended to the sheet
    verify(mockValues, times(1)).append(any(), any(), any());
  }


  @Test
  public void testWriteMultipleCharacters() throws IOException {
    sheetsOutputStream.startStream();
    String testString = "Hello World!";
    sheetsOutputStream.write(testString.getBytes(), 0, testString.length());
    assertEquals(testString, sheetsOutputStream.buffer.toString());
    sheetsOutputStream.stopStream();

    // verify stream was appended to the sheet
    verify(mockValues, times(1)).append(any(), any(), any());
  }

  @Test
  public void testWriteMultiLineString() throws IOException {
    sheetsOutputStream.startStream();
    String testString = "First line.\nSecond line.\nThird line.";
    sheetsOutputStream.write(testString.getBytes(), 0, testString.length());

    // verify stream was appended to the sheet
    ArgumentCaptor<ValueRange> argumentCaptor = ArgumentCaptor.forClass(ValueRange.class);
    verify(mockValues, times(1)).append(eq(spreadsheetId), eq(outputSheetTitle),
        argumentCaptor.capture());
    ValueRange capturedValue = argumentCaptor.getValue();
    assertEquals(3, capturedValue.getValues().size());
    assertEquals("First line.", capturedValue.getValues().get(0).get(0));
    assertEquals("Second line.", capturedValue.getValues().get(1).get(0));
    assertEquals("Third line.", capturedValue.getValues().get(2).get(0));

    // buffer is emptied after appending to the sheet
    assertEquals("", sheetsOutputStream.buffer.toString());

    sheetsOutputStream.stopStream();
  }

  @Test
  public void testAppendToSheetEmptyContent() throws IOException {
    sheetsOutputStream.buffer.append("  \n  \n"); // Whitespace and empty lines
    sheetsOutputStream.appendToSheet();

    // empty content is not appended to the sheet
    verify(mockValues, never()).append(any(), any(), any());
    assertEquals("", sheetsOutputStream.buffer.toString());
  }

  @Test
  public void testAddSheetIfNotExist() throws IOException {
    when(mockSpreadsheetsGet.execute()).thenReturn(
        new Spreadsheet().setSheets(Collections.emptyList()));
    SheetsOutputStream outputStream =
        new SheetsOutputStream(applicationName, spreadsheetId, outputSheetTitle, syncTime) {
          @Override
          Sheets createSheetsService() {
            return mockSheetsService;
          }
        };
    verify(mockBatchUpdate, times(1)).execute();
  }

  @Test
  public void testAddSheetFails() throws IOException {
    when(mockSpreadsheetsGet.execute()).thenReturn(
        new Spreadsheet().setSheets(Collections.emptyList()));
    when(mockBatchUpdate.execute()).thenThrow(new IOException("Failed to add sheet"));
    assertThrows(
        IOException.class,
        () -> new SheetsOutputStream(applicationName, spreadsheetId, outputSheetTitle,
            syncTime) {
          @Override
          Sheets createSheetsService() {
            return mockSheetsService;
          }
        });
  }


  @Test
  public void testSheetExists() throws IOException {
    SheetProperties sheetProperties = new SheetProperties().setTitle(outputSheetTitle);
    Sheet sheet = new Sheet().setProperties(sheetProperties);
    when(mockSpreadsheetsGet.execute()).thenReturn(
        new Spreadsheet().setSheets(Collections.singletonList(sheet)));
    SheetsOutputStream outputStream =
        new SheetsOutputStream(applicationName, spreadsheetId, outputSheetTitle, syncTime) {
          @Override
          Sheets createSheetsService() {
            return mockSheetsService;
          }
        };
    verify(mockBatchUpdate, never()).execute();
  }


  @Test
  public void testStartAndStopStream() throws IOException {
    SheetsOutputStream outputStream =
        new SheetsOutputStream(applicationName, spreadsheetId, outputSheetTitle, syncTime) {
          @Override
          Sheets createSheetsService() {
            return mockSheetsService;
          }
        };
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    outputStream.startStream();
    assertNotEquals(originalOut, System.out);
    assertNotEquals(originalErr, System.err);
    String testString = "Test output";
    System.out.println(testString);
    outputStream.stopStream();
    assertEquals(originalOut, System.out);
    assertEquals(originalErr, System.err);
  }

}