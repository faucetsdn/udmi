package com.google.daq.mqtt.sequencer.sequences;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.daq.mqtt.sequencer.SequenceBase.Capabilities.LOGGING;
import static com.google.daq.mqtt.sequencer.SequenceBase.Capabilities.MATCHING_SUBBLOCKS;
import static com.google.daq.mqtt.util.TimePeriodConstants.THREE_MINUTES_MS;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_RECEIVE;
import static udmi.schema.Category.SYSTEM_CONFIG_RECEIVE_LEVEL;
import static udmi.schema.FeatureDiscovery.FeatureStage.ALPHA;
import static udmi.schema.FeatureDiscovery.FeatureStage.BETA;
import static udmi.schema.FeatureDiscovery.FeatureStage.STABLE;

import com.google.daq.mqtt.sequencer.Capability;
import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.Summary;
import com.google.daq.mqtt.sequencer.ValidateSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import udmi.schema.Entry;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Level;

/**
 * Validate basic device configuration handling operation, not specific to any device function.
 */
public class ConfigSequences extends SequenceBase {

  // Delay to wait to let a device apply and log a new config.
  private static final long CONFIG_THRESHOLD_SEC = 20;
  // Delay after receiving a parse error to ensure an apply entry has not been received.
  private static final long LOG_APPLY_DELAY_MS = 1000;
  // How frequently to send out confg queries for device config acked check.
  private static final Duration CONFIG_QUERY_INTERVAL = Duration.ofSeconds(30);

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = STABLE, bucket = SYSTEM)
  @Summary("Check that last_update state is correctly set in response to a config update.")
  @ValidateSchema(SubFolder.SYSTEM)
  @Capability(value = MATCHING_SUBBLOCKS, stage = ALPHA)
  public void system_last_update() {
    waitFor("state last_config matches config timestamp", this::lastConfigUpdated);
    waitForCapability(MATCHING_SUBBLOCKS, "state update complete", this::stateMatchesConfig);
    forceConfigUpdate("trigger another config update");
    waitFor("state last_config matches config timestamp", this::lastConfigUpdated);
    waitForCapability(MATCHING_SUBBLOCKS, "state update complete", this::stateMatchesConfig);
    forceConfigUpdate("trigger another config update");
    waitFor("state last_config matches config timestamp", this::lastConfigUpdated);
    waitForCapability(MATCHING_SUBBLOCKS, "state update complete", this::stateMatchesConfig);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = STABLE, bucket = SYSTEM)
  @ValidateSchema(SubFolder.SYSTEM)
  public void valid_serial_no() {
    ifNullSkipTest(serialNo, "No test serial number provided");
    ensureStateUpdate();
    untilTrue("received serial number matches", () -> serialNo.equals(lastSerialNo));
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = ALPHA, bucket = SYSTEM, nostate = true)
  @Summary("Check that the min log-level config is honored by the device.")
  @ValidateSchema(SubFolder.SYSTEM)
  public void system_min_loglevel() {
    Integer savedLevel = deviceConfig.system.min_loglevel;
    checkState(SYSTEM_CONFIG_APPLY_LEVEL.value() >= savedLevel, "invalid saved level");
    checkState(SYSTEM_CONFIG_APPLY_LEVEL.value() < Level.WARNING.value(),
        "invalid config apply level");

    final Instant startTime = Instant.now();
    deviceConfig.system.min_loglevel = Level.INFO.value();
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    checkNotLogged(SYSTEM_CONFIG_APPLY, Level.WARNING);
    Instant expectedFinish = startTime.plusSeconds(CONFIG_THRESHOLD_SEC);
    Instant logFinished = Instant.now();
    checkThat(format("device config resolved within %ss", CONFIG_THRESHOLD_SEC),
        ifTrueGet(logFinished.isAfter(expectedFinish),
            format("apply log took until %s, expected before %s", isoConvert(logFinished),
                isoConvert(expectedFinish))));

    deviceConfig.system.min_loglevel = Level.WARNING.value();
    updateConfig("warning loglevel");
    // Nothing to actively wait for, so wait for some amount of time instead.
    safeSleep(CONFIG_THRESHOLD_SEC * 2000);
    checkNotLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);

    deviceConfig.system.min_loglevel = savedLevel;
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
  }

  @Test(timeout = THREE_MINUTES_MS)
  @Feature(stage = STABLE, bucket = SYSTEM)
  @Summary("Check that the device MQTT-acknowledges a sent config.")
  public void device_config_acked() {
    ifTrueSkipTest(catchToFalse(() -> !isNullOrEmpty(deviceMetadata.gateway.gateway_id)),
        "No config check for proxy device");

    // There's two separate delays that are accounted for here.
    //   * Data to show up in the backend API, this can take on the order of ~1m
    //   * Query/Reply to go out, hit the API, and return a reply.
    // Solution is to keep checking for the reply, and only occasionally send out a query.

    AtomicReference<Instant> lastQuerySent = new AtomicReference<>(Instant.ofEpochSecond(0));
    untilTrue("config acked", () -> {
      if (lastQuerySent.get().isBefore(Instant.now().minus(CONFIG_QUERY_INTERVAL))) {
        debug("Sending device config acked device query...");
        queryState();
        lastQuerySent.set(Instant.now());
      }
      return configAcked;
    });
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = STABLE, bucket = SYSTEM, score = 9)
  @Capability(value = LOGGING, stage = ALPHA)
  @Summary("Check that the device correctly handles a broken (non-json) config message.")
  @ValidateSchema(SubFolder.SYSTEM)
  public void broken_config() {
    expectedStatusLevel(Level.ERROR);

    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    updateConfig("starting broken_config");
    Date stableConfig = deviceConfig.timestamp;
    info("initial stable_config " + isoConvert(stableConfig));
    untilTrue("initial state synchronized",
        () -> dateEquals(stableConfig, deviceState.system.last_config));
    info("initial last_config " + isoConvert(deviceState.system.last_config));
    checkThat("initial stable_config matches last_config",
        () -> dateEquals(stableConfig, deviceState.system.last_config));

    forCapability(LOGGING, () -> waitForLog(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL));

    setExtraField("break_json");
    untilHasInterestingSystemStatus(true);
    forCapability(LOGGING, () -> waitForLog(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL));
    Entry stateStatus = deviceState.system.status;
    info("Error message: " + stateStatus.message);
    debug("Error detail: " + stateStatus.detail);
    assertEquals(SYSTEM_CONFIG_PARSE, stateStatus.category);
    assertEquals(Level.ERROR.value(), (int) stateStatus.level);
    info("following stable_config " + isoConvert(stableConfig));
    info("following last_config " + isoConvert(deviceState.system.last_config));
    // The last_config should not be updated to not reflect the broken config.
    assertTrue("following stable_config matches last_config",
        dateEquals(stableConfig, deviceState.system.last_config));
    assertTrue("system operational", deviceState.system.operation.operational);
    forCapability(LOGGING, () -> {
      untilLogged(SYSTEM_CONFIG_PARSE, Level.ERROR);
      safeSleep(LOG_APPLY_DELAY_MS);
      checkNotLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    });

    // Will restore min_loglevel to the default of INFO.
    resetConfig(); // clears extra_field and interesting status checks

    forCapability(LOGGING, () -> {
      untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
      untilTrue("restored state synchronized",
          () -> dateEquals(deviceConfig.timestamp, deviceState.system.last_config));
    });

    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    untilTrue("last_config updated",
        () -> !dateEquals(stableConfig, deviceState.system.last_config)
    );
    assertTrue("system operational", deviceState.system.operation.operational);
    forCapability(LOGGING, () -> {
      untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
      // These should not be logged since the level was at INFO until the new config is applied.
      checkNotLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
      checkNotLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
    });
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = STABLE, bucket = SYSTEM)
  @Summary("Check that the device correctly handles an extra out-of-schema field")
  public void extra_config() {
    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    untilTrue("last_config not null", () -> deviceState.system.last_config != null);
    untilTrue("system operational", () -> deviceState.system.operation.operational);
    checkThatHasInterestingSystemStatus(false);
    final Date prevConfig = deviceState.system.last_config;
    setExtraField("Flabberguilstadt");
    untilLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilTrue("last_config updated", () -> !deviceState.system.last_config.equals(prevConfig));
    untilTrue("system operational", () -> deviceState.system.operation.operational);
    checkThatHasInterestingSystemStatus(false);
    untilLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    final Date updatedConfig = deviceState.system.last_config;
    setExtraField(null);
    untilLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilTrue("last_config updated again",
        () -> !deviceState.system.last_config.equals(updatedConfig)
    );
    untilTrue("system operational", () -> deviceState.system.operation.operational);
    untilLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = STABLE, bucket = SYSTEM)
  @Summary("Check that the device publishes minimum required log entries when receiving config")
  public void config_logging() {
    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    updateConfig("set min_loglevel to debug");
    forceConfigUpdate("resend config to device");
    untilLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
  }

}
