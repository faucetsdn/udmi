package com.google.bos.udmi.service.messaging.impl;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.JSON_EXT;
import static com.google.udmi.util.JsonUtil.loadFileString;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static com.google.udmi.util.JsonUtil.toStringMap;
import static java.lang.String.format;

import com.google.bos.udmi.service.messaging.MessagePipe;
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
  private static final long FILE_PLAYBACK_MESSAGE_DELAY_MS = 50;
  private final Map<File, AtomicInteger> traceCounts = new HashMap<>();
  private File outFileRoot;

  public FileMessagePipe(EndpointConfiguration config) {
    ifNotNullThen(config.recv_id, this::playbackEngine);
    ifNotNullThen(config.send_id, this::fileOutHandler);
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
      throw new RuntimeException("While extracting envelope from " + file.getAbsolutePath(), e);
    }
  }

  private void playbackEngine(String recvId) {
    debug("Playback trace messages from " + new File(recvId).getAbsolutePath());
    DirectoryTraverser filesIn = new DirectoryTraverser(recvId);
    ExecutorService playback = Executors.newSingleThreadExecutor();
    playback.submit(() -> {
      filesIn.stream().forEach(this::consumeFile);
      terminateHandlers();
    });
  }

  private void consumeFile(File file) {
    try {
      Envelope envelope = makeEnvelope(file);
      receiveMessage(toStringMap(envelope), loadFileString(file));
      // Processing of received messages is asynchronous, so add a small artificial delay.
      safeSleep(FILE_PLAYBACK_MESSAGE_DELAY_MS);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void fileOutHandler(String sendId) {
    outFileRoot = new File(sendId);
    debug("Writing trace messages to " + outFileRoot.getAbsolutePath());
  }

  @Override
  protected void publishRaw(Bundle bundle) {
    if (outFileRoot == null) {
      throw new IllegalStateException("trace out file not defined, no send_id");
    }
    Envelope envelope = bundle.envelope;
    File outDir = new File(outFileRoot,
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
