package daq.pubber;

import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.sha256;
import static java.util.Optional.ofNullable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static udmi.schema.BlobsetConfig.SystemBlobsets.IOT_ENDPOINT_CONFIG;

import com.google.common.collect.ImmutableList;
import com.google.udmi.util.JsonUtil;
import daq.pubber.impl.host.PubberPublisherHost;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import udmi.lib.TestBase;
import udmi.lib.client.host.PublisherHost;
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
  private final DevicePersistent testPersistentData = new DevicePersistent();

  private enum PubberUnderTestFeatures {
    noInitializePersistentStore,
    OptionsNoPersist
  }

  private class PubberUnderTest extends PubberPublisherHost {

    private HashMap<PubberUnderTestFeatures, Boolean> testFeatures = new HashMap<>();

    private void setOptionsNoPersist(boolean value) {
      config.options.noPersist = value;
    }

    @Override
    public DevicePersistent newDevicePersistent() {
      return testPersistentData;
    }

    @Override
    public void initializePersistentStore() {
      if (!testFeatures.getOrDefault(PubberUnderTestFeatures.noInitializePersistentStore, false)) {
        super.initializePersistentStore();
      }
    }

    @Override
    protected void augmentEndpoint(EndpointConfiguration endpoint) {
      endpoint.topic_prefix = TEST_PREFIX;
    }

    PubberUnderTest(String iotProject, String sitePath, String deviceId, String serialNo) {
      super(iotProject, sitePath, deviceId, serialNo);
      setOptionsNoPersist(true);
      config.endpoint = ofNullable(config.endpoint).orElseGet(EndpointConfiguration::new);
      config.endpoint.topic_prefix = TEST_PREFIX;
    }

    PubberUnderTest(String projectId, String sitePath, String deviceId, String serialNo,
        HashMap<PubberUnderTestFeatures, Boolean> features) {
      super(projectId, sitePath, deviceId, serialNo);
      testFeatures = features;
      setOptionsNoPersist(
          testFeatures.getOrDefault(PubberUnderTestFeatures.OptionsNoPersist, true));
      config.endpoint = ofNullable(config.endpoint).orElseGet(EndpointConfiguration::new);
      config.endpoint.topic_prefix = TEST_PREFIX;
    }
  }

  private PubberUnderTest pubber;

  private PubberUnderTest singularPubber(String[] args) {
    PubberUnderTest pubber = new PubberUnderTest(args[0], args[1], args[2], args[3]);
    pubber.initialize();
    pubber.startConnection();
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
    pubber.getDeviceConfig().blobset = new BlobsetConfig();
    pubber.getDeviceConfig().blobset.blobs = new HashMap<>();
    pubber.getDeviceConfig().blobset.blobs.put(IOT_ENDPOINT_CONFIG.value(), blobBlobsetConfig);

    return pubber.extractEndpointBlobConfig();
  }

  private EndpointConfiguration configurePubberRedirect() {
    BlobBlobsetConfig blobBlobsetConfig = new BlobBlobsetConfig();
    blobBlobsetConfig.url = DATA_URL_PREFIX + encodeBase64(ENDPOINT_REDIRECT_BLOB);
    blobBlobsetConfig.sha256 = sha256(ENDPOINT_REDIRECT_BLOB);
    blobBlobsetConfig.phase = BlobPhase.FINAL;
    blobBlobsetConfig.generation = new Date();
    pubber.getDeviceConfig().blobset = new BlobsetConfig();
    pubber.getDeviceConfig().blobset.blobs = new HashMap<>();
    pubber.getDeviceConfig().blobset.blobs.put(IOT_ENDPOINT_CONFIG.value(), blobBlobsetConfig);

    return pubber.extractEndpointBlobConfig();
  }

  /**
   * Properly shutdown pubber if it had been instantiated.
   */
  @After
  public void terminatePubber() {
    if (pubber != null) {
      pubber.shutdown();
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
    String blobData = PublisherHost.acquireBlobData(testBlobDataUrl, sha256(TEST_BLOB_DATA));
    assertEquals("extracted blob data", blobData, TEST_BLOB_DATA);
  }

  @Test(expected = RuntimeException.class)
  public void badDataUrl() {
    String testBlobDataUrl = DATA_URL_PREFIX + encodeBase64(TEST_BLOB_DATA + "XXXX");
    PublisherHost.acquireBlobData(testBlobDataUrl, sha256(TEST_BLOB_DATA));
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
    pubber.maybeRedirectEndpoint(pubber.getExtractedEndpoint());
    assertEquals(BlobPhase.FINAL,
        pubber.getDeviceState().blobset.blobs.get(IOT_ENDPOINT_CONFIG.value()).phase);
    Date initialGeneration = pubber.getDeviceState().blobset.blobs.get(
        IOT_ENDPOINT_CONFIG.value()).generation;
    assertNotEquals(null, initialGeneration);

    configurePubberRedirect();
    pubber.maybeRedirectEndpoint(pubber.getExtractedEndpoint());
    assertEquals(BlobPhase.FINAL,
        pubber.getDeviceState().blobset.blobs.get(IOT_ENDPOINT_CONFIG.value()).phase);
    Date redirectGeneration = pubber.getDeviceState().blobset.blobs.get(
        IOT_ENDPOINT_CONFIG.value()).generation;
    assertNotEquals(null, redirectGeneration);

    assertTrue(redirectGeneration.after(initialGeneration));
  }

  @Test
  public void augmentDeviceMessageTest() {
    State testMessage = new State();

    assertNull(testMessage.timestamp);
    PublisherHost.augmentDeviceMessage(testMessage, new Date(), false);
    assertEquals(testMessage.version, PublisherHost.UDMI_VERSION);
    assertNotEquals(testMessage.timestamp, null);

    testMessage.timestamp = new Date(1241);
    PublisherHost.augmentDeviceMessage(testMessage, new Date(), false);
    assertEquals(testMessage.version, PublisherHost.UDMI_VERSION);
    assertNotEquals(testMessage.timestamp, new Date(1241));
  }

  @Test
  public void initializePersistentStoreNullTest() {
    // Initialize the test Pubber.
    HashMap<PubberUnderTestFeatures, Boolean> testFeatures = new HashMap<
        PubberUnderTestFeatures, Boolean>();
    testFeatures.put(PubberUnderTestFeatures.noInitializePersistentStore, true);
    pubber = new PubberUnderTest(TEST_PROJECT, TEST_SITE, TEST_DEVICE, SERIAL_NO, testFeatures);
    pubber.initialize();
    pubber.startConnection();

    // Prepare test.
    testPersistentData.endpoint = null;
    pubber.getPubberConfig().endpoint = null;

    // Now test.
    testFeatures.put(PubberUnderTestFeatures.noInitializePersistentStore, false);
    pubber.initializePersistentStore();
  }

  @Test
  public void initializePersistentStoreFromConfigTest() {
    // Initialize the test Pubber.
    HashMap<PubberUnderTestFeatures, Boolean> testFeatures = new HashMap<>();
    testFeatures.put(PubberUnderTestFeatures.noInitializePersistentStore, true);
    pubber = new PubberUnderTest(TEST_PROJECT, TEST_SITE, TEST_DEVICE, SERIAL_NO, testFeatures);
    pubber.initialize();
    pubber.startConnection();

    // Prepare test.
    testPersistentData.endpoint = null;
    pubber.getPubberConfig().endpoint = getEndpointConfiguration("from_config");

    // Now test.
    testFeatures.put(PubberUnderTestFeatures.noInitializePersistentStore, false);
    pubber.initializePersistentStore();
    assertEquals(pubber.persistentData.endpoint.hostname, "from_config");
  }

  @Test
  public void initializePersistentStoreFromPersistentDataTest() {
    // Initialize the test Pubber.
    HashMap<PubberUnderTestFeatures, Boolean> testFeatures = new HashMap<>();
    testFeatures.put(PubberUnderTestFeatures.noInitializePersistentStore, true);
    pubber = new PubberUnderTest(TEST_PROJECT, TEST_SITE, TEST_DEVICE, SERIAL_NO, testFeatures);
    pubber.initialize();
    pubber.startConnection();

    // Prepare test.
    testPersistentData.endpoint = getEndpointConfiguration("persistent");
    pubber.getPubberConfig().endpoint = null;

    // Now test.
    testFeatures.put(PubberUnderTestFeatures.noInitializePersistentStore, false);
    pubber.initializePersistentStore();
    assertEquals(pubber.persistentData.endpoint.hostname, "persistent");
    assertEquals(pubber.getPubberConfig().endpoint.hostname, "persistent");
  }
}
