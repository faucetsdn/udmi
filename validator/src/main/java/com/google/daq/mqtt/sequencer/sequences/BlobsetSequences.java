package com.google.daq.mqtt.sequencer.sequences;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.daq.mqtt.util.TimePeriodConstants.THREE_MINUTES_MS;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.GeneralUtils.encodeBase64;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static com.google.udmi.util.GeneralUtils.sha256;
import static com.google.udmi.util.JsonUtil.getNowInstant;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.stringify;
import static java.lang.String.format;
import static org.junit.Assert.assertNotEquals;
import static udmi.schema.Bucket.ENDPOINT_CONFIG;
import static udmi.schema.Bucket.SYSTEM_MODE;
import static udmi.schema.Category.BLOBSET_BLOB_APPLY;
import static udmi.schema.FeatureDiscovery.FeatureStage.ALPHA;
import static udmi.schema.FeatureDiscovery.FeatureStage.PREVIEW;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.Summary;
import com.google.daq.mqtt.sequencer.ValidateSchema;
import com.google.daq.mqtt.sequencer.semantic.SemanticDate;
import com.google.daq.mqtt.sequencer.semantic.SemanticValue;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import udmi.schema.Auth_provider;
import udmi.schema.Basic;
import udmi.schema.BlobBlobsetConfig;
import udmi.schema.BlobBlobsetConfig.BlobPhase;
import udmi.schema.BlobBlobsetState;
import udmi.schema.BlobsetConfig;
import udmi.schema.BlobsetConfig.SystemBlobsets;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.EndpointConfiguration.Transport;
import udmi.schema.Entry;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.IotAccess.IotProvider;
import udmi.schema.Level;
import udmi.schema.Operation.SystemMode;


/**
 * Validation tests for instances that involve blobset config messages.
 */

public class BlobsetSequences extends SequenceBase {

  public static final String JSON_MIME_TYPE = "application/json";
  public static final String DATA_URL_FORMAT = "data:%s;base64,%s";
  public static final String IOT_BLOB_KEY = SystemBlobsets.IOT_ENDPOINT_CONFIG.value();
  private static final String IOT_CORE_CLIENT_ID_FMT =
      "projects/%s/locations/%s/registries/%s/devices/%s";
  private static final String LOCAL_CLIENT_ID_FMT = "/r/%s/d/%s";
  private static final String BOGUS_ENDPOINT_HOSTNAME = "twiddily.fiddily.fog";
  public static final String BOGUS_REGISTRY = "BOGUS_REGISTRY";

  private static boolean isMqttProvider() {
    return exeConfig.iot_provider == IotProvider.MQTT;
  }

  @Override
  public void setUp() {
    ifTrueSkipTest(catchToFalse(() -> !isNullOrEmpty(deviceMetadata.gateway.gateway_id)),
        "No blobset check for proxy device");
    super.setUp();
  }

  @Before
  public void setupExpectedParameters() {
    allowDeviceStateChange("blobset");
  }

  private static String generateEndpointConfigClientId(String registryId) {
    return isMqttProvider() ? format(LOCAL_CLIENT_ID_FMT, registryId, getDeviceId())
        : format(IOT_CORE_CLIENT_ID_FMT, projectId, cloudRegion, registryId, getDeviceId());
  }

  private static String endpointConfigPayload(String hostname, String registryId) {
    EndpointConfiguration endpointConfiguration = new EndpointConfiguration();
    endpointConfiguration.protocol = Protocol.MQTT;
    endpointConfiguration.hostname = hostname;
    endpointConfiguration.client_id = generateEndpointConfigClientId(registryId);
    if (isMqttProvider()) {
      endpointConfiguration.topic_prefix = endpointConfiguration.client_id;
      endpointConfiguration.transport = Transport.SSL;
      Auth_provider authProvider = new Auth_provider();
      endpointConfiguration.auth_provider = authProvider;
      authProvider.basic = new Basic();
      authProvider.basic.username = endpointConfiguration.client_id;
      authProvider.basic.password = siteModel.getDevicePassword(getDeviceId());
    }
    return stringify(endpointConfiguration);
  }

  private void untilClearedRedirect() {
    deviceConfig.blobset.blobs.remove(IOT_BLOB_KEY);
    untilTrue("endpoint config blobset state not defined",
        () -> deviceState.blobset == null || deviceState.blobset.blobs.get(IOT_BLOB_KEY) == null);
  }

  private void untilSuccessfulRedirect(BlobPhase blobPhase) {
    untilCompletedRedirect(blobPhase, false);
  }

  private void untilCompletedRedirect(BlobPhase blobPhase, boolean expectFailure) {
    // This case is tracking the initial apply of a redirect, so it sets up the mirror config.
    if (blobPhase == BlobPhase.APPLY) {
      mirrorToOtherConfig();
    }
    String prefix = ifTrueGet(expectFailure, "not ", "");
    untilTrue(format("blobset phase is %s and stateStatus is %snull", blobPhase, prefix), () -> {
      BlobBlobsetState blobBlobsetState = deviceState.blobset.blobs.get(IOT_BLOB_KEY);
      BlobBlobsetConfig blobBlobsetConfig = deviceConfig.blobset.blobs.get(IOT_BLOB_KEY);
      // Successful reconnect sends a state message with empty Entry, error will have status.
      boolean statusError = blobBlobsetState.status != null;
      return blobPhase.equals(blobBlobsetState.phase)
          && blobBlobsetConfig.generation.equals(blobBlobsetState.generation)
          && statusError == expectFailure;
    });

    // This case is tracking the finalization of the redirect, so clear out the non-used one.
    if (blobPhase == BlobPhase.FINAL) {
      clearOtherConfig();
    }
  }

  private void untilErrorReported() {
    untilTrue("blobset entry config status is error", () -> {
      BlobBlobsetState blobBlobsetState = deviceState.blobset.blobs.get(IOT_BLOB_KEY);
      BlobBlobsetConfig blobBlobsetConfig = deviceConfig.blobset.blobs.get(IOT_BLOB_KEY);
      return blobBlobsetConfig.generation.equals(blobBlobsetState.generation)
          && blobBlobsetState.phase.equals(BlobPhase.FINAL)
          && blobBlobsetState.status.category.equals(BLOBSET_BLOB_APPLY)
          && blobBlobsetState.status.level == Level.ERROR.value();
    });
  }

  private void setDeviceConfigEndpointBlob(String hostname, String registryId, boolean badHash) {
    deviceConfig.blobset = getEndpointRedirectBlobset(hostname, registryId, badHash);
    debug("blobset config", stringify(deviceConfig.blobset));
  }

  public static BlobsetConfig getEndpointReturnBlobset() {
    return getEndpointRedirectBlobset(getAlternateEndpointHostname(), registryId, false);
  }

  private static BlobsetConfig getEndpointRedirectBlobset(String hostname, String registryId,
      boolean badHash) {
    String payload = endpointConfigPayload(hostname, registryId);
    BlobBlobsetConfig config = makeEndpointConfigBlob(payload, badHash);
    BlobsetConfig blobset = new BlobsetConfig();
    blobset.blobs = new HashMap<>();
    blobset.blobs.put(IOT_BLOB_KEY, config);
    return blobset;
  }

  private static BlobBlobsetConfig makeEndpointConfigBlob(String payload, boolean badHash) {
    BlobBlobsetConfig config = new BlobBlobsetConfig();
    config.url = SemanticValue.describe("endpoint data", generateEndpointConfigDataUrl(payload));
    config.phase = BlobPhase.FINAL;
    config.generation = SemanticDate.describe("blob generation", new Date());
    String description = badHash ? "invalid blob data hash" : "blob data hash";
    config.sha256 = SemanticValue.describe(description,
        badHash ? sha256(payload + "X") : sha256(payload));
    return config;
  }

  private static String generateEndpointConfigDataUrl(String payload) {
    return format(DATA_URL_FORMAT, JSON_MIME_TYPE, encodeBase64(payload));
  }

  @Feature(stage = PREVIEW, bucket = ENDPOINT_CONFIG)
  @Summary("Push endpoint config message to device that results in a connection error.")
  @Test(timeout = TWO_MINUTES_MS) // TODO Is this enough? Does a client try X times?
  public void endpoint_connection_error() {
    setDeviceConfigEndpointBlob(BOGUS_ENDPOINT_HOSTNAME, registryId, false);
    untilErrorReported();
    untilClearedRedirect();
  }

  @Feature(stage = PREVIEW, bucket = ENDPOINT_CONFIG)
  @Summary("Check repeated endpoint with same information gets retried.")
  @Test(timeout = TWO_MINUTES_MS)
  public void endpoint_connection_retry() {
    setDeviceConfigEndpointBlob(BOGUS_ENDPOINT_HOSTNAME, registryId, false);
    final Date savedGeneration = deviceConfig.blobset.blobs.get(IOT_BLOB_KEY).generation;
    untilErrorReported();
    setDeviceConfigEndpointBlob(BOGUS_ENDPOINT_HOSTNAME, registryId, false);
    // Semantically this is a different date; manually update for change-detection purposes.
    deviceConfig.blobset.blobs.get(IOT_BLOB_KEY).generation = SemanticDate.describe(
        "new generation", new Date());
    assertNotEquals("config generation", savedGeneration,
        deviceConfig.blobset.blobs.get(IOT_BLOB_KEY).generation);
    untilErrorReported();
    untilClearedRedirect();
  }

  @Feature(stage = PREVIEW, bucket = ENDPOINT_CONFIG)
  @Summary("Check a successful reconnect to the same endpoint.")
  @Test(timeout = TWO_MINUTES_MS)
  public void endpoint_connection_success_reconnect() {
    setDeviceConfigEndpointBlob(getAlternateEndpointHostname(), registryId, false);
    untilSuccessfulRedirect(BlobPhase.FINAL);
    untilClearedRedirect();
  }

  @Feature(stage = ALPHA, bucket = ENDPOINT_CONFIG)
  @Summary("Failed connection because of bad hash.")
  @ValidateSchema(SubFolder.BLOBSET)
  @Test(timeout = TWO_MINUTES_MS)
  public void endpoint_connection_bad_hash() {
    setDeviceConfigEndpointBlob(getAlternateEndpointHostname(), registryId, true);
    untilTrue("blobset status is ERROR", () -> {
      BlobBlobsetState blobBlobsetState = deviceState.blobset.blobs.get(IOT_BLOB_KEY);
      BlobBlobsetConfig blobBlobsetConfig = deviceConfig.blobset.blobs.get(IOT_BLOB_KEY);
      // Successful reconnect sends a state message with empty Entry.
      Entry blobStateStatus = blobBlobsetState.status;
      return BlobPhase.FINAL.equals(blobBlobsetState.phase)
          && blobBlobsetConfig.generation.equals(blobBlobsetState.generation)
          && blobStateStatus.category.equals(BLOBSET_BLOB_APPLY)
          && blobStateStatus.level == Level.ERROR.value();
    });
  }

  @Feature(stage = ALPHA, bucket = ENDPOINT_CONFIG)
  @Summary("Failed connection never uses alternate registry.")
  @ValidateSchema(SubFolder.BLOBSET)
  @Test(timeout = TWO_MINUTES_MS)
  public void endpoint_connection_no_alternate() {
    check_endpoint_connection_success(false, true);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = PREVIEW, bucket = ENDPOINT_CONFIG)
  @Summary("Check connection to an alternate project.")
  public void endpoint_connection_success_alternate() {
    check_endpoint_connection_success(false, false);
  }

  @Test(timeout = THREE_MINUTES_MS)
  @Feature(stage = PREVIEW, bucket = ENDPOINT_CONFIG)
  public void endpoint_redirect_and_restart() {
    check_endpoint_connection_success(true, false);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = PREVIEW, bucket = ENDPOINT_CONFIG)
  public void endpoint_failure_and_restart() {
    setDeviceConfigEndpointBlob(BOGUS_ENDPOINT_HOSTNAME, registryId, false);
    untilErrorReported();
    check_system_restart();
    untilClearedRedirect();
  }

  private void check_endpoint_connection_success(boolean doRestart, boolean useInvalidRegistry) {
    // Phase one: initiate connection to alternate registry.
    waitUntil("initial last_config matches config timestamp", this::lastConfigUpdated);

    String useRegistry = useInvalidRegistry ? BOGUS_REGISTRY : altRegistry;
    setDeviceConfigEndpointBlob(getAlternateEndpointHostname(), useRegistry, false);

    BlobPhase endPhase = useInvalidRegistry ? BlobPhase.FINAL : BlobPhase.APPLY;
    untilCompletedRedirect(endPhase, useInvalidRegistry);

    withAlternateClient(useInvalidRegistry, () -> {
      // Phase two: verify connection to alternate registry.
      untilCompletedRedirect(BlobPhase.FINAL, useInvalidRegistry);

      if (useInvalidRegistry) {
        // This will never be valid, so wait a bit to ensure it's had time to process the error.
        waitDuration("alternate client connect delay", Duration.ofSeconds(10));
        return;
      }

      waitUntil("alternate last_config matches config timestamp", this::lastConfigUpdated);
      untilClearedRedirect();

      if (doRestart) {
        // Phase two.five: restart the system to make sure the change sticks.
        check_system_restart();
      }

      // Phase three: initiate connection back to initial registry.
      // Phase 3/4 test the same thing as phase 1/2, included to restore system to initial state.
      setDeviceConfigEndpointBlob(getAlternateEndpointHostname(), registryId, false);
      untilSuccessfulRedirect(BlobPhase.APPLY);
    });

    ifTrueThen(useInvalidRegistry,
        () -> setDeviceConfigEndpointBlob(getAlternateEndpointHostname(), registryId, false));

    // Phase four: verify restoration of initial registry connection.
    whileDoing("restoring main connection", () -> {
      untilSuccessfulRedirect(BlobPhase.FINAL);
      waitUntil("restored last_config matches config timestamp", this::lastConfigUpdated);
      untilClearedRedirect();
    });
  }

  private void waitDuration(String reason, Duration duration) {
    Instant endTime = getNowInstant().plus(duration);
    String waitingMessage = "waiting until " + isoConvert(endTime);
    waitUntil(reason, () -> ifTrueGet(getNowInstant().isBefore(endTime), waitingMessage));
  }

  @Test
  @Summary("Restart and connect to same endpoint and expect it returns.")
  @Feature(stage = ALPHA, bucket = SYSTEM_MODE)
  public void system_mode_restart() {
    check_system_restart();
  }

  private void check_system_restart() {
    allowDeviceStateChange("system.operation.");

    // Prepare for the restart.
    final Date dateZero = new Date(0);
    untilTrue("last_start is not zero",
        () -> deviceState.system.operation.last_start.after(dateZero));

    final Integer initialCount = deviceState.system.operation.restart_count;
    checkThat("initial count is greater than 0", () -> initialCount > 0);

    deviceConfig.system.operation.mode = SystemMode.ACTIVE;

    untilTrue("system mode is ACTIVE",
        () -> deviceState.system.operation.mode.equals(SystemMode.ACTIVE));

    final Date last_config = deviceState.system.last_config;
    final Date last_start = deviceConfig.system.operation.last_start;

    // Send the restart mode.
    deviceConfig.system.operation.mode = SystemMode.RESTART;

    // Wait for the device to go through the correct states as it restarts.
    untilTrue("system mode is INITIAL",
        () -> deviceState.system.operation.mode.equals(SystemMode.INITIAL));

    checkThat("restart count increased by one",
        () -> deviceState.system.operation.restart_count == initialCount + 1);

    deviceConfig.system.operation.mode = SystemMode.ACTIVE;

    untilTrue("system mode is ACTIVE",
        () -> deviceState.system.operation.mode.equals(SystemMode.ACTIVE));

    // Capture error from last_start unexpectedly changing due to restart condition.
    try {
      untilTrue("last_config is newer than previous last_config before abort",
          () -> deviceState.system.last_config.after(last_config));
    } catch (AbortMessageLoop e) {
      info("Squelching aborted message loop: " + e.getMessage());
    }

    untilTrue("last_config is newer than previous last_config after abort",
        () -> deviceState.system.last_config.after(last_config));

    untilTrue("last_start is newer than previous last_start",
        () -> deviceConfig.system.operation.last_start.after(last_start));
  }
}
