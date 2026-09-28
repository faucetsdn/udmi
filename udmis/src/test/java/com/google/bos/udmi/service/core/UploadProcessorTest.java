package com.google.bos.udmi.service.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.bos.udmi.service.pod.UdmiServicePod;
import com.google.bos.udmi.service.support.DataRef;
import com.google.bos.udmi.service.support.IotDataProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import udmi.schema.DiscoveryUpload;
import udmi.schema.Envelope;

class UploadProcessorTest {

  @TempDir
  File tempDir;

  private Map<String, String> sharedStore;
  private IotDataProvider dataProvider;
  private Envelope envelope;

  @BeforeEach
  void setUp() {
    UdmiServicePod.resetForTest();
    sharedStore = new ConcurrentHashMap<>();
    dataProvider = mock(IotDataProvider.class);
    when(dataProvider.ref()).thenAnswer(inv -> new SharedMemoryDataRef(sharedStore));
    envelope = new Envelope();
    envelope.deviceRegistryId = "test-reg";
    envelope.deviceId = "SNP-1";
  }

  @AfterEach
  void tearDown() {
    UdmiServicePod.resetForTest();
  }

  @Test
  void testOutOfOrderMultiPodReassemblyAndSha256Verification() throws Exception {
    final byte[] chunk1Bytes = "PCAP-HEADER-AND-FIRST-PACKET-".getBytes(StandardCharsets.UTF_8);
    final byte[] chunk2Bytes = "SECOND-PACKET-AND-FOOTER".getBytes(StandardCharsets.UTF_8);
    final byte[] fullPayload = "PCAP-HEADER-AND-FIRST-PACKET-SECOND-PACKET-AND-FOOTER"
        .getBytes(StandardCharsets.UTF_8);
    final String expectedSha256 = UploadProcessor.computeSha256Hex(fullPayload);
    final String sessionId = "trace-bacnet-2026-09-27T12:00:00Z";

    // Pod 1 receives start marker (event_no = 0)
    DiscoveryUpload startMsg = new DiscoveryUpload();
    startMsg.session_id = sessionId;
    startMsg.event_no = 0;
    startMsg.family = "bacnet";
    startMsg.total_chunks = 2;
    assertNull(UploadProcessor.processDiscoveryUpload(envelope, startMsg, dataProvider, tempDir));

    // Pod 2 receives chunk 2 out-of-order (event_no = 2)
    DiscoveryUpload chunk2Msg = new DiscoveryUpload();
    chunk2Msg.session_id = sessionId;
    chunk2Msg.event_no = 2;
    chunk2Msg.chunk_index = 1;
    chunk2Msg.total_chunks = 2;
    chunk2Msg.data = Base64.getEncoder().encodeToString(chunk2Bytes);
    assertNull(UploadProcessor.processDiscoveryUpload(envelope, chunk2Msg, dataProvider, tempDir));

    // Pod 3 receives terminal EOF marker (event_no = -3) before chunk 1 arrives
    DiscoveryUpload eofMsg = new DiscoveryUpload();
    eofMsg.session_id = sessionId;
    eofMsg.event_no = -3;
    eofMsg.total_chunks = 2;
    eofMsg.sha256 = expectedSha256;
    assertNull(UploadProcessor.processDiscoveryUpload(envelope, eofMsg, dataProvider, tempDir));

    // Pod 1 receives chunk 1 (event_no = 1), completing the set and winning CAS election
    DiscoveryUpload chunk1Msg = new DiscoveryUpload();
    chunk1Msg.session_id = sessionId;
    chunk1Msg.event_no = 1;
    chunk1Msg.chunk_index = 0;
    chunk1Msg.total_chunks = 2;
    chunk1Msg.data = Base64.getEncoder().encodeToString(chunk1Bytes);
    File reassembled =
        UploadProcessor.processDiscoveryUpload(envelope, chunk1Msg, dataProvider, tempDir);

    assertNotNull(reassembled);
    assertTrue(reassembled.exists());
    assertArrayEquals(fullPayload, Files.readAllBytes(reassembled.toPath()));
    assertTrue(sharedStore.isEmpty(), "Staged DataRef entries must be cleaned up after reassembly");
  }

  @Test
  void testSha256MismatchFailsFastAndCleansUp() {
    byte[] chunk1Bytes = "corrupted-chunk".getBytes(StandardCharsets.UTF_8);
    String sessionId = "trace-corrupted";

    DiscoveryUpload chunk1Msg = new DiscoveryUpload();
    chunk1Msg.session_id = sessionId;
    chunk1Msg.event_no = 1;
    chunk1Msg.data = Base64.getEncoder().encodeToString(chunk1Bytes);
    assertNull(UploadProcessor.processDiscoveryUpload(envelope, chunk1Msg, dataProvider, tempDir));

    DiscoveryUpload eofMsg = new DiscoveryUpload();
    eofMsg.session_id = sessionId;
    eofMsg.event_no = -2;
    eofMsg.sha256 = "0000000000000000000000000000000000000000000000000000000000000000";

    IllegalStateException thrown = assertThrows(IllegalStateException.class,
        () -> UploadProcessor.processDiscoveryUpload(envelope, eofMsg, dataProvider, tempDir));
    assertTrue(thrown.getMessage().contains("SHA-256 mismatch"));
    assertEquals(0, sharedStore.size(), "Staged DataRef entries must be cleaned up on failure");
  }

  private static class SharedMemoryDataRef extends DataRef {
    private final Map<String, String> data;

    SharedMemoryDataRef(Map<String, String> data) {
      this.data = data;
    }

    private String getKeyPath(String key) {
      return (registryId != null ? "r/" + registryId : "")
          + (deviceId != null ? "/d/" + deviceId : "")
          + (collection != null ? "/c/" + collection : "")
          + ":"
          + key;
    }

    @Override
    public void delete(String key) {
      data.remove(getKeyPath(key));
    }

    @Override
    public Map<String, String> entries() {
      String prefix = getKeyPath("");
      Map<String, String> res = new HashMap<>();
      for (Map.Entry<String, String> entry : data.entrySet()) {
        if (entry.getKey().startsWith(prefix)) {
          res.put(entry.getKey().substring(prefix.length()), entry.getValue());
        }
      }
      return res;
    }

    @Override
    public String get(String key) {
      return data.get(getKeyPath(key));
    }

    @Override
    public AutoCloseable lock() {
      return () -> {};
    }

    @Override
    public void put(String key, String value) {
      data.put(getKeyPath(key), value);
    }

    @Override
    public void update(Map<String, String> puts, Set<String> deletes) {
      if (puts != null) {
        puts.forEach(this::put);
      }
      if (deletes != null) {
        deletes.forEach(this::delete);
      }
    }

    @Override
    public boolean updateIfMatch(String matchKey, String expectedValue, Map<String, String> puts,
        Set<String> deletes) {
      synchronized (data) {
        String current = get(matchKey);
        if ((expectedValue == null && current == null)
            || (expectedValue != null && expectedValue.equals(current))) {
          update(puts, deletes);
          return true;
        }
        return false;
      }
    }
  }
}
