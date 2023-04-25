package com.google.bos.udmi.service.messaging.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.udmi.util.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.PointsetEvent;
import udmi.schema.SystemEvent;

class FileMessagePipeTest {

  private static final String TRACE_FILES = "../tests/simple.trace/devices";
  private static final String TRACE_RECV = "unknown";

  List<Bundle> consumed = new ArrayList<>();

  @Test
  public void basicPlayback() {
    FileMessagePipe fileMessagePipe = new FileMessagePipe(getTestConfig());
    fileMessagePipe.activate(consumed::add);
    fileMessagePipe.awaitShutdown();

    assertEquals(12, consumed.size(), "playback messages");

    List<String> errors =
        consumed.stream().filter(bundle -> bundle.envelope.subFolder == SubFolder.ERROR)
            .map(bundle -> (String) bundle.message)
            .collect(Collectors.toList());
    assertEquals(1, errors.size(), "expected message errors");

    Set<String> devices =
        consumed.stream().map(bundle -> bundle.envelope.deviceId).collect(Collectors.toSet());
    assertEquals(4, devices.size(), "expected devices in trace");

    SystemEvent systemEvent = JsonUtil.convertTo(SystemEvent.class, consumed.get(1).message);
    assertEquals("device.testing", systemEvent.logentries.get(0).category, "log entry category for second message");
  }

  private EndpointConfiguration getTestConfig() {
    EndpointConfiguration endpointConfiguration = new EndpointConfiguration();
    endpointConfiguration.protocol = Protocol.FILE;
    endpointConfiguration.hostname = TRACE_FILES;
    endpointConfiguration.recv_id = TRACE_RECV;
    return endpointConfiguration;
  }
}