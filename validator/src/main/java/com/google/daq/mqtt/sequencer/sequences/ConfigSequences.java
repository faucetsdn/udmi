package com.google.daq.mqtt.sequencer.sequences;

import static com.google.common.base.Preconditions.checkState;
import static com.google.daq.mqtt.util.TimePeriodConstants.ONE_MINUTE_MS;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_RECEIVE;
import static udmi.schema.Category.SYSTEM_CONFIG_RECEIVE_LEVEL;
import static udmi.schema.FeatureEnumeration.FeatureStage.ALPHA;
import static udmi.schema.FeatureEnumeration.FeatureStage.BETA;
import static udmi.schema.FeatureEnumeration.FeatureStage.STABLE;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.Summary;
import com.google.daq.mqtt.sequencer.ValidateSchema;
import java.time.Instant;
import java.util.Date;
import org.junit.Test;
import udmi.schema.Entry;
import udmi.schema.Level;

/**
 * Validate basic device configuration handling operation, not specific to any device function.
 */
public class ConfigSequences extends SequenceBase {

  // Delay to wait to let a device apply a new config.
  private static final long CONFIG_THRESHOLD_SEC = 10;
  // Delay after receiving a parse error to ensure an apply entry has not been received.
  private static final long LOG_APPLY_DELAY_MS = 1000;

  @Test(timeout = ONE_MINUTE_MS)
  @Feature(stage = STABLE, bucket = SYSTEM)
  @Summary("Check that last_update state is correctly set in response to a config update.")
  @ValidateSchema
  public void system_last_update() {
    untilTrue("state last_config matches config timestamp", this::stateMatchesConfigTimestamp);
    ensureStateUpdate();
  }

  @Test
  @Feature(stage = ALPHA, bucket = SYSTEM)
  @ValidateSchema
  public void valid_serial_no() {
    ifNullSkipTest(serialNo, "No test serial number provided");
    untilTrue("received serial number matches", () -> serialNo.equals(lastSerialNo));
    ensureStateUpdate();
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = ALPHA, bucket = SYSTEM)
  @Summary("Check that the min log-level config is honored by the device.")
  public void system_min_loglevel() {
    Integer savedLevel = deviceConfig.system.min_loglevel;
    checkState(SYSTEM_CONFIG_APPLY_LEVEL.value() >= savedLevel, "invalid saved level");
    checkState(SYSTEM_CONFIG_APPLY_LEVEL.value() < Level.WARNING.value(),
        "invalid config apply level");

    final Instant startTime = Instant.now();
    deviceConfig.system.min_loglevel = Level.INFO.value();
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    checkNotLogged(SYSTEM_CONFIG_APPLY, Level.WARNING);
    checkThat(String.format("device config resolved within %ss", CONFIG_THRESHOLD_SEC), () ->
        Instant.now().isBefore(startTime.plusSeconds(CONFIG_THRESHOLD_SEC)));

    deviceConfig.system.min_loglevel = Level.WARNING.value();
    updateConfig("warning loglevel");
    // Nothing to actively wait for, so wait for some amount of time instead.
    safeSleep(CONFIG_THRESHOLD_SEC * 2000);
    checkNotLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);

    deviceConfig.system.min_loglevel = savedLevel;
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = BETA, bucket = SYSTEM)
  @Summary("Check that the device MQTT-acknowledges a sent config.")
  public void device_config_acked() {
    untilTrue("config acked", () -> configAcked);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = ALPHA, bucket = SYSTEM)
  @Summary("Check that the device correctly handles a broken (non-json) config message.")
  public void broken_config() {
    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    updateConfig("starting broken_config");
    Date stableConfig = deviceConfig.timestamp;
    info("initial stable_config " + getTimestamp(stableConfig));
    untilTrue("initial state synchronized",
        () -> dateEquals(stableConfig, deviceState.system.last_config));
    info("initial last_config " + getTimestamp(deviceState.system.last_config));
    checkThat("initial stable_config matches last_config",
        () -> dateEquals(stableConfig, deviceState.system.last_config));
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);

    setExtraField("break_json");
    untilLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilHasInterestingSystemStatus(true);
    Entry stateStatus = deviceState.system.status;
    info("Error message: " + stateStatus.message);
    debug("Error detail: " + stateStatus.detail);
    assertEquals(SYSTEM_CONFIG_PARSE, stateStatus.category);
    assertEquals(Level.ERROR.value(), (int) stateStatus.level);
    info("following stable_config " + getTimestamp(stableConfig));
    info("following last_config " + getTimestamp(deviceState.system.last_config));
    // The last_config should not be updated to not reflect the broken config.
    assertTrue("following stable_config matches last_config",
        dateEquals(stableConfig, deviceState.system.last_config));
    assertTrue("system operational", deviceState.system.operation.operational);
    untilLogged(SYSTEM_CONFIG_PARSE, Level.ERROR);
    safeSleep(LOG_APPLY_DELAY_MS);
    checkNotLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);

    // Will restore min_loglevel to the default of INFO.
    resetConfig(); // clears extra_field
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    untilTrue("restored state synchronized",
        () -> dateEquals(deviceConfig.timestamp, deviceState.system.last_config));

    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    checkThatHasInterestingSystemStatus(false);
    untilTrue("last_config updated",
        () -> !dateEquals(stableConfig, deviceState.system.last_config)
    );
    assertTrue("system operational", deviceState.system.operation.operational);
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    // These should not be logged since the level was at INFO until the new config is applied.
    checkNotLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    checkNotLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
  }

  @Test(timeout = ONE_MINUTE_MS)
  @Feature(stage = BETA, bucket = SYSTEM)
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
    checkThatHasInterestingSystemStatus(false);
    untilLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
  }

  @Test(timeout = ONE_MINUTES_MS)
  @Feature(stage = BETA, bucket = SYSTEM)
  @Summary("Check that the device publishes minimum required log entries when receiving config")
  public void config_logging() {
    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    updateConfig("set min_loglevel to debug");
    safeSleep(CONFIG_THRESHOLD_SEC * 2000);
    forceConfigUpdate("send config");
    untilLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);

  }

}
