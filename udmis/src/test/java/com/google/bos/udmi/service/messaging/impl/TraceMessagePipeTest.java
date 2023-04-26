package com.google.bos.udmi.service.messaging.impl;

import static com.google.bos.udmi.service.messaging.impl.FileMessagePipe.DEVICES_DIR_NAME;
import static com.google.udmi.util.JsonUtil.loadStrict;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointsetEvent;
import udmi.schema.SystemEvent;

class TraceMessagePipeTest {

  private static final String SIMPLE_TRACE = "traces/legacy-discovery";
  private static final String TRACE_OUT = "out/trace_pipe/";
  private static final String TEST_REGISTRY = "TEST_REGISTRY";
  private static final String TEST_PROJECT = "TEST_PROJECT";
  private static final String DEVICE_ONE = "one";
  private static final String DEVICE_TWO = "two";
  private static final String TEST_POINT = "test_point";
  private static final String VALUE_ONE = "value1";
  private static final String VALUE_TWO = "value2";
  private static final String TEST_FILENAME = "002_event_pointset.json";

  private static final File DEVICES_BASE =
      new File(format("%s/%s/%s/%s", TRACE_OUT, TEST_PROJECT, TEST_REGISTRY, DEVICES_DIR_NAME));
  public static final File TRACES_TWO = new File(DEVICES_BASE, DEVICE_TWO);
  public static final File TRACES_ONE = new File(DEVICES_BASE, DEVICE_ONE);
  public static final String TEST_DEVICE = "bacnet-3104810";

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
    envelope.subType = SubType.EVENT;
    envelope.deviceId = deviceId;
    envelope.deviceRegistryId = TEST_REGISTRY;
    envelope.projectId = TEST_PROJECT;
    PointsetEvent message = new PointsetEvent();
    message.points = new HashMap<>();
    message.points.computeIfAbsent(TEST_POINT, key -> new PointPointsetEvent()).present_value =
        presentValue;
    return new Bundle(envelope, message);
  }

  @Test
  public void traceOutput() throws IOException {
    deleteDirectory(new File(TRACE_OUT));
    assertFalse(new File(TRACES_ONE, TEST_FILENAME).exists());

    TraceMessagePipe fileMessagePipe = new TraceMessagePipe(getTraceOutConfig());
    fileMessagePipe.publish(traceOutBundle(DEVICE_ONE, VALUE_ONE));
    fileMessagePipe.publish(traceOutBundle(DEVICE_ONE, VALUE_TWO));
    fileMessagePipe.publish(traceOutBundle(DEVICE_TWO, VALUE_ONE));

    assertTrue(new File(TRACES_ONE, TEST_FILENAME).exists());

    List<File> files1 =
        Arrays.stream(requireNonNull(TRACES_ONE.listFiles())).sorted().collect(Collectors.toList());
    assertEquals(2, files1.size(), "expected device one trace files");
    assertEquals(TEST_FILENAME, files1.get(1).getName(), "trace output filename");
    PointsetEvent pointsetEvent = loadStrict(PointsetEvent.class, files1.get(1));
    assertEquals("value2", pointsetEvent.points.get(TEST_POINT).present_value,
        "point present value");

    assertEquals(1, requireNonNull(TRACES_TWO.listFiles()).length,
        "expected device two trace files");
  }

  @Test
  public void tracePlayback() {
    TraceMessagePipe fileMessagePipe = new TraceMessagePipe(getTraceInConfig());
    fileMessagePipe.activate(consumed::add);
    fileMessagePipe.awaitShutdown();

    assertEquals(89, consumed.size(), "playback messages");

    List<String> errors =
        consumed.stream().filter(bundle -> bundle.envelope.subFolder == SubFolder.ERROR)
            .map(bundle -> (String) bundle.message)
            .collect(Collectors.toList());
    assertEquals(0, errors.size(), "expected message errors");

    Set<String> devices =
        consumed.stream().map(bundle -> bundle.envelope.deviceId).collect(Collectors.toSet());
    assertEquals(71, devices.size(), "expected devices in trace");

    long deviceCount =
        consumed.stream().filter(bundle -> TEST_DEVICE.equals(extractMessage(bundle).get("device")))
            .count();
    assertEquals(2, deviceCount, "expected count of device " + TEST_DEVICE);
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> extractMessage(Bundle bundle) {
    return (Map<String, String>) bundle.message;
  }

  private Object getBundleMessage(Bundle bundle) {
    return bundle.message;
  }
}