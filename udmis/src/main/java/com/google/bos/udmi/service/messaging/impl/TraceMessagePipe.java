package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.Common.PUBLISH_TIME_KEY;
import static com.google.udmi.util.GeneralUtils.decodeBase64;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.asMap;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.JsonUtil.writeFile;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;

/**
 * Basic file message pipe that reads from simple json files encoded with type/folder in the
 * filename. Not the same as a message "trace" which is a more complete json structure with all
 * attributes and other values.
 */
public class TraceMessagePipe extends MessageBase {

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
      Map<String, String> envelopeMap = makeEnvelope(traceBundle);
      Map<String, Object> message = asMap(decodeBase64((String) traceBundle.get("data")));
      receiveMessage(envelopeMap, message);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private Map<String, String> makeEnvelope(Map<String, Object> bundle) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, String> attributes = (Map<String, String>) bundle.get("attributes");
      attributes.put(PUBLISH_TIME_KEY,
          ofNullable(attributes.get(PUBLISH_TIME_KEY)).orElse((String) bundle.get("publish_time")));
      return attributes;
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
  protected void publishRaw(Bundle bundle) {
    if (traceOutFile == null) {
      throw new IllegalStateException("trace out file not defined, no send_id");
    }
    Envelope envelope = bundle.envelope;
    String publishTime =
        envelope.publishTime == null ? getTimestamp() : isoConvert(envelope.publishTime);
    int endMark = publishTime.lastIndexOf(":");
    String useTime = publishTime.substring(0, endMark >= 0 ? endMark : publishTime.length());
    String timePath = useTime.replaceAll("[T:Z]", "/");
    File outDir = new File(traceOutFile, timePath);
    outDir.mkdirs();
    int messageCount =
        traceCounts.computeIfAbsent(outDir, key -> new AtomicInteger()).incrementAndGet();
    String sanitized = publishTime.replaceAll(":", "x");
    File outFile = new File(outDir, format("%s_%06d.json", sanitized, messageCount));
    Map<String, Object> outputMap = ImmutableMap.of(
        "data", encodeBase64(stringify(bundle.message)),
        "attributes", envelope
    );
    writeFile(outputMap, outFile);
  }
}
