package com.google.bos.udmi.monitoring;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LogEntryServerStream;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.TailOption;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Payload;
import com.google.cloud.logging.Payload.ProtoPayload;
import com.google.cloud.logging.Severity;
import com.google.protobuf.Any;
import java.util.ArrayList;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;


public class LogTailTest {

  final static String TEST_PROJECT_NAME = "XXXtestXXXCantBeARealProject";
  final static String[] TEST_MAIN_ARGS = {"-p", TEST_PROJECT_NAME};

  private LogTail getLogTailMock() {
    LogTail logTail = new LogTail(TEST_PROJECT_NAME);
    return (LogTail) Mockito.spy(logTail);
  }

  @Test
  public void testGetCloudLogStreamWhenFilter() {
    LogTail logTailMock = getLogTailMock();
    LoggingOptions loggingOptionsMock = Mockito.mock(LoggingOptions.class);
    Logging loggingMock = Mockito.mock(Logging.class);
    LogEntryServerStream streamMock = Mockito.mock(LogEntryServerStream.class);
    Mockito.when(logTailMock.getLoggingOptionsDefaultInstance()).thenReturn(loggingOptionsMock);
    Mockito.when(loggingOptionsMock.getService()).thenReturn(loggingMock);
    Mockito.when(loggingMock.tailLogEntries(any(TailOption.class), any(TailOption.class))).thenReturn(streamMock);
    assertEquals(streamMock, logTailMock.getCloudLogStream("meaningless filter"));
  }

  @Test
  public void testGetCloudLogStreamWhenNoFilter() {
    LogTail logTailMock = getLogTailMock();
    LoggingOptions loggingOptionsMock = Mockito.mock(LoggingOptions.class);
    Logging loggingMock = Mockito.mock(Logging.class);
    LogEntryServerStream streamMock = Mockito.mock(LogEntryServerStream.class);
    Mockito.when(logTailMock.getLoggingOptionsDefaultInstance()).thenReturn(loggingOptionsMock);
    Mockito.when(loggingOptionsMock.getService()).thenReturn(loggingMock);
    Mockito.when(loggingMock.tailLogEntries(any(TailOption.class))).thenReturn(streamMock);
    assertEquals(streamMock, logTailMock.getCloudLogStream(null));
  }

  @Test
  public void testMain() throws org.apache.commons.cli.ParseException {
    LogTail logTailMock = getLogTailMock();
    Mockito.doNothing().when(logTailMock).tailLogs();
    LogTail.mainUnderTest(TEST_MAIN_ARGS, logTailMock);
  }

  @Test
  public void testMainNoProject() {
    String[] args = {};
    assertThrows(RuntimeException.class, () -> LogTail.main(args));
  }

  /*

  This test does not work yet.
  The mock of ProtoPayload is not working because it's a final class.

  @Test
  public void testProcessLogEntryError() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    ProtoPayload payloadMock = Mockito.mock(ProtoPayload.class);
    Payload<Any> dataMock = Mockito.mock(Payload.class);
    Mockito.when(logEntryMock.getPayload()).thenReturn(payloadMock);
    Mockito.when(payloadMock.getData()).thenReturn(dataMock.getData());
    logTailMock.processLogEntryError(logEntryMock);
  }


   */

  @Test
  public void testProcessLogEntryWhenFunctionUdmiConfig() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    MonitoredResource monitoredResource = MonitoredResource.newBuilder("global")
        .addLabel("function_name", "udmi_config").build();
    Mockito.when(logEntryMock.getSeverity()).thenReturn(Severity.INFO);
    Mockito.when(logEntryMock.getResource()).thenReturn(monitoredResource);
    Mockito.doNothing().when(logTailMock).processLogEntryError(logEntryMock);
    Mockito.doNothing().when(logTailMock).processLogEntryUdmiConfig(logEntryMock);
    // Go
    logTailMock.processLogEntry(logEntryMock);
  }

  @Test
  public void testProcessLogEntryWhenFunctionUdmiState() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    MonitoredResource monitoredResource = MonitoredResource.newBuilder("global")
        .addLabel("function_name", "udmi_state").build();
    Mockito.when(logEntryMock.getSeverity()).thenReturn(Severity.INFO);
    Mockito.when(logEntryMock.getResource()).thenReturn(monitoredResource);
    Mockito.doNothing().when(logTailMock).processLogEntryError(logEntryMock);
    Mockito.doNothing().when(logTailMock).processLogEntryUdmiState(logEntryMock);
    // Go
    logTailMock.processLogEntry(logEntryMock);
  }

  // TODO: Parameterize the udmi_{target,state,config} tests.
  @Test
  public void testProcessLogEntryWhenFunctionUdmiTarget() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    MonitoredResource monitoredResource = MonitoredResource.newBuilder("global")
        .addLabel("function_name", "udmi_target").build();
    Mockito.when(logEntryMock.getSeverity()).thenReturn(Severity.INFO);
    Mockito.when(logEntryMock.getResource()).thenReturn(monitoredResource);
    Mockito.doNothing().when(logTailMock).processLogEntryError(logEntryMock);
    Mockito.doNothing().when(logTailMock).processLogEntryUdmiTarget(logEntryMock);
    // Go
    logTailMock.processLogEntry(logEntryMock);
  }

  @Test
  public void testProcessLogEntryWhenLogSeverityError() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    Mockito.when(logEntryMock.getSeverity()).thenReturn(Severity.ERROR);
    Mockito.doNothing().when(logTailMock).processLogEntryError(logEntryMock);
    // Go
    logTailMock.processLogEntry(logEntryMock);
  }

  @Test
  public void testProcessLogStream() {
    LogTail logTailMock = getLogTailMock();
    LogEntryServerStream streamMock = Mockito.mock(LogEntryServerStream.class);
    Iterator iteratorMock = Mockito.mock(Iterator.class);
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    Mockito.when(streamMock.iterator()).thenReturn(iteratorMock);
    Mockito.when(iteratorMock.hasNext()).thenReturn(true, false);
    Mockito.when(iteratorMock.next()).thenReturn(logEntryMock);
    Mockito.doNothing().when(logTailMock).processLogEntry(logEntryMock);
    logTailMock.processLogStream(streamMock);
  }

  @Test
  public void testTailLogs() {
    LogTail logTailMock = getLogTailMock();
    LogEntryServerStream logEntryServerStreamMock = Mockito.mock(LogEntryServerStream.class);
    Mockito.when(logTailMock.getCloudLogStream(logTailMock.LOG_FILTER)).thenReturn(
        logEntryServerStreamMock);
    Mockito.doNothing().when(logTailMock).processLogStream(logEntryServerStreamMock);
    assertTrue(logTailMock.outputJson);
    logTailMock.tailLogs();
    assertEquals(logTailMock.output.getClass(), LogTailJsonOutput.class);
  }
}
