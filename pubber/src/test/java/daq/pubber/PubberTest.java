package daq.pubber;

import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.sha256;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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
import udmi.schema.DevicePersistent;
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
  private static final String TEST_REDIRECT_HOSTNAME = "mqtt-redirect.google.com";
  private static final String DATA_URL_PREFIX = "data:application/json;base64,";
  private static final EndpointConfiguration TEST_ENDPOINT = getEndpointConfiguration(null);
  private static final EndpointConfiguration TEST_REDIRECT_ENDPOINT = 
      getEndpointConfiguration(TEST_REDIRECT_HOSTNAME);
  private static final String ENDPOINT_BLOB = JsonUtil.stringify(TEST_ENDPOINT);
  private static final String ENDPOINT_REDIRECT_BLOB = JsonUtil.stringify(TEST_REDIRECT_ENDPOINT);
  private DevicePersistent testPersistentData = new DevicePersistent();

  private enum PubberUnderTestFeatures {
    nopInitializePersistentStore,
    OptionsNoPersist
  }

  private class PubberUnderTest extends Pubber {

    private HashMap<PubberUnderTestFeatures, Boolean> testFeatures = new HashMap<
        PubberUnderTestFeatures, Boolean>();

    private void setOptionsNoPersist(boolean value) {
      configuration.options.noPersist = value;
    }

    @Override
    protected DevicePersistent newDevicePersistent() {
      return testPersistentData;
    }

    @Override
    protected void initializePersistentStore() {
      if (!testFeatures.getOrDefault(PubberUnderTestFeatures.nopInitializePersistentStore, false)) {
        super.initializePersistentStore();
      }
    }

    PubberUnderTest(String projectId, String sitePath, String deviceId, String serialNo) {
      super(projectId, sitePath, deviceId, serialNo);
      setOptionsNoPersist(true);
    }

    PubberUnderTest(String projectId, String sitePath, String deviceId, String serialNo,
        HashMap<PubberUnderTestFeatures, Boolean> features) {
      super(projectId, sitePath, deviceId, serialNo);
      testFeatures = features;
      setOptionsNoPersist(
          testFeatures.getOrDefault(PubberUnderTestFeatures.OptionsNoPersist, true));
    }
  }

  private PubberUnderTest pubber;

  private PubberUnderTest singularPubber(String[] args) {
    PubberUnderTest pubber = new PubberUnderTest(args[0], args[1], args[2], args[3]);
    pubber.initialize();
    pubber.startConnection(deviceId -> {
      return true; });
    return pubber;
  }

  private static EndpointConfiguration getEndpointConfiguration(String hostname) {
    EndpointConfiguration endpointConfiguration = new EndpointConfiguration();
    endpointConfiguration.client_id = TEST_DEVICE;
    if (hostname != null) {
      endpointConfiguration.hostname = hostname;
    }
    return endpointConfiguration;
  }

  private PubberUnderTest makeTestPubber(String deviceId) {
    try {
      List<String> args = ImmutableList.of(TEST_PROJECT, TEST_SITE, deviceId, SERIAL_NO);
      return singularPubber(args.toArray(new String[0]));
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
    blobBlobsetConfig.generation = new Date();
    pubber.deviceConfig.blobset = new BlobsetConfig();
    pubber.deviceConfig.blobset.blobs = new HashMap<>();
    pubber.deviceConfig.blobset.blobs.put(IOT_ENDPOINT_CONFIG.value(), blobBlobsetConfig);

    return pubber.extractEndpointBlobConfig();
  }

  private EndpointConfiguration configurePubberRedirect() {
    BlobBlobsetConfig blobBlobsetConfig = new BlobBlobsetConfig();
    blobBlobsetConfig.url = DATA_URL_PREFIX + encodeBase64(ENDPOINT_REDIRECT_BLOB);
    blobBlobsetConfig.sha256 = sha256(ENDPOINT_REDIRECT_BLOB);
    blobBlobsetConfig.phase = BlobPhase.FINAL;
    blobBlobsetConfig.generation = new Date();
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
      e.printStackTrace();
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
  public void redirectEndpoint() throws InterruptedException {
    configurePubberEndpoint();
    pubber.maybeRedirectEndpoint();
    assertEquals(BlobPhase.FINAL,
        pubber.deviceState.blobset.blobs.get(IOT_ENDPOINT_CONFIG.value()).phase);
    Date initialGeneration = pubber.deviceState.blobset.blobs.get(
        IOT_ENDPOINT_CONFIG.value()).generation;
    assertNotEquals(null, initialGeneration);

    configurePubberRedirect();
    pubber.maybeRedirectEndpoint();
    assertEquals(BlobPhase.FINAL,
        pubber.deviceState.blobset.blobs.get(IOT_ENDPOINT_CONFIG.value()).phase);
    Date redirectGeneration = pubber.deviceState.blobset.blobs.get(
        IOT_ENDPOINT_CONFIG.value()).generation;
    assertNotEquals(null, redirectGeneration);

    assertTrue(redirectGeneration.after(initialGeneration));
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
    assertNotEquals(testMessage.timestamp, new Date(1241));
  }

  @Test
  public void initializePersistentStoreNullTest() {
    // Initialize the test Pubber.
    HashMap<PubberUnderTestFeatures, Boolean> testFeatures = new HashMap<
        PubberUnderTestFeatures, Boolean>();
    testFeatures.put(PubberUnderTestFeatures.nopInitializePersistentStore, true);
    pubber = new PubberUnderTest(TEST_PROJECT, TEST_SITE, TEST_DEVICE, SERIAL_NO, testFeatures);
    pubber.initialize();
    pubber.startConnection(deviceId -> {
      return true;
    });

    // Prepare test.
    testPersistentData.endpoint = null;
    pubber.configuration.endpoint = null;

    // Now test.
    testFeatures.put(PubberUnderTestFeatures.nopInitializePersistentStore, false);
    pubber.initializePersistentStore();
  }

  @Test
  public void initializePersistentStoreFromConfigTest() {
    // Initialize the test Pubber.
    HashMap<PubberUnderTestFeatures, Boolean> testFeatures = new HashMap<
        PubberUnderTestFeatures, Boolean>();
    testFeatures.put(PubberUnderTestFeatures.nopInitializePersistentStore, true);
    pubber = new PubberUnderTest(TEST_PROJECT, TEST_SITE, TEST_DEVICE, SERIAL_NO, testFeatures);
    pubber.initialize();
    pubber.startConnection(deviceId -> {
      return true;
    });

    // Prepare test.
    testPersistentData.endpoint = null;
    pubber.configuration.endpoint = getEndpointConfiguration("from_config");

    // Now test.
    testFeatures.put(PubberUnderTestFeatures.nopInitializePersistentStore, false);
    pubber.initializePersistentStore();
    assertEquals(pubber.persistentData.endpoint.hostname, "from_config");
  }

  @Test
  public void initializePersistentStoreFromPersistentDataTest() {
    // Initialize the test Pubber.
    HashMap<PubberUnderTestFeatures, Boolean> testFeatures = new HashMap<
        PubberUnderTestFeatures, Boolean>();
    testFeatures.put(PubberUnderTestFeatures.nopInitializePersistentStore, true);
    pubber = new PubberUnderTest(TEST_PROJECT, TEST_SITE, TEST_DEVICE, SERIAL_NO, testFeatures);
    pubber.initialize();
    pubber.startConnection(deviceId -> {
      return true;
    });

    // Prepare test.
    testPersistentData.endpoint = getEndpointConfiguration("persistent");
    pubber.configuration.endpoint = null;

    // Now test.
    testFeatures.put(PubberUnderTestFeatures.nopInitializePersistentStore, false);
    pubber.initializePersistentStore();
    assertEquals(pubber.persistentData.endpoint.hostname, "persistent");
    assertEquals(pubber.configuration.endpoint.hostname, "persistent");
  }
}
