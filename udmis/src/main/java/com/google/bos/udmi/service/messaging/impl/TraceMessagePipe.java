package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.asMap;
import static java.lang.String.format;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.udmi.util.GeneralUtils;
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
      try {
        Object message = asMap(decodeBase64((String) traceBundle.get("data")));
        receiveBundle(new Bundle(envelope, message));
      } catch (Exception e) {
        receiveBundle(makeErrorBundle(envelope, e));
      }
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
      envelope.deviceId = attributes.get("deviceId");
      envelope.projectId = attributes.get("projectId");
      envelope.deviceRegistryId = attributes.get("deviceRegistryId");
      return envelope;
    } catch (Exception e) {
      throw new RuntimeException("While extracting envelope from bundle", e);
    }
  }

  private Bundle makeErrorBundle(Envelope envelope, Exception e) {
    envelope.subFolder = SubFolder.ERROR;
    return new Bundle(envelope, GeneralUtils.stackTraceString(e));
  }

  private void playbackEngine(String recvId) {
    debug("Playback trace messages from " + new File(recvId).getAbsolutePath());
    DirectoryTraverser traceIn = new DirectoryTraverser(recvId);
    ExecutorService playback = Executors.newSingleThreadExecutor();
    playback.submit(() -> {
      traceIn.stream().forEach(this::consumeTrace);
      terminateHandler();
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
    File outDir = new File(traceOutFile,
        format("%s/%s/%s/%s", envelope.projectId, envelope.deviceRegistryId, DEVICES_DIR_NAME,
            envelope.deviceId));
    outDir.mkdirs();
    int messageCount =
        traceCounts.computeIfAbsent(outDir, key -> new AtomicInteger()).incrementAndGet();
    File outFile = new File(outDir,
        format("%03d_%s_%s.json", messageCount, envelope.subType, envelope.subFolder));
    JsonUtil.writeFile(bundle.message, outFile);
  }
}
