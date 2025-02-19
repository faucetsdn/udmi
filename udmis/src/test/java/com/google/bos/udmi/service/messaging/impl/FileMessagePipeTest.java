package com.google.bos.udmi.service.messaging.impl;

import static com.google.bos.udmi.service.messaging.impl.FileMessagePipe.DEVICES_DIR_NAME;
import static com.google.udmi.util.JsonUtil.loadFileStrict;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.PointPointsetEvents;
import udmi.schema.PointsetEvents;
import udmi.schema.SystemEvents;

class FileMessagePipeTest extends MessageTestCore {

  private static final String SIMPLE_TRACE = "../tests/traces/simple/devices";
  private static final String TRACE_OUT = "out/test.trace/";
  private static final String TEST_REGISTRY = "TEST_REGISTRY";
  private static final String TEST_PROJECT = "TEST_PROJECT";
  private static final String DEVICE_ONE = "one";
  private static final String DEVICE_TWO = "two";
  private static final String TEST_POINT = "test_point";
  private static final String VALUE_ONE = "value1";
  private static final String VALUE_TWO = "value2";
  private static final String TEST_FILENAME = "002_events_pointset.json";

  private static final File DEVICES_BASE =
      new File(format("%s/%s/%s/%s", TRACE_OUT, TEST_PROJECT, TEST_REGISTRY, DEVICES_DIR_NAME));
  public static final File TRACES_TWO = new File(DEVICES_BASE, DEVICE_TWO);
  public static final File TRACES_ONE = new File(DEVICES_BASE, DEVICE_ONE);

  private final List<Bundle> consumed = new ArrayList<>();

  private EndpointConfiguration getTraceInConfig() {
    EndpointConfiguration endpointConfiguration = new EndpointConfiguration();
    endpointConfiguration.protocol = Protocol.FILE;
    endpointConfiguration.recv_id = SIMPLE_TRACE;
    return endpointConfiguration;
  }

  private EndpointConfiguration getTraceOutConfig() {
    EndpointConfiguration endpointConfiguration = new EndpointConfiguration();
    endpointConfiguration.protocol = Protocol.FILE;
    endpointConfiguration.send_id = TRACE_OUT;
    return endpointConfiguration;
  }

  private Bundle traceOutBundle(String deviceId, Object presentValue) {
    Envelope envelope = new Envelope();
    envelope.subFolder = SubFolder.POINTSET;
    envelope.subType = SubType.EVENTS;
    envelope.deviceId = deviceId;
    envelope.deviceRegistryId = TEST_REGISTRY;
    envelope.projectId = TEST_PROJECT;
    PointsetEvents message = new PointsetEvents();
    message.points = new HashMap<>();
    message.points.computeIfAbsent(TEST_POINT, key -> new PointPointsetEvents()).present_value =
        presentValue;
    return new Bundle(envelope, message);
  }

  @Test
  public void traceOutput() throws IOException {
    deleteDirectory(new File(TRACE_OUT));
    assertFalse(new File(TRACES_ONE, TEST_FILENAME).exists());

    FileMessagePipe fileMessagePipe = new FileMessagePipe(getTraceOutConfig());
    fileMessagePipe.publish(traceOutBundle(DEVICE_ONE, VALUE_ONE));
    fileMessagePipe.publish(traceOutBundle(DEVICE_ONE, VALUE_TWO));
    fileMessagePipe.publish(traceOutBundle(DEVICE_TWO, VALUE_ONE));

    assertTrue(new File(TRACES_ONE, TEST_FILENAME).exists());

    List<File> files =
        Arrays.stream(requireNonNull(TRACES_ONE.listFiles())).sorted().collect(Collectors.toList());
    assertEquals(2, files.size(), "expected device one trace files");
    assertEquals(TEST_FILENAME, files.get(1).getName(), "trace output filename");
    PointsetEvents pointsetEvent = loadFileStrict(PointsetEvents.class, files.get(1));
    assertEquals("value2", pointsetEvent.points.get(TEST_POINT).present_value,
        "point present value");

    assertEquals(1, requireNonNull(TRACES_TWO.listFiles()).length,
        "expected device two trace files");
  }

  @Test
  public void tracePlayback() {
    FileMessagePipe fileMessagePipe = new FileMessagePipe(getTraceInConfig());
    fileMessagePipe.activate(consumed::add);
    fileMessagePipe.awaitShutdown();

    assertEquals(15, consumed.size(), "playback messages");

    List<String> errors =
        consumed.stream().filter(bundle -> bundle.message instanceof String)
            .map(bundle -> (String) bundle.message).toList();
    assertEquals(1, errors.size(), "expected message errors");

    Set<String> devices =
        consumed.stream().filter(bundle -> bundle.envelope.deviceId != null)
            .map(bundle -> bundle.envelope.deviceId).collect(Collectors.toSet());
    assertEquals(4, devices.size(), "expected devices in trace");

    assertEquals(SubFolder.SYSTEM, consumed.get(1).envelope.subFolder, "expecting system event");
    SystemEvents systemEvent = JsonUtil.convertTo(SystemEvents.class, consumed.get(1).message);
    assertEquals("device.testing", systemEvent.logentries.get(0).category,
        "log entry category for second message");
  }
}
