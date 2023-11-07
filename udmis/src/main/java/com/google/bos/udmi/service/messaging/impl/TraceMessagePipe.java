package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.Common.DEVICE_ID_KEY;
import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.writeFile;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.common.collect.ImmutableMap;
import com.google.udmi.util.Common;
import com.google.udmi.util.JsonUtil;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.Nullable;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * Basic file message pipe that reads from simple json files encoded with type/folder in the
 * filename. Not the same as a message "trace" which is a more complete json structure with all
 * attributes and other values.
 */
public class TraceMessagePipe extends MessageBase {

  private static final String DEVICES_DIR_NAME = "devices";
  private static final Map<String, String> FOLDER_HACKS = new HashMap<>();

  static {
    // Some egregious hacks for dealing with legacy corner-cases data streams.
    FOLDER_HACKS.put("discover", "discovery");
    FOLDER_HACKS.put("", null);
  }

  private final Map<File, AtomicInteger> traceCounts = new HashMap<>();
  private File traceOutFile;

  /**
   * Create a trace replay pipe for the given configuration.
   */
  public TraceMessagePipe(EndpointConfiguration config) {
    ifNotNullThen(config.recv_id, this::playbackEngine);
    ifNotNullThen(config.send_id, this::traceOutHandler);
  }

  public static MessagePipe fromConfig(EndpointConfiguration config) {
    return new TraceMessagePipe(config);
  }

  private void consumeTrace(File file) {
    try {
      Map<String, Object> traceBundle = asMap(file);
      Envelope envelope = makeEnvelope(traceBundle);
      Map<String, Object> message = asMap(decodeBase64((String) traceBundle.get("data")));
      receiveMessage(envelope, message);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Nullable
  private SubFolder getBundleSubfolder(Map<String, String> attributes) {
    String subFolder = attributes.get("subFolder");
    return ifNotNullGet(FOLDER_HACKS.getOrDefault(subFolder, subFolder), SubFolder::fromValue);
  }

  private Envelope makeEnvelope(Map<String, Object> bundle) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, String> attributes = (Map<String, String>) bundle.get("attributes");
      Envelope envelope = new Envelope();
      envelope.subFolder = getBundleSubfolder(attributes);
      envelope.subType = ifNotNullGet(attributes.get("subType"), SubType::fromValue);
      envelope.deviceId = attributes.get(DEVICE_ID_KEY);
      envelope.projectId = attributes.get("projectId");
      envelope.deviceRegistryId = attributes.get("deviceRegistryId");
      envelope.publishTime = ifNotNullGet(ofNullable(attributes.get(Common.PUBLISH_TIME_KEY))
          .orElse((String) bundle.get("publish_time")), JsonUtil::getDate);
      return envelope;
    } catch (Exception e) {
      throw new RuntimeException("While extracting envelope from bundle", e);
    }
  }

  private void playbackEngine(String recvId) {
    debug("Playback trace messages from " + new File(recvId).getAbsolutePath());
    DirectoryTraverser traceIn = new DirectoryTraverser(recvId);
    ExecutorService playback = Executors.newSingleThreadExecutor();
    playback.submit(() -> {
      traceIn.stream().forEach(this::consumeTrace);
      terminateHandlers();
    });
  }

  private void traceOutHandler(String sendId) {
    traceOutFile = new File(sendId);
    debug("Writing trace messages to " + traceOutFile.getAbsolutePath());
  }

  @Override
  public void publish(Bundle bundle) {
    if (traceOutFile == null) {
      throw new IllegalStateException("trace out file not defined, no send_id");
    }
    Envelope envelope = bundle.envelope;
    String publishTime =
        envelope.publishTime == null ? getTimestamp() : getTimestamp(envelope.publishTime);
    int endMark = publishTime.lastIndexOf(".");
    String useTime = publishTime.substring(0, endMark >= 0 ? endMark : publishTime.length());
    String timePath = useTime.replaceAll("[T:Z]", "/");
    File outDir = new File(traceOutFile, timePath);
    outDir.mkdirs();
    int messageCount =
        traceCounts.computeIfAbsent(outDir, key -> new AtomicInteger()).incrementAndGet();
    File outFile = new File(outDir, format("%s_%06d.json", publishTime, messageCount));
    Map<String, Object> outputMap = ImmutableMap.of(
        "data", encodeBase64(stringify(bundle.message)),
        "attributes", envelope
    );
    writeFile(outputMap, outFile);
  }
}
