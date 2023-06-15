package com.google.bos.udmi.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.auth.oauth2.ServiceAccountCredentials;
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
import java.io.InputStream;
import java.time.Instant;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;


/**
 * Unit tests for LogTail class. It may be beneficial to clear local gcloud set up so that tests run
 * in the IDE the same way they run on Github CI. gcloud auth revoke gcloud config unset project
 */
public class LogTailTest {

  static final String TEST_PROJECT_NAME = "XXXtestXXXCantBeARealProject";
  static final String TEST_KEY_FILENAME = "/tmp/thisKeyFileDoesNotExistXXX";
  static final String[] TEST_MAIN_ARGS = {"-p", TEST_PROJECT_NAME, "-k", TEST_KEY_FILENAME};

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
    LogTail logTail = new LogTail(TEST_PROJECT_NAME, TEST_KEY_FILENAME);
    return (LogTail) Mockito.spy(logTail);
  }

  @Test
  public void testGetLoggingServiceWhenCredentialsStreamIoException() {
    LogTail logTailMock = getLogTailMock();
    final LoggingOptions.Builder loggingOptionsBuilderMock = Mockito.mock(
        LoggingOptions.Builder.class);
    final LoggingOptions loggingOptionsMock = Mockito.mock(LoggingOptions.class);
    final Logging loggingMock = Mockito.mock(Logging.class);
    final InputStream credentialsStreamMock = Mockito.mock(InputStream.class);
    final ServiceAccountCredentials serviceAccountCredentialsMock = Mockito.mock(
        ServiceAccountCredentials.class);

    doReturn(loggingOptionsBuilderMock).when(logTailMock).getLoggingOptionsBuilder();
    try {
      doThrow(IOException.class).when(
          logTailMock).getServiceAccountCredentialsFromStream(credentialsStreamMock);
    } catch (IOException e) {
      fail(e);
    }

    assertThrows(IllegalArgumentException.class, () ->
        logTailMock.getLoggingService(TEST_PROJECT_NAME, credentialsStreamMock));
  }

  @Test
  public void testGetLoggingService() {
    LogTail logTailMock = getLogTailMock();
    final LoggingOptions.Builder loggingOptionsBuilderMock = Mockito.mock(
        LoggingOptions.Builder.class);
    final LoggingOptions loggingOptionsMock = Mockito.mock(LoggingOptions.class);
    final Logging loggingMock = Mockito.mock(Logging.class);
    final InputStream credentialsStreamMock = Mockito.mock(InputStream.class);
    final ServiceAccountCredentials serviceAccountCredentialsMock = Mockito.mock(
        ServiceAccountCredentials.class);

    doReturn(loggingOptionsBuilderMock).when(logTailMock).getLoggingOptionsBuilder();
    try {
      doReturn(serviceAccountCredentialsMock).when(
          logTailMock).getServiceAccountCredentialsFromStream(credentialsStreamMock);
    } catch (IOException e) {
      fail(e);
    }
    doReturn(loggingOptionsBuilderMock).when(loggingOptionsBuilderMock).setCredentials(any(
        ServiceAccountCredentials.class));
    doReturn(loggingOptionsBuilderMock).when(loggingOptionsBuilderMock)
        .setProjectId(eq(TEST_PROJECT_NAME));
    doReturn(loggingOptionsMock).when(loggingOptionsBuilderMock).build();
    doReturn(loggingMock).when(loggingOptionsMock).getService();

    assertEquals(loggingMock,
        logTailMock.getLoggingService(TEST_PROJECT_NAME, credentialsStreamMock));
  }

  @Test
  public void testGetCloudLogStreamWhenFilter() {
    LogTail logTailMock = getLogTailMock();
    Logging loggingMock = Mockito.mock(Logging.class);
    InputStream streamMock = Mockito.mock(InputStream.class);
    LogEntryServerStream logEntryServerStreamMock = Mockito.mock(LogEntryServerStream.class);

    doReturn(streamMock).when(logTailMock).getCredentialsStream(eq(TEST_KEY_FILENAME));
    doReturn(loggingMock).when(logTailMock)
        .getLoggingService(eq(TEST_PROJECT_NAME), eq(streamMock));

    doReturn(logEntryServerStreamMock).when(loggingMock)
        .tailLogEntries(any(TailOption.class), any(TailOption.class));

    assertEquals(logEntryServerStreamMock, logTailMock.getCloudLogStream("meaningless filter"));
  }

  @Test
  public void testGetCloudLogStreamWhenNoFilter() {
    LogTail logTailMock = getLogTailMock();
    Logging loggingMock = Mockito.mock(Logging.class);
    InputStream streamMock = Mockito.mock(InputStream.class);
    LogEntryServerStream logEntryServerStreamMock = Mockito.mock(LogEntryServerStream.class);

    doReturn(streamMock).when(logTailMock).getCredentialsStream(eq(TEST_KEY_FILENAME));
    doReturn(loggingMock).when(logTailMock)
        .getLoggingService(eq(TEST_PROJECT_NAME), eq(streamMock));

    doReturn(logEntryServerStreamMock).when(loggingMock).tailLogEntries(any(TailOption.class));

    assertEquals(logEntryServerStreamMock, logTailMock.getCloudLogStream(null));
  }

  @Test
  public void testMain() throws org.apache.commons.cli.ParseException {
    LogTail logTailMock = getLogTailMock();
    doNothing().when(logTailMock).tailLogs();
    LogTail.mainUnderTest(TEST_MAIN_ARGS, logTailMock);
  }

  @Test
  public void testMainNoKeyFilename() {
    String[] args = {"-p", TEST_PROJECT_NAME};
    assertThrows(RuntimeException.class, () -> LogTail.main(args));
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

    doReturn(payloadMock).when(logEntryMock).getPayload();
    doReturn(dataMock).when(payloadMock).getData();
    doReturn("type.googleapis.com/google.cloud.audit.AuditLog").when(dataMock).getTypeUrl();
    doReturn(byteStringMock).when(dataMock).getValue();
    doReturn(byteArray).when(byteStringMock).toByteArray();

    AuditLog auditLogMock = Mockito.mock(AuditLog.class);
    try {
      doReturn(auditLogMock).when(logTailMock).getAuditLogParseFrom(any(byte[].class));
    } catch (InvalidProtocolBufferException e) {
      fail(e);
    }

    LogTailEntry entryTest = getLogTailEntryTest();
    doReturn(entryTest.timestamp).when(logEntryMock).getInstantTimestamp();
    doReturn(entryTest.methodName).when(auditLogMock).getMethodName();
    doReturn(entryTest.serviceName).when(auditLogMock).getServiceName();
    doReturn(entryTest.resourceName).when(auditLogMock).getResourceName();
    Status statusMock = Mockito.mock(Status.class);
    doReturn(statusMock).when(auditLogMock).getStatus();
    doReturn(entryTest.statusCode).when(statusMock).getCode();
    doReturn(entryTest.statusMessage).when(statusMock).getMessage();
    doReturn(entryTest.severity).when(logEntryMock).getSeverity();

    logTailMock.logTimeSeries = Mockito.mock(LogTimeSeries.class);
    ArgumentCaptor<LogTailEntry> argumentCaptor = ArgumentCaptor.forClass(LogTailEntry.class);
    doNothing().when(logTailMock.logTimeSeries).add(argumentCaptor.capture());
    try {
      doNothing().when(logTailMock.logTimeSeries)
          .maybeEmitMetrics(any(LogTailOutput.class));
    } catch (IOException e) {
      fail(e);
    }

    logTailMock.processLogEntryError(logEntryMock);
    assertTrue(argumentCaptor.getValue().equals(entryTest));
  }

  @Test
  public void testProcessLogEntryErrorWhenInvalidProtocolBufferException() {
    LogTail logTailMock = getLogTailMock();

    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    ProtoPayload payloadMock = Mockito.mock(ProtoPayload.class);
    Any dataMock = Mockito.mock(Any.class);
    ByteString byteStringMock = Mockito.mock(ByteString.class);
    byte[] byteArray = {0};

    when(logEntryMock.getPayload()).thenReturn(payloadMock);
    when(payloadMock.getData()).thenReturn(dataMock);
    when(dataMock.getTypeUrl())
        .thenReturn("type.googleapis.com/google.cloud.audit.AuditLog");
    when(dataMock.getValue()).thenReturn(byteStringMock);
    when(byteStringMock.toByteArray()).thenReturn(byteArray);

    try {
      doThrow(InvalidProtocolBufferException.class).when(logTailMock)
          .getAuditLogParseFrom(any(byte[].class));
    } catch (InvalidProtocolBufferException e) {
      fail(e);
    }

    logTailMock.processLogEntryError(logEntryMock);
    verify(logTailMock).error(anyString());
  }

  /*
   * TODO: Fix this test.
   *
   * This test false-passes. The test code:
   *
   *  doThrow(IOException.class)
   *    .when(logTailMock.logTimeSeries).maybeEmitMetrics(...)
   *
   * Does not actually program the method to return an exception.
   */
  @Test
  public void testProcessLogEntryErrorWhenIoException() {
    LogTail logTailMock = getLogTailMock();

    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    ProtoPayload payloadMock = Mockito.mock(ProtoPayload.class);
    Any dataMock = Mockito.mock(Any.class);
    ByteString byteStringMock = Mockito.mock(ByteString.class);
    byte[] byteArray = {0};

    doReturn(payloadMock).when(logEntryMock).getPayload();
    doReturn(dataMock).when(payloadMock).getData();
    doReturn("type.googleapis.com/google.cloud.audit.AuditLog").when(dataMock).getTypeUrl();
    doReturn(byteStringMock).when(dataMock).getValue();
    doReturn(byteArray).when(byteStringMock).toByteArray();

    AuditLog auditLogMock = Mockito.mock(AuditLog.class);
    try {
      doReturn(auditLogMock).when(logTailMock).getAuditLogParseFrom(any(byte[].class));
    } catch (InvalidProtocolBufferException e) {
      fail(e);
    }

    LogTailEntry entryTest = getLogTailEntryTest();
    doReturn(entryTest.timestamp).when(logEntryMock).getInstantTimestamp();
    doReturn(entryTest.methodName).when(auditLogMock).getMethodName();
    doReturn(entryTest.serviceName).when(auditLogMock).getServiceName();
    doReturn(entryTest.resourceName).when(auditLogMock).getResourceName();
    Status statusMock = Mockito.mock(Status.class);
    doReturn(statusMock).when(auditLogMock).getStatus();
    doReturn(entryTest.statusCode).when(statusMock).getCode();
    doReturn(entryTest.statusMessage).when(statusMock).getMessage();
    doReturn(entryTest.severity).when(logEntryMock).getSeverity();

    logTailMock.logTimeSeries = Mockito.mock(LogTimeSeries.class);
    doNothing().when(logTailMock.logTimeSeries).add(any(LogTailEntry.class));

    try {
      doThrow(IOException.class).when(logTailMock.logTimeSeries)
          .maybeEmitMetrics(any(LogTailOutput.class));
    } catch (Exception e) {
      fail(e);
    }
    doNothing().when(logTailMock).error(anyString());
    logTailMock.processLogEntryError(logEntryMock);
  }

  @Test
  public void testProcessLogEntryErrorWhenNotAuditPayload() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    ProtoPayload payloadMock = Mockito.mock(ProtoPayload.class);
    Any dataMock = Mockito.mock(Any.class);
    when(logEntryMock.getPayload()).thenReturn(payloadMock);
    when(payloadMock.getData()).thenReturn(dataMock);
    when(dataMock.getTypeUrl())
        .thenReturn("type.googleapis.com/google.cloud.audit.AuditLog_INVALID");
    doNothing().when(logTailMock).error("Log payload is not AuditLog");
    logTailMock.processLogEntryError(logEntryMock);
  }

  @Test
  public void testProcessLogEntryWhenFunctionUdmiConfig() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
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
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
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
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
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
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    when(logEntryMock.getSeverity()).thenReturn(Severity.ERROR);
    doNothing().when(logTailMock).processLogEntryError(logEntryMock);
    logTailMock.processLogEntry(logEntryMock);
  }

  @Test
  public void testProcessLogStream() {
    LogTail logTailMock = getLogTailMock();
    LogEntryServerStream streamMock = Mockito.mock(LogEntryServerStream.class);
    Iterator iteratorMock = Mockito.mock(Iterator.class);
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    doReturn(iteratorMock).when(streamMock).iterator();
    doReturn(true, false).when(iteratorMock).hasNext();
    doReturn(logEntryMock).when(iteratorMock).next();
    doNothing().when(logTailMock).processLogEntry(logEntryMock);
    logTailMock.processLogStream(streamMock);
  }

  @Test
  public void testTailLogs() {
    LogTail logTailMock = getLogTailMock();
    LogEntryServerStream streamMock = Mockito.mock(LogEntryServerStream.class);
    doReturn(streamMock).when(logTailMock)
        .getCloudLogStream(eq(logTailMock.LOG_FILTER));
    doNothing().when(logTailMock).processLogStream(streamMock);
    assertTrue(logTailMock.outputJson);
    logTailMock.tailLogs();
    verify(logTailMock).processLogStream(streamMock);
    assertEquals(logTailMock.output.getClass(), LogTailJsonOutput.class);
  }
}
