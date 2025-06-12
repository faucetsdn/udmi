package com.google.udmi.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for SheetsOutputStream.java.
 */
@RunWith(MockitoJUnitRunner.class)
public class SheetsOutputStreamTest {

  private static final String TEST_SHEET_TITLE = "TestOutputSheet";
  private static final long TEST_SYNC_TIME = 50;

  @Mock
  private SpreadsheetManager mockSpreadsheetManager;

  private MockedStatic<Instant> mockedStaticInstant;
  private MockedStatic<Clock> mockedStaticClock;
  @Mock
  private Instant mockInstant;
  @Captor
  private ArgumentCaptor<List<List<Object>>> valuesCaptor;

  private SheetsOutputStream sheetsOutputStream;

  // --- Variables to hold original System streams ---
  private PrintStream originalSystemOut;
  private PrintStream originalSystemErr;
  private InputStream originalSystemIn;

  // --- Test streams ---
  private ByteArrayOutputStream testOutContent;
  private ByteArrayOutputStream testErrContent;
  private PrintStream testOutPrintStream;
  private PrintStream testErrPrintStream;


  /**
   * Setup and mock as required.
   */
  @Before
  public void setUp() throws IOException {
    originalSystemOut = System.out;
    originalSystemErr = System.err;
    originalSystemIn = System.in;

    testOutContent = new ByteArrayOutputStream();
    testErrContent = new ByteArrayOutputStream();
    testOutPrintStream = new PrintStream(testOutContent, true, StandardCharsets.UTF_8);
    testErrPrintStream = new PrintStream(testErrContent, true, StandardCharsets.UTF_8);

    Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(0), ZoneOffset.UTC);
    mockedStaticClock = Mockito.mockStatic(Clock.class);
    mockedStaticClock.when(Clock::systemUTC).thenReturn(fixedClock);

    mockedStaticInstant = Mockito.mockStatic(Instant.class);
    mockedStaticInstant.when(Instant::now).thenReturn(mockInstant);

    Clock spyClock = spy(fixedClock);
    when(spyClock.instant()).thenReturn(mockInstant);
    mockedStaticClock.when(Clock::systemUTC).thenReturn(spyClock);

    when(mockInstant.toEpochMilli()).thenReturn(0L); // Start time at 0

    sheetsOutputStream = spy(
        new SheetsOutputStream(mockSpreadsheetManager, TEST_SHEET_TITLE, TEST_SYNC_TIME));

    doNothing().when(mockSpreadsheetManager).appendToSheet(eq(TEST_SHEET_TITLE), anyList());
  }

  /**
   * Reset to original streams.
   */
  @After
  public void tearDown() {
    System.setOut(originalSystemOut);
    System.setErr(originalSystemErr);
    System.setIn(originalSystemIn);

    if (mockedStaticInstant != null) {
      mockedStaticInstant.close();
    }
    if (mockedStaticClock != null) {
      mockedStaticClock.close();
    }

    try {
      testOutPrintStream.close();
      testErrPrintStream.close();
      testOutContent.close();
      testErrContent.close();
    } catch (IOException e) {
      // Ignore closing errors
    }
  }

  @Test
  public void constructor_callsAddNewSheet() throws IOException {
    reset(mockSpreadsheetManager);
    doNothing().when(mockSpreadsheetManager).addNewSheet(anyString());
    new SheetsOutputStream(mockSpreadsheetManager, TEST_SHEET_TITLE, TEST_SYNC_TIME);
    verify(mockSpreadsheetManager).addNewSheet(TEST_SHEET_TITLE);
  }

  @Test
  public void write_int_appendsCharToBuffer() {
    sheetsOutputStream.write('H');
    sheetsOutputStream.write('i');
    assertEquals("Hi", sheetsOutputStream.buffer.toString());
    verify(sheetsOutputStream, never()).appendToSheet();
  }

  @Test
  public void write_byteArray_appendsStringToBuffer() {
    byte[] data = "Hello".getBytes(StandardCharsets.UTF_8);
    sheetsOutputStream.write(data, 0, data.length);
    assertEquals("Hello", sheetsOutputStream.buffer.toString());
    verify(sheetsOutputStream, never()).appendToSheet();
  }

  @Test
  public void write_byteArray_partial_appendsStringToBuffer() {
    byte[] data = "XHelloX".getBytes(StandardCharsets.UTF_8);
    sheetsOutputStream.write(data, 1, 5);
    assertEquals("Hello", sheetsOutputStream.buffer.toString());
    verify(sheetsOutputStream, never()).appendToSheet();
  }

  @Test
  public void syncIfNeeded_doesNotSync_ifNoNewline() {
    when(mockInstant.toEpochMilli()).thenReturn(0L, TEST_SYNC_TIME + 10);
    sheetsOutputStream.write('a');
    sheetsOutputStream.write('b');
    verify(sheetsOutputStream, never()).appendToSheet();
    assertEquals("ab", sheetsOutputStream.buffer.toString());
  }

  @Test
  public void syncIfNeeded_doesNotSync_ifNewlineButTimeNotElapsed() throws IOException {
    when(mockInstant.toEpochMilli()).thenReturn(0L, TEST_SYNC_TIME - 10);
    sheetsOutputStream.write("a\n".getBytes());
    sheetsOutputStream.write('b');
    verify(sheetsOutputStream, never()).appendToSheet();
    assertEquals("a\nb", sheetsOutputStream.buffer.toString());
  }

  @Test
  public void syncIfNeeded_Syncs_ifNewlineAndTimeElapsed() throws IOException {
    when(mockInstant.toEpochMilli()).thenReturn(0L, TEST_SYNC_TIME + 10);
    sheetsOutputStream.write("line1\n".getBytes());
    sheetsOutputStream.write("line2".getBytes());
    verify(sheetsOutputStream, times(1)).appendToSheet();
    assertEquals("", sheetsOutputStream.buffer.toString());
  }

  @Test
  public void appendToSheet_sendsBufferedLinesToManager() throws IOException {
    String line1 = "First line";
    String line2 = "Second line";
    sheetsOutputStream.buffer.append(line1).append("\n").append(line2).append("\n");
    sheetsOutputStream.appendToSheet();
    verify(mockSpreadsheetManager).appendToSheet(eq(TEST_SHEET_TITLE), valuesCaptor.capture());
    List<List<Object>> capturedValues = valuesCaptor.getValue();
    assertEquals(2, capturedValues.size());
    assertEquals(line1, capturedValues.get(0).get(0));
    assertEquals(line2, capturedValues.get(1).get(0));
    assertEquals(0, sheetsOutputStream.buffer.length());
  }

  @Test
  public void appendToSheet_handlesEmptyLinesAndWhitespace() throws IOException {
    sheetsOutputStream.buffer.append("  \n").append("Real Data\n\n").append("   More Data   \n");
    sheetsOutputStream.appendToSheet();
    verify(mockSpreadsheetManager).appendToSheet(eq(TEST_SHEET_TITLE), valuesCaptor.capture());
    List<List<Object>> capturedValues = valuesCaptor.getValue();
    assertEquals(2, capturedValues.size());
    assertEquals("Real Data", capturedValues.get(0).get(0));
    assertEquals("   More Data   ", capturedValues.get(1).get(0));
    assertEquals(0, sheetsOutputStream.buffer.length());
  }

  @Test
  public void appendToSheet_doesNothingIfBufferIsEmptyOrWhitespace() throws IOException {
    sheetsOutputStream.buffer.setLength(0);
    sheetsOutputStream.appendToSheet();
    verify(mockSpreadsheetManager, never()).appendToSheet(anyString(), anyList());
    assertEquals(0, sheetsOutputStream.buffer.length());

    sheetsOutputStream.buffer.append("   \n\t  \n ");
    sheetsOutputStream.appendToSheet();
    verify(mockSpreadsheetManager, never()).appendToSheet(anyString(), anyList());
    assertEquals(0, sheetsOutputStream.buffer.length());
  }

  @Test
  public void appendToSheet_doesNotClearBufferIfApiCallFails() throws IOException {
    String text = "Some data\n";
    sheetsOutputStream.buffer.append(text);
    doThrow(new IOException("Sheet API Error")).when(mockSpreadsheetManager)
        .appendToSheet(anyString(), anyList());
    sheetsOutputStream.appendToSheet();
    assertEquals(text, sheetsOutputStream.buffer.toString());
    verify(mockSpreadsheetManager).appendToSheet(eq(TEST_SHEET_TITLE), anyList());
  }

  @Test
  public void startStream_redirectsSystemOutAndErr() throws Exception {
    String testOutMessage = "Message to System.out";
    String testErrMessage = "Message to System.err";

    try {
      // --- Redirect ---
      System.setOut(testOutPrintStream);
      System.setErr(testErrPrintStream);

      // --- Act ---
      SheetsOutputStream stream = spy(
          new SheetsOutputStream(mockSpreadsheetManager, TEST_SHEET_TITLE, TEST_SYNC_TIME));
      stream.startStream();

      // The internal originalSystemOut field now holds our testOutPrintStream
      assertSame(testOutPrintStream, stream.originalSystemOut);
      assertSame(testErrPrintStream, stream.originalSystemErr);

      // Simulate output
      when(mockInstant.toEpochMilli()).thenReturn(0L, TEST_SYNC_TIME + 10, TEST_SYNC_TIME + 20);
      System.out.println(
          testOutMessage); // Goes to DualOutputStream -> testOutPrintStream AND SheetsOutputStream
      System.err.println(
          testErrMessage); // Goes to DualOutputStream -> testOutPrintStream AND SheetsOutputStream

      // --- Assert ---
      // 1. Check captured output
      testOutPrintStream.flush();
      testErrPrintStream.flush();
      String capturedOut = testOutContent.toString(StandardCharsets.UTF_8).trim();
      String capturedErr = testErrContent.toString(StandardCharsets.UTF_8).trim();

      assertTrue("Captured output should contain System.out message",
          capturedOut.contains(testOutMessage));
      assertTrue("Captured output should also contain System.err message",
          capturedOut.contains(testErrMessage));
      assertEquals("Captured error stream should be empty after startStream", 0,
          capturedErr.length());

      // 2. Check if SheetsOutputStream synced
      verify(stream, atLeastOnce()).appendToSheet();
      verify(mockSpreadsheetManager, times(1)).appendToSheet(eq(TEST_SHEET_TITLE),
          valuesCaptor.capture());

      List<List<Object>> allValues = valuesCaptor.getAllValues().stream().flatMap(List::stream)
          .toList();
      assertEquals(2, allValues.size());
      assertTrue(allValues.stream().anyMatch(row -> testOutMessage.equals(row.get(0))));
      assertTrue(allValues.stream().anyMatch(row -> testErrMessage.equals(row.get(0))));

      stream.stopStream();
    } finally {
      System.setOut(originalSystemOut);
      System.setErr(originalSystemErr);
    }
  }

  @Test
  public void stopStream_restoresOriginalStreamsAndFlushes() throws Exception {
    PrintStream realOriginalOut = originalSystemOut;
    PrintStream realOriginalErr = originalSystemErr;

    try {
      // Redirect system streams so startStream captures them
      System.setOut(testOutPrintStream);
      System.setErr(testErrPrintStream);

      // Arrange: Start stream, put data in buffer
      SheetsOutputStream stream = spy(
          new SheetsOutputStream(mockSpreadsheetManager, TEST_SHEET_TITLE, TEST_SYNC_TIME));
      stream.startStream();
      stream.buffer.append("Final data\n");
      reset(mockSpreadsheetManager);
      doNothing().when(mockSpreadsheetManager).appendToSheet(anyString(), anyList());

      // Act
      stream.stopStream(); // Should restore testOutPrintStream/testErrPrintStream

      // Assert: Streams restored
      assertSame("System.out should be restored to what startStream captured", testOutPrintStream,
          System.out);
      assertSame("System.err should be restored to what startStream captured", testErrPrintStream,
          System.err);

      // Assert: Check final flush happened
      verify(mockSpreadsheetManager).appendToSheet(eq(TEST_SHEET_TITLE), valuesCaptor.capture());
      assertEquals("Final data", valuesCaptor.getValue().get(0).get(0));
      assertEquals(0, stream.buffer.length());

      // Assert: Internal fields nulled
      assertNull(stream.originalSystemOut);
      assertNull(stream.originalSystemErr);

    } finally {
      System.setOut(realOriginalOut);
      System.setErr(realOriginalErr);
    }
  }

  @Test
  public void startStream_isIdempotent() throws Exception {
    PrintStream firstCapturedOut = null;
    PrintStream firstCapturedErr = null;
    PrintStream originalOutRef = null;
    try {
      System.setOut(testOutPrintStream); // Redirect before first call
      System.setErr(testErrPrintStream);

      SheetsOutputStream stream = spy(
          new SheetsOutputStream(mockSpreadsheetManager, TEST_SHEET_TITLE, TEST_SYNC_TIME));
      stream.startStream(); // First call

      firstCapturedOut = System.out;
      firstCapturedErr = System.err;
      originalOutRef = stream.originalSystemOut;

      stream.startStream(); // Second call

      // Assert: Streams shouldn't have changed from the first call
      assertSame(firstCapturedOut, System.out);
      assertSame(firstCapturedErr, System.err); // Should be same object
      // Assert: Internal original refs should not be overwritten
      assertSame(originalOutRef, stream.originalSystemOut);

    } finally {
      System.setOut(originalSystemOut);
      System.setErr(originalSystemErr);
    }
  }

  @Test
  public void stopStream_isIdempotent() throws IOException {
    try {
      System.setOut(testOutPrintStream);
      System.setErr(testErrPrintStream);

      SheetsOutputStream stream = spy(
          new SheetsOutputStream(mockSpreadsheetManager, TEST_SHEET_TITLE, TEST_SYNC_TIME));
      stream.startStream();
      stream.buffer.append("Data");

      // First call, flushes
      stream.stopStream();
      // Flushed once
      verify(stream, times(1)).appendToSheet();

      // Second call
      stream.stopStream();
      // Should not flush again, count unchanged
      verify(stream, times(1)).appendToSheet();

      // Streams should remain restored to what startStream captured
      assertSame(testOutPrintStream, System.out);
      assertSame(testErrPrintStream, System.err);

    } finally {
      System.setOut(originalSystemOut);
      System.setErr(originalSystemErr);
    }
  }

  @Test
  public void startStreamFromInput_readsInputAndWritesToSheet() throws Exception {
    String line1 = "Input Line One";
    String line2 = "Input Line Two";
    String simulatedInput = line1 + System.lineSeparator() + line2 + System.lineSeparator();

    try (InputStream testInputStream = new ByteArrayInputStream(
        simulatedInput.getBytes(StandardCharsets.UTF_8))) {
      // --- Redirect ---
      System.setIn(testInputStream);
      System.setOut(testOutPrintStream);

      // --- Act ---
      sheetsOutputStream = spy(
          new SheetsOutputStream(mockSpreadsheetManager, TEST_SHEET_TITLE, TEST_SYNC_TIME));
      reset(mockSpreadsheetManager);
      doNothing().when(mockSpreadsheetManager).appendToSheet(anyString(), anyList());
      when(mockInstant.toEpochMilli()).thenReturn(0L, 1L, 2L, 3L,
          TEST_SYNC_TIME + 10);

      sheetsOutputStream.startStreamFromInput();

      // --- Assert ---
      // 1. Check output echoed to testOutPrintStream
      testOutPrintStream.flush();
      String capturedEcho = testOutContent.toString(StandardCharsets.UTF_8);
      assertTrue(capturedEcho.contains(line1));
      assertTrue(capturedEcho.contains(line2));

      // 2. Check appendToSheet calls
      verify(sheetsOutputStream, atLeastOnce()).appendToSheet();
      verify(mockSpreadsheetManager, atLeastOnce()).appendToSheet(eq(TEST_SHEET_TITLE),
          valuesCaptor.capture());

      List<List<Object>> allValues = valuesCaptor.getAllValues().stream().flatMap(List::stream)
          .toList();
      assertTrue(allValues.stream().anyMatch(row -> line1.equals(row.get(0))));
      assertTrue(allValues.stream().anyMatch(row -> line2.equals(row.get(0))));

    } finally {
      System.setIn(originalSystemIn);
      System.setOut(originalSystemOut);
    }
  }

  @Test
  public void startStreamFromInput_finalFlushHappens() throws Exception {
    String lastLine = "The very last line, no newline";

    try (InputStream testInputStream = new ByteArrayInputStream(
        lastLine.getBytes(StandardCharsets.UTF_8))) {
      // --- Redirect ---
      System.setIn(testInputStream);
      System.setOut(testOutPrintStream);

      // --- Act ---
      sheetsOutputStream = spy(
          new SheetsOutputStream(mockSpreadsheetManager, TEST_SHEET_TITLE, TEST_SYNC_TIME));
      reset(mockSpreadsheetManager);
      doNothing().when(mockSpreadsheetManager).appendToSheet(anyString(), anyList());

      sheetsOutputStream.startStreamFromInput();

      // --- Assert ---
      verify(sheetsOutputStream, atLeastOnce()).appendToSheet();
      verify(mockSpreadsheetManager, atLeastOnce()).appendToSheet(eq(TEST_SHEET_TITLE),
          valuesCaptor.capture());

      // Check the *last* captured value list contains the final line
      List<List<Object>> lastValues = valuesCaptor.getValue();
      assertFalse("Expected final values list to not be empty", lastValues.isEmpty());
      assertEquals(lastLine, lastValues.get(lastValues.size() - 1).get(0));

    } finally {
      System.setIn(originalSystemIn);
      System.setOut(originalSystemOut);
    }
  }
}