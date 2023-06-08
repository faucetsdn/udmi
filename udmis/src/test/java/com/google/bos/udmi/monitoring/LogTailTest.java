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
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Base64;
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
    Mockito.when(loggingMock.tailLogEntries(any(TailOption.class), any(TailOption.class)))
        .thenReturn(streamMock);
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

  @Test
  public void testProcessLogEntryError() {
    LogTail logTailMock = getLogTailMock();
    LogEntry logEntryMock = Mockito.mock(LogEntry.class);
    ProtoPayload payloadMock = Mockito.mock(ProtoPayload.class);
    Any dataMock = Mockito.mock(Any.class);
    ByteString byteStringMock = Mockito.mock(ByteString.class);
    String base64Data =
        "dHlwZV91cmw6ICJ0eXBlLmdvb2dsZWFwaXMuY29tL2dvb2dsZS5jbG91ZC5hdWRpdC5BdWRpdExv"
            + "ZyIKdmFsdWU6ICJcMDIyL1xiXHRcMDIyK0RldmljZSBgMjY1ODE0NzEzNjg0NjU2NmAgaXMgbm90"
            + "IGNvbm5lY3RlZC5cMDMyXDM0MlwwMDFcbjFlc3NlbnRpYWwta2VlcC0xOTc4MjJAYXBwc3BvdC5n"
            + "c2VydmljZWFjY291bnQuY29tMkFcbj9cbj1zZXJ2aWNlLTEwNDczMDg3NzQ2NzhAZ2NmLWFkbWlu"
            + "LXJvYm90LmlhbS5nc2VydmljZWFjY291bnQuY29tMkBcbj5cbjxzZXJ2aWNlLTcwMDQwNjc5OTA1"
            + "MkBnY3AtZ2FlLXNlcnZpY2UuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20yKFxuJlxuJGFwcC1lbmdp"
            + "bmUtYXBwc2VydmVyQHByb2QuZ29vZ2xlLmNvbVwiTVxuXDAxNzEwNy4xNzguMjAwLjIwNFwwMjIm"
            + "Z3JwYy1ub2RlLWpzLzEuMy4xLGd6aXAoZ2ZlKSxnemlwKGdmZSk6XDAyMEpcZlxiXDMxMlwzMTRc"
            + "MjA0XDI0NFwwMDZcMDIwXDIyN1wyMjZcMzQzXDIyNlwwMDNqXDAwMEJcMDAwOlwwMjdjbG91ZGlv"
            + "dC5nb29nbGVhcGlzLmNvbUI1Z29vZ2xlLmNsb3VkLmlvdC52MS5EZXZpY2VNYW5hZ2VyLlNlbmRD"
            + "b21tYW5kVG9EZXZpY2VKXDIwNFwwMDFcbmBwcm9qZWN0cy9lc3NlbnRpYWwta2VlcC0xOTc4MjIv"
            + "bG9jYXRpb25zL3VzLWNlbnRyYWwxL3JlZ2lzdHJpZXMvVURNUy1SRUZMRUNUL2RldmljZXMvVFct"
            + "TlRDLVRQS0RcMDIyXDAzNGNsb3VkaW90LmRldmljZXMuc2VuZENvbW1hbmRcMDMwXDAwMSpcMDAw"
            + "WmBwcm9qZWN0cy9lc3NlbnRpYWwta2VlcC0xOTc4MjIvbG9jYXRpb25zL3VzLWNlbnRyYWwxL3Jl"
            + "Z2lzdHJpZXMvVURNUy1SRUZMRUNUL2RldmljZXMvVFctTlRDLVRQS0RcMjAyXDAwMVwyNzNcMDAx"
            + "XG5NXG5cMDA1QHR5cGVcMDIyRFwwMzJCdHlwZS5nb29nbGVhcGlzLmNvbS9nb29nbGUuY2xvdWQu"
            + "aW90LnYxLlNlbmRDb21tYW5kVG9EZXZpY2VSZXF1ZXN0XG5qXG5cMDA0bmFtZVwwMjJiXDAzMmBw"
            + "cm9qZWN0cy9lc3NlbnRpYWwta2VlcC0xOTc4MjIvbG9jYXRpb25zL3VzLWNlbnRyYWwxL3JlZ2lz"
            + "dHJpZXMvVURNUy1SRUZMRUNUL2RldmljZXMvVFctTlRDLVRQS0QiCg==";

    byte[] byteArray = Base64.getDecoder().decode(base64Data);

    Mockito.when(logEntryMock.getPayload()).thenReturn(payloadMock);
    Mockito.when(payloadMock.getData()).thenReturn(dataMock);
    Mockito.when(dataMock.getTypeUrl())
        .thenReturn("type.googleapis.com/google.cloud.audit.AuditLog2");
/*
    Mockito.when(dataMock.getValue()).thenReturn(byteStringMock);
    Mockito.when(byteStringMock.toByteArray()).thenReturn(byteArray);

    logTailMock.logsTimeSeries = Mockito.mock(LogTimeSeries.class);
*/
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
