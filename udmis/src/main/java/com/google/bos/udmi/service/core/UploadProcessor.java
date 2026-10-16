package com.google.bos.udmi.service.core;

import static com.google.udmi.util.GeneralUtils.ifNotNullThen;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import com.google.bos.udmi.service.messaging.MessageContinuation;
import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.bos.udmi.service.support.DataRef;
import com.google.bos.udmi.service.support.IotDataProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import udmi.schema.DiscoveryUpload;
import udmi.schema.EndpointConfiguration;
import udmi.schema.Envelope;
import udmi.schema.Envelope.SubType;

/**
 * Processor for device-to-cloud chunked binary uploads over the {@code upload} channel.
 *
 * <p>Stages incoming chunks in the shared {@link IotDataProvider} (Etcd) so multiple UDMIS pods
 * can receive chunks in any order and uses an atomic Compare-And-Swap election to ensure exactly
 * one pod reassembles and verifies the complete payload once all chunks and the negative EOF
 * boundary marker have arrived.
 */
@ComponentName("upload")
public class UploadProcessor extends ProcessorBase {

  public static final String DATABASE_COMPONENT = "database";
  public static final String CHUNK_KEY_PREFIX = "chunk_";
  public static final String EXPECTED_CHUNKS_KEY = "expected_chunks";
  public static final String EXPECTED_SHA256_KEY = "expected_sha256";
  public static final String STATUS_KEY = "status";
  public static final String STATUS_ASSEMBLING = "assembling";
  private static final String DEFAULT_UPLOAD_DIR = "/tmp/udmi_uploads";

  private IotDataProvider database;
  private File outputDir = new File(DEFAULT_UPLOAD_DIR);

  public UploadProcessor(EndpointConfiguration config) {
    super(config);
  }

  @Override
  public void activate() {
    database = UdmiServicePod.maybeGetComponent(DATABASE_COMPONENT);
    super.activate();
  }

  @Override
  protected SubType getExceptionSubType() {
    return SubType.UPLOAD;
  }

  public void setOutputDir(File outputDir) {
    this.outputDir = requireNonNull(outputDir, "outputDir not defined");
  }

  public File getOutputDir() {
    return outputDir;
  }

  /**
   * Handle discovery upload chunk messages on {@code upload/discovery}.
   */
  @MessageHandler
  public void discoveryUploadHandler(DiscoveryUpload message) {
    MessageContinuation continuation = getContinuation(message);
    Envelope envelope = continuation.getEnvelope();
    IotDataProvider dataProvider = database != null
        ? database
        : UdmiServicePod.maybeGetComponent(DATABASE_COMPONENT);
    requireNonNull(dataProvider, "Missing database component for upload reassembly");
    processDiscoveryUpload(envelope, message, dataProvider, outputDir);
  }

  /**
   * Stage a discovery upload chunk or boundary marker in shared {@link IotDataProvider} and
   * atomically reassemble the binary artifact once all chunks and the EOF marker are present.
   */
  public static File processDiscoveryUpload(
      Envelope envelope,
      DiscoveryUpload message,
      IotDataProvider dataProvider,
      File baseOutputDir) {
    String registryId = requireNonNull(envelope.deviceRegistryId, "missing deviceRegistryId");
    String deviceId = requireNonNull(envelope.deviceId, "missing deviceId");
    String sessionId = requireNonNull(message.session_id, "missing session_id");
    Integer eventNo = requireNonNull(message.event_no, "missing event_no");

    String sanitizedSession = sessionId.replaceAll("[:/]", "_");
    DataRef uploadRef = dataProvider.ref()
        .registry(registryId)
        .device(deviceId)
        .collection("upload_" + sanitizedSession);

    if (eventNo == 0) {
      Map<String, String> startPuts = new HashMap<>();
      startPuts.put("started", "true");
      ifNotNullThen(message.family, family -> startPuts.put("family", family));
      if (message.total_chunks != null && message.total_chunks > 0) {
        startPuts.put(EXPECTED_CHUNKS_KEY, String.valueOf(message.total_chunks));
      }
      uploadRef.update(startPuts, null);
      return null;
    } else if (eventNo > 0) {
      if (message.data == null || message.data.isEmpty()) {
        throw new IllegalArgumentException(
            format("Upload chunk event_no %d for session %s missing data payload",
                eventNo, sessionId));
      }
      Map<String, String> chunkPuts = new HashMap<>();
      chunkPuts.put(CHUNK_KEY_PREFIX + eventNo, message.data);
      if (message.total_chunks != null && message.total_chunks > 0) {
        chunkPuts.put(EXPECTED_CHUNKS_KEY, String.valueOf(message.total_chunks));
      }
      uploadRef.update(chunkPuts, null);
    } else {
      int expectedChunks = -(eventNo + 1);
      if (expectedChunks <= 0) {
        throw new IllegalArgumentException(
            format("Invalid terminal event_no %d for session %s", eventNo, sessionId));
      }
      if (message.sha256 == null || message.sha256.isEmpty()) {
        throw new IllegalArgumentException(
            format("Terminal upload marker event_no %d for session %s missing sha256",
                eventNo, sessionId));
      }
      Map<String, String> eofPuts = new HashMap<>();
      eofPuts.put(EXPECTED_CHUNKS_KEY, String.valueOf(expectedChunks));
      eofPuts.put(EXPECTED_SHA256_KEY, message.sha256);
      uploadRef.update(eofPuts, null);
    }

    Map<String, String> entries = uploadRef.entries();
    String expectedChunksStr = entries.get(EXPECTED_CHUNKS_KEY);
    String expectedSha256 = entries.get(EXPECTED_SHA256_KEY);
    if (expectedChunksStr == null || expectedSha256 == null) {
      return null;
    }

    int totalChunks = Integer.parseInt(expectedChunksStr);
    for (int i = 1; i <= totalChunks; i++) {
      if (!entries.containsKey(CHUNK_KEY_PREFIX + i)) {
        return null;
      }
    }

    if (!uploadRef.updateIfMatch(STATUS_KEY, null, Map.of(STATUS_KEY, STATUS_ASSEMBLING), null)) {
      return null;
    }

    Set<String> cleanupKeys = uploadRef.entries().keySet();
    try {
      ByteArrayOutputStream assembled = new ByteArrayOutputStream();
      for (int i = 1; i <= totalChunks; i++) {
        String chunkBase64 = entries.get(CHUNK_KEY_PREFIX + i);
        byte[] decoded = Base64.getDecoder().decode(chunkBase64);
        assembled.write(decoded);
      }
      byte[] payloadBytes = assembled.toByteArray();
      String actualSha256 = computeSha256Hex(payloadBytes);
      if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
        throw new IllegalStateException(format(
            "SHA-256 mismatch for upload session %s: expected %s, got %s",
            sessionId, expectedSha256, actualSha256));
      }

      File deviceOutputDir = new File(new File(baseOutputDir, registryId), deviceId);
      Files.createDirectories(deviceOutputDir.toPath());
      File outputFile = new File(deviceOutputDir, sanitizedSession + ".pcap");
      Files.write(outputFile.toPath(), payloadBytes);
      return outputFile;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(
          format("While reassembling upload session %s for %s/%s",
              sessionId, registryId, deviceId), e);
    } finally {
      uploadRef.update(null, cleanupKeys);
    }
  }

  static String computeSha256Hex(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(data));
    } catch (Exception e) {
      throw new RuntimeException("Unable to compute SHA-256 digest", e);
    }
  }
}
