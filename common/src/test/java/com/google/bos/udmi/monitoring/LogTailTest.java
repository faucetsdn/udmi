package com.google.bos.udmi.monitoring;

import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.bos.udmi.monitoring.LogTail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LogEntryServerStream;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.TailOption;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Severity;
import com.google.cloud.logging.v2.LoggingSettings;
import java.util.ArrayList;
import java.util.HashMap;
import org.apache.commons.cli.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;


public class LogTailTest {

  final static String TEST_PROJECT_NAME = "XXXtestXXXCantBeARealProject";
  final static String[] TEST_MAIN_ARGS = {"-p", TEST_PROJECT_NAME};


  private AutoCloseable closeable;

  private LogTail getLogTailMock() {
    LogTail logTail = new LogTail(TEST_PROJECT_NAME);
    LogTail logTailMock = spy(logTail);
    return logTailMock;
  }

  @Before
  public void openMocks() {
    closeable = MockitoAnnotations.openMocks(this);
  }

  @After
  public void releaseMocks() throws Exception {
    closeable.close();
  }

  @Test
  public void testInstantiate() {
    LogTail logTail = new LogTail(TEST_PROJECT_NAME);
  }

  @Test
  public void testMain() throws org.apache.commons.cli.ParseException {
    LogTail logTailMock = getLogTailMock();
    doNothing().when(logTailMock).tailLogs();
    LogTail.mainUnderTest(TEST_MAIN_ARGS, logTailMock);
  }

  @Test
  public void testMainNoProject() throws org.apache.commons.cli.ParseException {
    String[] args = {};
    assertThrows(RuntimeException.class, () -> LogTail.main(args));
  }

  @Test
  public void testProcessLogEntryWhenFunctionUdmiConfig() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = mock(LogEntry.class);
    MonitoredResource monitoredResource = MonitoredResource.newBuilder("global")
        .addLabel("function_name", "udmi_config").build();
    when(logEntryMock.getSeverity()).thenReturn(Severity.INFO);
    when(logEntryMock.getResource()).thenReturn(monitoredResource);
    doNothing().when(logTailMock).processLogEntryError(logEntryMock);
    doNothing().when(logTailMock).processLogEntryUdmiConfig(logEntryMock);
    // Go
    logTailMock.processLogEntry(logEntryMock);
  }

  @Test
  public void testProcessLogEntryWhenFunctionUdmiState() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = mock(LogEntry.class);
    MonitoredResource monitoredResource = MonitoredResource.newBuilder("global")
        .addLabel("function_name", "udmi_state").build();
    when(logEntryMock.getSeverity()).thenReturn(Severity.INFO);
    when(logEntryMock.getResource()).thenReturn(monitoredResource);
    doNothing().when(logTailMock).processLogEntryError(logEntryMock);
    doNothing().when(logTailMock).processLogEntryUdmiState(logEntryMock);
    // Go
    logTailMock.processLogEntry(logEntryMock);
  }

  // TODO: Parameterize the udmi_{target,state,config} tests.
  @Test
  public void testProcessLogEntryWhenFunctionUdmiTarget() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = mock(LogEntry.class);
    MonitoredResource monitoredResource = MonitoredResource.newBuilder("global")
        .addLabel("function_name", "udmi_target").build();
    when(logEntryMock.getSeverity()).thenReturn(Severity.INFO);
    when(logEntryMock.getResource()).thenReturn(monitoredResource);
    doNothing().when(logTailMock).processLogEntryError(logEntryMock);
    doNothing().when(logTailMock).processLogEntryUdmiTarget(logEntryMock);
    // Go
    logTailMock.processLogEntry(logEntryMock);
  }

  @Test
  public void testProcessLogEntryWhenLogSeverityError() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = mock(LogEntry.class);
    when(logEntryMock.getSeverity()).thenReturn(Severity.ERROR);
    doNothing().when(logTailMock).processLogEntryError(logEntryMock);
    // Go
    logTailMock.processLogEntry(logEntryMock);
  }

  @Test
  public void testTailLogs() {
    LogTail logTailMock = getLogTailMock();
    LogEntryServerStream logEntryServerStreamMock = mock(LogEntryServerStream.class);
    when(logTailMock.getCloudLogStream(logTailMock.LOG_FILTER)).thenReturn(
        logEntryServerStreamMock);
    doNothing().when(logTailMock).processLogStream(logEntryServerStreamMock);
    assertTrue(logTailMock.outputJson);
    logTailMock.tailLogs();
    assertTrue(logTailMock.output.getClass().equals(LogTailJsonOutput.class));
  }
}
