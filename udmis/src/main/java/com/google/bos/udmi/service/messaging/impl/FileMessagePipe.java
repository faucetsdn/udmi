package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.JSON_EXT;
import static com.google.udmi.util.JsonUtil.loadMap;
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
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

/**
 * Basic file message pipe that reads from simple json files encoded with type/folder in the
 * filename. Not the same as a message "trace" which is a more complete json structure with all
 * attributes and other values.
 */
public class FileMessagePipe extends MessageBase {

  public static final String DEVICES_DIR_NAME = "devices";
  private final Map<File, AtomicInteger> traceCounts = new HashMap<>();
  private File traceOutFile;

  public FileMessagePipe(EndpointConfiguration config) {
    ifNotNullThen(config.recv_id, this::playbackEngine);
    ifNotNullThen(config.send_id, this::traceOutHandler);
  }

  public static MessagePipe fromConfig(EndpointConfiguration config) {
    return new FileMessagePipe(config);
  }

  private Envelope makeEnvelope(File file) {
    try {
      Envelope envelope = new Envelope();
      String[] fileParts = file.getAbsolutePath().split(File.separator);
      envelope.projectId = fileParts[fileParts.length - 5];
      envelope.deviceRegistryId = fileParts[fileParts.length - 4];
      String devicesDir = fileParts[fileParts.length - 3];
      if (!DEVICES_DIR_NAME.equals(devicesDir)) {
        throw new IllegalStateException("Unexpected path element " + devicesDir);
      }
      envelope.deviceId = fileParts[fileParts.length - 2];

      String name = fileParts[fileParts.length - 1];
      String[] split = name.split("[._]", 4);
      if (split.length != 4 || !JSON_EXT.equals(split[3])) {
        throw new IllegalArgumentException("Malformed trace parts: " + CSV_JOINER.join(split));
      }
      envelope.subType = SubType.fromValue(split[1]);
      envelope.subFolder = SubFolder.fromValue(split[2]);

      return envelope;
    } catch (Exception e) {
      throw new RuntimeException("While extracting envelope from " + file.getAbsolutePath());
    }
  }

  private Bundle makeErrorBundle(Envelope envelope, Exception e) {
    envelope.subFolder = SubFolder.ERROR;
    return new Bundle(envelope, GeneralUtils.stackTraceString(e));
  }

  private void playbackEngine(String recvId) {
    DirectoryTraverser traceIn = new DirectoryTraverser(recvId);
    ExecutorService playback = Executors.newSingleThreadExecutor();
    playback.submit(() -> {
      traceIn.stream().forEach(this::publishFile);
      terminateHandler();
    });
  }

  private void publishFile(File file) {
    try {
      Envelope envelope = makeEnvelope(file);
      try {
        receiveBundle(new Bundle(envelope, loadMap(file)));
      } catch (Exception e) {
        receiveBundle(makeErrorBundle(envelope, e));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void traceOutHandler(String sendId) {
    traceOutFile = new File(sendId);
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
