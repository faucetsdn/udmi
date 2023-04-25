package com.google.bos.udmi.service.messaging.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static com.google.udmi.util.JsonUtil.JSON_EXT;
import static com.google.udmi.util.JsonUtil.loadMap;

import com.google.bos.udmi.service.messaging.MessagePipe;
import com.google.udmi.util.GeneralUtils;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Envelope.SubType;

public class FileMessagePipe extends MessageBase {

  private final DirectoryTraverser traverser;
  private ExecutorService playback;

  public FileMessagePipe(EndpointConfiguration config) {
    String hostname = checkNotNull(config.hostname, "path not defined in hostname field");
    traverser = new DirectoryTraverser(hostname);
    ifNotNullThen(config.recv_id, this::playbackEngine);
  }

  private void playbackEngine(String recvId) {
    playback = Executors.newSingleThreadExecutor();
    playback.submit(() -> {
      traverser.stream().forEach(this::publishFile);
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

  private Bundle makeErrorBundle(Envelope envelope, Exception e) {
    envelope.subFolder = SubFolder.ERROR;
    return new Bundle(envelope, GeneralUtils.stackTraceString(e));
  }

  private Envelope makeEnvelope(File file) {
    try {
      String name = file.getName();
      String[] split = name.split("[._]", 4);
      if (split.length != 4 || !JSON_EXT.equals(split[3])) {
        throw new IllegalArgumentException("Malformed trace parts: " + CSV_JOINER.join(split));
      }
      Envelope envelope = new Envelope();
      envelope.subType = SubType.fromValue(split[1]);
      envelope.subFolder = SubFolder.fromValue(split[2]);
      String[] fileParts = file.getAbsolutePath().split(File.separator);
      envelope.deviceId = fileParts[fileParts.length - 2];
      envelope.deviceRegistryId = fileParts[fileParts.length - 3];
      envelope.projectId = fileParts[fileParts.length - 4];
      return envelope;
    } catch (Exception e) {
      throw new RuntimeException("While extracting envelope from " + file.getAbsolutePath());
    }
  }

  public static MessagePipe fromConfig(EndpointConfiguration config) {
    return new FileMessagePipe(config);
  }

  @Override
  public void publish(Bundle bundle) {
    throw new IllegalStateException("Not yet implemented");
  }
}
