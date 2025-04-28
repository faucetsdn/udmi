package com.google.udmi.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for SpreadsheetManager.java.
 */
@RunWith(MockitoJUnitRunner.class)
public class SpreadsheetManagerTest {

  private static final String TEST_APP_NAME = "TestApp";
  private static final String TEST_SPREADSHEET_ID = "testSpreadsheetId";
  private static final String TEST_SHEET_NAME = "TestSheet";
  private static final String TEST_RANGE = TEST_SHEET_NAME + "!A1:B2";
  private static final String DEFAULT_START_COLUMN = "A";

  @Mock
  private Sheets mockSheetsService;
  @Mock
  private Sheets.Spreadsheets mockSpreadsheets;
  @Mock
  private Sheets.Spreadsheets.Get mockSpreadsheetsGet;
  @Mock
  private Sheets.Spreadsheets.BatchUpdate mockSpreadsheetsBatchUpdate;
  @Mock
  private Sheets.Spreadsheets.Values mockSpreadsheetValues;
  @Mock
  private Sheets.Spreadsheets.Values.Get mockValuesGet;
  @Mock
  private Sheets.Spreadsheets.Values.Append mockValuesAppend;
  @Mock
  private Sheets.Spreadsheets.Values.Update mockValuesUpdate;

  @Captor
  private ArgumentCaptor<String> stringCaptor;
  @Captor
  private ArgumentCaptor<ValueRange> valueRangeCaptor;
  @Captor
  private ArgumentCaptor<BatchUpdateSpreadsheetRequest> batchUpdateCaptor;

  private SpreadsheetManager spreadsheetManager;

  /**
   * Setup and mock the required services.
   */
  @Before
  public void setUp() throws IOException {
    when(mockSheetsService.spreadsheets()).thenReturn(mockSpreadsheets);
    when(mockSpreadsheets.get(eq(TEST_SPREADSHEET_ID))).thenReturn(mockSpreadsheetsGet);
    when(mockSpreadsheets.batchUpdate(eq(TEST_SPREADSHEET_ID),
        any(BatchUpdateSpreadsheetRequest.class)))
        .thenReturn(mockSpreadsheetsBatchUpdate);

    when(mockSpreadsheets.values()).thenReturn(mockSpreadsheetValues);
    when(mockSpreadsheetValues.get(eq(TEST_SPREADSHEET_ID), anyString())).thenReturn(mockValuesGet);
    when(mockSpreadsheetValues.append(eq(TEST_SPREADSHEET_ID), anyString(), any(ValueRange.class)))
        .thenReturn(mockValuesAppend);
    when(mockSpreadsheetValues.update(eq(TEST_SPREADSHEET_ID), anyString(), any(ValueRange.class)))
        .thenReturn(mockValuesUpdate);

    when(mockSpreadsheetsGet.execute()).thenReturn(new Spreadsheet());
    when(mockSpreadsheetsBatchUpdate.execute()).thenReturn(new BatchUpdateSpreadsheetResponse());
    when(mockValuesGet.execute()).thenReturn(new ValueRange());
    when(mockValuesAppend.setValueInputOption(anyString())).thenReturn(mockValuesAppend);
    when(mockValuesAppend.execute()).thenReturn(new AppendValuesResponse());
    when(mockValuesUpdate.setValueInputOption(anyString())).thenReturn(mockValuesUpdate);
    when(mockValuesUpdate.execute()).thenReturn(new UpdateValuesResponse());

    spreadsheetManager = new SpreadsheetManager(TEST_APP_NAME, TEST_SPREADSHEET_ID,
        mockSheetsService);
  }

  @Test
  public void checkSheetExists_SheetFound() throws IOException {
    Sheet sheet1 = new Sheet().setProperties(new SheetProperties().setTitle("AnotherSheet"));
    Sheet sheet2 = new Sheet().setProperties(new SheetProperties().setTitle(TEST_SHEET_NAME));
    Spreadsheet spreadsheet = new Spreadsheet().setSheets(Arrays.asList(sheet1, sheet2));
    when(mockSpreadsheetsGet.execute()).thenReturn(spreadsheet);

    assertTrue(spreadsheetManager.checkSheetExists(TEST_SHEET_NAME));

    verify(mockSheetsService).spreadsheets();
    verify(mockSpreadsheets).get(TEST_SPREADSHEET_ID);
    verify(mockSpreadsheetsGet).execute();
  }

  @Test
  public void checkSheetExists_SheetFound_CaseInsensitive() throws IOException {
    Sheet sheet1 = new Sheet().setProperties(new SheetProperties().setTitle("AnotherSheet"));
    Sheet sheet2 = new Sheet().setProperties(
        new SheetProperties().setTitle(TEST_SHEET_NAME.toUpperCase()));
    Spreadsheet spreadsheet = new Spreadsheet().setSheets(Arrays.asList(sheet1, sheet2));
    when(mockSpreadsheetsGet.execute()).thenReturn(spreadsheet);

    // Check with different case
    assertTrue(spreadsheetManager.checkSheetExists(TEST_SHEET_NAME.toLowerCase()));

    verify(mockSpreadsheetsGet).execute();
  }

  @Test
  public void checkSheetExists_SheetNotFound() throws IOException {
    Sheet sheet1 = new Sheet().setProperties(new SheetProperties().setTitle("AnotherSheet"));
    Sheet sheet2 = new Sheet().setProperties(new SheetProperties().setTitle("YetAnotherSheet"));
    Spreadsheet spreadsheet = new Spreadsheet().setSheets(Arrays.asList(sheet1, sheet2));
    when(mockSpreadsheetsGet.execute()).thenReturn(spreadsheet);

    assertFalse(spreadsheetManager.checkSheetExists(TEST_SHEET_NAME));

    verify(mockSpreadsheetsGet).execute();
  }

  @Test
  public void checkSheetExists_NoSheetsInSpreadsheet() throws IOException {
    Spreadsheet spreadsheet = new Spreadsheet().setSheets(Collections.emptyList());
    when(mockSpreadsheetsGet.execute()).thenReturn(spreadsheet);

    assertFalse(spreadsheetManager.checkSheetExists(TEST_SHEET_NAME));

    verify(mockSpreadsheetsGet).execute();
  }

  @Test
  public void checkSheetExists_NullSheetsInSpreadsheet() throws IOException {
    Spreadsheet spreadsheet = new Spreadsheet().setSheets(null); // API might return null
    when(mockSpreadsheetsGet.execute()).thenReturn(spreadsheet);

    assertFalse(spreadsheetManager.checkSheetExists(TEST_SHEET_NAME));

    verify(mockSpreadsheetsGet).execute();
  }


  @Test(expected = IOException.class)
  public void checkSheetExists_ApiThrowsException() throws IOException {
    IOException expectedException = new IOException("API Error");
    when(mockSpreadsheetsGet.execute()).thenThrow(expectedException);

    // This call is expected to throw
    // Verification happens implicitly if exception is thrown correctly
    spreadsheetManager.checkSheetExists(TEST_SHEET_NAME);
  }

  @Test
  public void addNewSheet_SheetDoesNotExist() throws IOException {
    SpreadsheetManager spyManager = Mockito.spy(spreadsheetManager);

    doReturn(false).when(spyManager).checkSheetExists(TEST_SHEET_NAME);

    // Act
    spyManager.addNewSheet(TEST_SHEET_NAME);

    // Assert
    verify(spyManager).checkSheetExists(TEST_SHEET_NAME);
    verify(mockSheetsService).spreadsheets();
    verify(mockSpreadsheets).batchUpdate(eq(TEST_SPREADSHEET_ID), batchUpdateCaptor.capture());
    verify(mockSpreadsheetsBatchUpdate).execute();

    // Verify the request payload
    BatchUpdateSpreadsheetRequest capturedRequest = batchUpdateCaptor.getValue();
    assertNotNull(capturedRequest);
    assertEquals(1, capturedRequest.getRequests().size());
    Request addSheetReq = capturedRequest.getRequests().get(0);
    assertNotNull(addSheetReq.getAddSheet());
    assertEquals(TEST_SHEET_NAME, addSheetReq.getAddSheet().getProperties().getTitle());
  }

  @Test
  public void addNewSheet_SheetAlreadyExists() throws IOException {
    SpreadsheetManager spyManager = Mockito.spy(spreadsheetManager);
    doReturn(true).when(spyManager).checkSheetExists(TEST_SHEET_NAME);

    // Act
    spyManager.addNewSheet(TEST_SHEET_NAME);

    // Assert
    verify(spyManager).checkSheetExists(TEST_SHEET_NAME);

    // Verify batchUpdate was never called on the underlying service
    verify(mockSpreadsheets, never()).batchUpdate(anyString(),
        any(BatchUpdateSpreadsheetRequest.class));
    verify(mockSpreadsheetsBatchUpdate, never()).execute();
  }

  @Test
  public void addNewSheet_CheckSheetThrowsException() throws IOException {
    SpreadsheetManager spyManager = Mockito.spy(spreadsheetManager);
    IOException checkException = new IOException("Check failed");
    doThrow(checkException).when(spyManager).checkSheetExists(TEST_SHEET_NAME);

    try {
      spyManager.addNewSheet(TEST_SHEET_NAME);
      fail("Expected IOException was not thrown");
    } catch (IOException actualException) {
      assertSame("Expected the specific exception from checkSheetExists", checkException,
          actualException);
    }
    verify(mockSpreadsheets, never()).batchUpdate(anyString(),
        any(BatchUpdateSpreadsheetRequest.class));
  }

  @Test
  public void addNewSheet_BatchUpdateThrowsException() throws IOException {
    SpreadsheetManager spyManager = Mockito.spy(spreadsheetManager);
    doReturn(false).when(spyManager).checkSheetExists(TEST_SHEET_NAME);

    IOException updateException = new IOException("Update failed");
    when(mockSpreadsheetsBatchUpdate.execute()).thenThrow(updateException);

    try {
      spyManager.addNewSheet(TEST_SHEET_NAME);
      fail("Expected IOException was not thrown");
    } catch (IOException actualException) {
      assertNotNull(actualException);
      assertEquals("Failed to add output sheet: " + TEST_SHEET_NAME, actualException.getMessage());
      assertSame("Expected underlying cause to be the API exception", updateException,
          actualException.getCause());
    }

    verify(mockSpreadsheetsBatchUpdate).execute();
  }


  @Test
  public void appendToSheet_WithStartColumn() throws IOException {
    List<List<Object>> values = List.of(List.of("a", 1), List.of("b", 2));
    String startColumn = "C";

    spreadsheetManager.appendToSheet(TEST_SHEET_NAME, startColumn, values);

    verify(mockSheetsService).spreadsheets();
    verify(mockSpreadsheets).values();
    verify(mockSpreadsheetValues).append(eq(TEST_SPREADSHEET_ID), stringCaptor.capture(),
        valueRangeCaptor.capture());
    verify(mockValuesAppend).setValueInputOption("RAW");
    verify(mockValuesAppend).execute();

    String expectedRange = TEST_SHEET_NAME + "!" + startColumn + ":" + startColumn;
    assertEquals(expectedRange, stringCaptor.getValue());
    assertEquals(values, valueRangeCaptor.getValue().getValues());
  }

  @Test
  public void appendToSheet_DefaultStartColumn() throws IOException {
    List<List<Object>> values = List.of(List.of("c", 3));

    spreadsheetManager.appendToSheet(TEST_SHEET_NAME, values); // Use the overload

    verify(mockSpreadsheetValues).append(eq(TEST_SPREADSHEET_ID), stringCaptor.capture(),
        valueRangeCaptor.capture());
    verify(mockValuesAppend).setValueInputOption("RAW");
    verify(mockValuesAppend).execute();

    String expectedRange =
        TEST_SHEET_NAME + "!" + DEFAULT_START_COLUMN + ":" + DEFAULT_START_COLUMN;
    assertEquals(expectedRange, stringCaptor.getValue());
    assertEquals(values, valueRangeCaptor.getValue().getValues());
  }

  @Test
  public void appendToSheet_ApiThrowsException() throws IOException {
    List<List<Object>> values = List.of(List.of("a", 1));
    String startColumn = "B";
    IOException apiException = new IOException("Append API failed");
    when(mockValuesAppend.execute()).thenThrow(apiException);

    try {
      spreadsheetManager.appendToSheet(TEST_SHEET_NAME, startColumn, values);
      fail("Expected IOException was not thrown");
    } catch (IOException actualException) {
      assertNotNull(actualException);
      assertEquals("Failed to append data to sheet: " + TEST_SHEET_NAME + " starting from column "
          + startColumn, actualException.getMessage());
      assertSame(apiException, actualException.getCause());
    }
    verify(mockValuesAppend).execute();
  }


  @Test
  public void writeToRange() throws IOException {
    List<List<Object>> values = List.of(List.of("header1", "header2"), List.of("data1", "data2"));

    spreadsheetManager.writeToRange(TEST_RANGE, values);

    verify(mockSheetsService).spreadsheets();
    verify(mockSpreadsheets).values();
    verify(mockSpreadsheetValues).update(eq(TEST_SPREADSHEET_ID), stringCaptor.capture(),
        valueRangeCaptor.capture());
    verify(mockValuesUpdate).setValueInputOption("RAW");
    verify(mockValuesUpdate).execute();

    assertEquals(TEST_RANGE, stringCaptor.getValue());
    assertEquals(values, valueRangeCaptor.getValue().getValues());
  }

  @Test
  public void writeToRange_ApiThrowsException() throws IOException {
    List<List<Object>> values = List.of(List.of("header1"));
    IOException apiException = new IOException("Update API failed");
    when(mockValuesUpdate.execute()).thenThrow(apiException);

    try {
      spreadsheetManager.writeToRange(TEST_RANGE, values);
      fail("Expected IOException was not thrown");
    } catch (IOException actualException) {
      assertNotNull(actualException);
      assertEquals("Failed to write data to range: " + TEST_RANGE, actualException.getMessage());
      assertSame(apiException, actualException.getCause());
    }
    verify(mockValuesUpdate).execute();
  }

  @Test
  public void getSheetRecords_DataFound() throws IOException {
    List<List<Object>> expectedValues = List.of(List.of("r1c1", "r1c2"), List.of("r2c1", "r2c2"));
    ValueRange mockResponse = new ValueRange().setValues(expectedValues);
    when(mockValuesGet.execute()).thenReturn(mockResponse);

    List<List<Object>> actualValues = spreadsheetManager.getSheetRecords(TEST_SHEET_NAME);

    assertEquals(expectedValues, actualValues);
    verify(mockSheetsService).spreadsheets();
    verify(mockSpreadsheets).values();
    verify(mockSpreadsheetValues).get(TEST_SPREADSHEET_ID, TEST_SHEET_NAME);
    verify(mockValuesGet).execute();
  }

  @Test
  public void getSheetRecords_NoDataFound_NullValues() throws IOException {
    ValueRange mockResponse = new ValueRange().setValues(null);
    when(mockValuesGet.execute()).thenReturn(mockResponse);

    List<List<Object>> actualValues = spreadsheetManager.getSheetRecords(TEST_SHEET_NAME);

    assertNotNull(actualValues);
    assertTrue(actualValues.isEmpty());
    verify(mockValuesGet).execute();
  }

  @Test
  public void getSheetRecords_NoDataFound_EmptyList() throws IOException {
    ValueRange mockResponse = new ValueRange().setValues(Collections.emptyList());
    when(mockValuesGet.execute()).thenReturn(mockResponse);

    List<List<Object>> actualValues = spreadsheetManager.getSheetRecords(TEST_SHEET_NAME);

    assertNotNull(actualValues);
    assertTrue(actualValues.isEmpty());
    verify(mockValuesGet).execute();
  }

  @Test
  public void getSheetRecords_ApiThrowsException() throws IOException {
    IOException apiException = new IOException("Get API failed");
    when(mockValuesGet.execute()).thenThrow(apiException);

    try {
      spreadsheetManager.getSheetRecords(TEST_SHEET_NAME);
      fail("Expected IOException was not thrown");
    } catch (IOException actualException) {
      assertNotNull(actualException);
      assertEquals("failed to fetch data from sheet: " + TEST_SHEET_NAME,
          actualException.getMessage());
      assertSame(apiException, actualException.getCause());
    }
    verify(mockValuesGet).execute();
  }
}