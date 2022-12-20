package daq.pubber;

import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.sha256;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static udmi.schema.BlobsetConfig.SystemBlobsets.IOT_ENDPOINT_CONFIG;

import com.google.common.collect.ImmutableList;
import com.google.udmi.util.JsonUtil;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import udmi.schema.BlobBlobsetConfig;
import udmi.schema.BlobBlobsetConfig.BlobPhase;
import udmi.schema.BlobsetConfig;
import udmi.schema.EndpointConfiguration;
import udmi.schema.State;

/**
 * Unit tests for Pubber.
 */
public class PubberTest extends TestBase {

  private static final String TEST_PROJECT = "test-project";
  private static final String TEST_SITE = "../sites/udmi_site_model";
  private static final String BAD_DEVICE = "ASHDQWHD";
  private static final String TEST_DEVICE = "AHU-1";
  private static final String SERIAL_NO = "18217398172";
  private static final String TEST_BLOB_DATA = "mary had a little lamb";
  private static final String DATA_URL_PREFIX = "data:application/json;base64,";
  private static final EndpointConfiguration TEST_ENDPOINT = getEndpointConfiguration();
  private static final String ENDPOINT_BLOB = JsonUtil.stringify(TEST_ENDPOINT);
  private Pubber pubber;

  private static EndpointConfiguration getEndpointConfiguration() {
    EndpointConfiguration endpointConfiguration = new EndpointConfiguration();
    endpointConfiguration.client_id = TEST_DEVICE;
    return endpointConfiguration;
  }

  private Pubber makeTestPubber(String deviceId) {
    try {
      List<String> args = ImmutableList.of(TEST_PROJECT, TEST_SITE, deviceId, SERIAL_NO);
      return Pubber.singularPubber(args.toArray(new String[0]));
    } catch (Exception e) {
      throw new RuntimeException("While creating singular pubber", e);
    }
  }

  private EndpointConfiguration configurePubberEndpoint() {
    pubber = makeTestPubber(TEST_DEVICE);
    BlobBlobsetConfig blobBlobsetConfig = new BlobBlobsetConfig();
    blobBlobsetConfig.url = DATA_URL_PREFIX + encodeBase64(ENDPOINT_BLOB);
    blobBlobsetConfig.sha256 = sha256(ENDPOINT_BLOB);
    blobBlobsetConfig.phase = BlobPhase.FINAL;
    pubber.deviceConfig.blobset = new BlobsetConfig();
    pubber.deviceConfig.blobset.blobs = new HashMap<>();
    pubber.deviceConfig.blobset.blobs.put(IOT_ENDPOINT_CONFIG.value(), blobBlobsetConfig);

    EndpointConfiguration endpointConfiguration = pubber.extractEndpointBlobConfig();
    return endpointConfiguration;
  }

  /**
   * Properly shutdown pubber if it had been instantiated.
   */
  @After
  public void terminatePubber() {
    if (pubber != null) {
      pubber.terminate();
      pubber = null;
    }
  }

  @Test
  public void missingDevice() {
    try {
      makeTestPubber(BAD_DEVICE);
    } catch (Throwable e) {
      while (e != null) {
        String message = e.getMessage();
        if (message.contains(BAD_DEVICE)) {
          return;
        }
        e = e.getCause();
      }
    }
    throw new RuntimeException("No exception thrown for bad device");
  }

  @Test
  public void parseDataUrl() {
    String testBlobDataUrl = DATA_URL_PREFIX + encodeBase64(TEST_BLOB_DATA);
    String blobData = Pubber.acquireBlobData(testBlobDataUrl, sha256(TEST_BLOB_DATA));
    assertEquals("extracted blob data", blobData, TEST_BLOB_DATA);
  }

  @Test(expected = RuntimeException.class)
  public void badDataUrl() {
    String testBlobDataUrl = DATA_URL_PREFIX + encodeBase64(TEST_BLOB_DATA + "XXXX");
    Pubber.acquireBlobData(testBlobDataUrl, sha256(TEST_BLOB_DATA));
  }

  @Test
  public void extractedEndpointConfigBlob() {
    EndpointConfiguration endpointConfiguration = configurePubberEndpoint();
    String extractedClientId = endpointConfiguration.client_id;
    assertEquals("blob client id", TEST_DEVICE, extractedClientId);
  }

  @Test
  public void redirectEndpoint() {
    configurePubberEndpoint();

    pubber.maybeRedirectEndpoint();
    assertEquals(BlobPhase.FINAL,
        pubber.deviceState.blobset.blobs.get(IOT_ENDPOINT_CONFIG.value()).phase);
  }

  @Test
  public void augmentDeviceMessageTest() {
    State testMessage = new State();

    assertNull(testMessage.timestamp);
    Pubber.augmentDeviceMessage(testMessage);
    assertEquals(testMessage.version, Pubber.UDMI_VERSION);
    assertNotEquals(testMessage.timestamp, null);

    testMessage.timestamp = new Date(1241);
    Pubber.augmentDeviceMessage(testMessage);
    assertEquals(testMessage.version, Pubber.UDMI_VERSION);
    assertEquals(testMessage.timestamp, new Date(1241));
  }
}
