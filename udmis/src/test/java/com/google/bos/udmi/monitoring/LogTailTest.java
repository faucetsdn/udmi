package com.google.bos.udmi.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;

import com.google.cloud.MonitoredResource;
import com.google.cloud.audit.AuditLog;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LogEntryServerStream;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.TailOption;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Payload.ProtoPayload;
import com.google.cloud.logging.Severity;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;


/**
 * Unit tests for LogTail class.
 * It may be beneficial to clear local gcloud set up so that tests run in the IDE
 * the same way they run on Github CI.
 *   gcloud auth revoke
 *   gcloud config unset project
 *
 */
public class LogTailTest {

  static final String TEST_PROJECT_NAME = "XXXtestXXXCantBeARealProject";
  static final String[] TEST_MAIN_ARGS = {"-p", TEST_PROJECT_NAME};

  private LogTailEntry getLogTailEntryTest() {
    LogTailEntry entry = new LogTailEntry();
    entry.timestamp = Instant.now();
    entry.methodName = "method_name";
    entry.serviceName = "service_name";
    entry.resourceName = "resource_name";
    entry.statusCode = 1;
    entry.statusMessage = "status_message";
    entry.severity = Severity.ERROR;
    return entry;
  }

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

    Mockito.doReturn(loggingOptionsMock).when(logTailMock).getLoggingOptionsDefaultInstance();
    Mockito.doReturn(loggingMock).when(loggingOptionsMock).getService();
    Mockito.doReturn(streamMock).when(loggingMock)
        .tailLogEntries(any(TailOption.class), any(TailOption.class));

    assertEquals(streamMock, logTailMock.getCloudLogStream("meaningless filter"));
  }

  @Test
  public void testGetCloudLogStreamWhenNoFilter() {
    LogTail logTailMock = getLogTailMock();
    LoggingOptions loggingOptionsMock = Mockito.mock(LoggingOptions.class);
    Logging loggingMock = Mockito.mock(Logging.class);
    LogEntryServerStream streamMock = Mockito.mock(LogEntryServerStream.class);

    Mockito.doReturn(loggingOptionsMock).when(logTailMock).getLoggingOptionsDefaultInstance();
    Mockito.doReturn(loggingMock).when(loggingOptionsMock).getService();
    Mockito.doReturn(streamMock).when(loggingMock).tailLogEntries(any(TailOption.class));

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

  @Test
  public void testProcessLogEntryError() {
    LogTail logTailMock = getLogTailMock();

    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    ProtoPayload payloadMock = Mockito.mock(ProtoPayload.class);
    Any dataMock = Mockito.mock(Any.class);
    ByteString byteStringMock = Mockito.mock(ByteString.class);
    byte[] byteArray = {0};

    Mockito.when(logEntryMock.getPayload()).thenReturn(payloadMock);
    Mockito.when(payloadMock.getData()).thenReturn(dataMock);
    Mockito.when(dataMock.getTypeUrl())
        .thenReturn("type.googleapis.com/google.cloud.audit.AuditLog");
    Mockito.when(dataMock.getValue()).thenReturn(byteStringMock);
    Mockito.when(byteStringMock.toByteArray()).thenReturn(byteArray);

    AuditLog auditLogMock = Mockito.mock(AuditLog.class);
    try {
      Mockito.doReturn(auditLogMock).when(logTailMock).getAuditLogParseFrom(any(byte[].class));
    } catch (InvalidProtocolBufferException e) {
      fail(e);
    }

    LogTailEntry entryTest = getLogTailEntryTest();
    Mockito.doReturn(entryTest.timestamp).when(logEntryMock).getInstantTimestamp();
    Mockito.doReturn(entryTest.methodName).when(auditLogMock).getMethodName();
    Mockito.doReturn(entryTest.serviceName).when(auditLogMock).getServiceName();
    Mockito.doReturn(entryTest.resourceName).when(auditLogMock).getResourceName();
    Status statusMock = Mockito.mock(Status.class);
    Mockito.doReturn(statusMock).when(auditLogMock).getStatus();
    Mockito.doReturn(entryTest.statusCode).when(statusMock).getCode();
    Mockito.doReturn(entryTest.statusMessage).when(statusMock).getMessage();
    Mockito.doReturn(entryTest.severity).when(logEntryMock).getSeverity();

    logTailMock.logsTimeSeries = Mockito.mock(LogTimeSeries.class);
    ArgumentCaptor<LogTailEntry> argumentCaptor = ArgumentCaptor.forClass(LogTailEntry.class);
    Mockito.doNothing().when(logTailMock.logsTimeSeries).add(argumentCaptor.capture());
    try {
      Mockito.doNothing().when(logTailMock.logsTimeSeries)
          .maybeEmitMetrics(any(LogTailOutput.class));
    } catch (IOException e) {
      fail(e);
    }

    logTailMock.processLogEntryError(logEntryMock);
    assertTrue(argumentCaptor.getValue().equals(entryTest));
  }

  /*
   This test fails because IOException is not thrown from maybeEmitMetrics().

  @Test
  public void testProcessLogEntryErrorWhenIOException() {
    LogTail logTailMock = getLogTailMock();

    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    ProtoPayload payloadMock = Mockito.mock(ProtoPayload.class);
    Any dataMock = Mockito.mock(Any.class);
    ByteString byteStringMock = Mockito.mock(ByteString.class);
    byte[] byteArray = {0};

    Mockito.when(logEntryMock.getPayload()).thenReturn(payloadMock);
    Mockito.when(payloadMock.getData()).thenReturn(dataMock);
    Mockito.when(dataMock.getTypeUrl())
        .thenReturn("type.googleapis.com/google.cloud.audit.AuditLog");
    Mockito.when(dataMock.getValue()).thenReturn(byteStringMock);
    Mockito.when(byteStringMock.toByteArray()).thenReturn(byteArray);

    AuditLog auditLogMock = Mockito.mock(AuditLog.class);
    try {
      Mockito.doReturn(auditLogMock).when(logTailMock).getAuditLogParseFrom(any(byte[].class));
    } catch (InvalidProtocolBufferException e) {
      fail(e);
    }

    LogTailEntry entryTest = getLogTailEntryTest();
    Mockito.doReturn(entryTest.timestamp).when(logEntryMock).getInstantTimestamp();
    Mockito.doReturn(entryTest.methodName).when(auditLogMock).getMethodName();
    Mockito.doReturn(entryTest.serviceName).when(auditLogMock).getServiceName();
    Mockito.doReturn(entryTest.resourceName).when(auditLogMock).getResourceName();
    Status statusMock = Mockito.mock(Status.class);
    Mockito.doReturn(statusMock).when(auditLogMock).getStatus();
    Mockito.doReturn(entryTest.statusCode).when(statusMock).getCode();
    Mockito.doReturn(entryTest.statusMessage).when(statusMock).getMessage();
    Mockito.doReturn(entryTest.severity).when(logEntryMock).getSeverity();

    logTailMock.logsTimeSeries = Mockito.mock(LogTimeSeries.class);
    LogTimeSeries logsTimeSeriesMock = Mockito.mock(LogTimeSeries.class);
    logTailMock.logsTimeSeries = logsTimeSeriesMock;

    Mockito.doNothing().when(logTailMock.logsTimeSeries).add(any(LogTailEntry.class));

    try {
      Mockito.doThrow(IOException.class).when(logsTimeSeriesMock)
          .maybeEmitMetrics(any(LogTailOutput.class));
    } catch (IOException e) {
      fail(e);
    }
    logTailMock.processLogEntryError(logEntryMock);
    Mockito.verify(logTailMock).error(anyString());
  }
*/

  @Test
  public void testProcessLogEntryErrorWhenInvalidProtocolBufferException() {
    LogTail logTailMock = getLogTailMock();

    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    ProtoPayload payloadMock = Mockito.mock(ProtoPayload.class);
    Any dataMock = Mockito.mock(Any.class);
    ByteString byteStringMock = Mockito.mock(ByteString.class);
    byte[] byteArray = {0};

    Mockito.when(logEntryMock.getPayload()).thenReturn(payloadMock);
    Mockito.when(payloadMock.getData()).thenReturn(dataMock);
    Mockito.when(dataMock.getTypeUrl())
        .thenReturn("type.googleapis.com/google.cloud.audit.AuditLog");
    Mockito.when(dataMock.getValue()).thenReturn(byteStringMock);
    Mockito.when(byteStringMock.toByteArray()).thenReturn(byteArray);

    try {
      Mockito.doThrow(InvalidProtocolBufferException.class).when(logTailMock)
          .getAuditLogParseFrom(any(byte[].class));
    } catch (InvalidProtocolBufferException e) {
      fail(e);
    }

    logTailMock.processLogEntryError(logEntryMock);
    Mockito.verify(logTailMock).error(anyString());

  }

  @Test
  public void testProcessLogEntryErrorWhenNotAuditPayload() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    ProtoPayload payloadMock = Mockito.mock(ProtoPayload.class);
    Any dataMock = Mockito.mock(Any.class);
    Mockito.when(logEntryMock.getPayload()).thenReturn(payloadMock);
    Mockito.when(payloadMock.getData()).thenReturn(dataMock);
    Mockito.when(dataMock.getTypeUrl())
        .thenReturn("type.googleapis.com/google.cloud.audit.AuditLog_INVALID");
    Mockito.doNothing().when(logTailMock).error("Log payload is not AuditLog");
    logTailMock.processLogEntryError(logEntryMock);
  }

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
    logTailMock.processLogEntry(logEntryMock);
  }

  @Test
  public void testProcessLogStream() {
    LogTail logTailMock = getLogTailMock();
    LogEntryServerStream streamMock = Mockito.mock(LogEntryServerStream.class);
    Iterator iteratorMock = Mockito.mock(Iterator.class);
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    Mockito.doReturn(iteratorMock).when(streamMock).iterator();
    Mockito.doReturn(true, false).when(iteratorMock).hasNext();
    Mockito.doReturn(logEntryMock).when(iteratorMock).next();
    Mockito.doNothing().when(logTailMock).processLogEntry(logEntryMock);
    logTailMock.processLogStream(streamMock);
  }

  @Test
  public void testTailLogs() {
    LogTail logTailMock = getLogTailMock();
    LogEntryServerStream streamMock = Mockito.mock(LogEntryServerStream.class);
    Mockito.doReturn(streamMock).when(logTailMock)
        .getCloudLogStream(eq(logTailMock.LOG_FILTER));
    Mockito.doNothing().when(logTailMock).processLogStream(streamMock);
    assertTrue(logTailMock.outputJson);
    logTailMock.tailLogs();
    Mockito.verify(logTailMock).processLogStream(streamMock);
    assertEquals(logTailMock.output.getClass(), LogTailJsonOutput.class);
  }
}
