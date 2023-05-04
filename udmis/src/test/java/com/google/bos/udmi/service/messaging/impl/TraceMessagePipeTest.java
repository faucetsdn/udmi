package com.google.bos.udmi.service.messaging.impl;

import static com.google.bos.udmi.service.messaging.impl.FileMessagePipe.DEVICES_DIR_NAME;
import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.fromString;
import static java.lang.String.format;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.bos.udmi.service.messaging.impl.MessageBase.Bundle;
import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.util.ArrayList;
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

public class TraceMessagePipeTest {

  public static final String TEST_DEVICE = "bacnet-3104810";
  private static final String SIMPLE_TRACE = "traces/legacy-discovery";
  private static final String TRACE_OUT = "out/trace_pipe/";
  public static final String TEST_REGISTRY = "TEST_REGISTRY";
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
  private final List<Bundle> consumed = new ArrayList<>();

  @SuppressWarnings("unchecked")
  private Map<String, String> extractMessage(Bundle bundle) {
    return (Map<String, String>) bundle.message;
  }

  private Object getBundleMessage(Bundle bundle) {
    return bundle.message;
  }

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
  public void traceOutput() throws Exception {
    deleteDirectory(new File(TRACE_OUT));

    TraceMessagePipe fileMessagePipe = new TraceMessagePipe(getTraceOutConfig());
    fileMessagePipe.publish(traceOutBundle(DEVICE_ONE, VALUE_ONE));
    fileMessagePipe.publish(traceOutBundle(DEVICE_ONE, VALUE_TWO));
    fileMessagePipe.publish(traceOutBundle(DEVICE_TWO, VALUE_ONE));

    DirectoryTraverser directoryTraverser = new DirectoryTraverser(TRACE_OUT);
    List<File> messages = directoryTraverser.stream().collect(Collectors.toList());
    assertEquals(3, messages.size(), "traced messages");

    Map<String, Object> stringObjectMap = JsonUtil.loadMap(messages.get(1));
    Envelope envelope = convertTo(Envelope.class, stringObjectMap.get("attributes"));
    assertEquals(DEVICE_ONE, envelope.deviceId, "received envelope deviceId");

    PointsetEvent pointsetEvent =
        fromString(PointsetEvent.class, decodeBase64((String) stringObjectMap.get("data")));
    assertEquals(VALUE_TWO, pointsetEvent.points.get(TEST_POINT).present_value,
        "received point value");
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
}