package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.sequencer.Feature.Stage.ALPHA;
import static com.google.daq.mqtt.util.TimePeriodConstants.THREE_MINUTES_MS;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_RECEIVE;
import static udmi.schema.Category.SYSTEM_CONFIG_RECEIVE_LEVEL;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.SkipTest;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import udmi.schema.Entry;
import udmi.schema.Level;

/**
 * Validate basic device configuration handling operation, not specific to any device function.
 */
public class ConfigSequences extends SequenceBase {

  // Delay to wait to let a device apply a new config.
  private static final long CONFIG_THRESHOLD_SEC = 10;

  private boolean hasInterestingStatus() {
    return deviceState.system.status != null
        && deviceState.system.status.level >= Level.WARNING.value();
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Description("Check that last_update state is correctly set in response to a config update.")
  public void system_last_update() {
    untilTrue("state last_config matches config timestamp", this::stateMatchesConfigTimestamp);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Description("Check that the min log-level config is honored by the device.")
  @Feature()
  public void system_min_loglevel() {
    Integer savedLevel = deviceConfig.system.min_loglevel;
    assert SYSTEM_CONFIG_APPLY_LEVEL.value() >= savedLevel;
    assert SYSTEM_CONFIG_APPLY_LEVEL.value() < Level.WARNING.value();

    final Instant startTime = Instant.now();
    deviceConfig.system.min_loglevel = Level.INFO.value();
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    checkNotLogged(SYSTEM_CONFIG_APPLY, Level.WARNING);
    checkThat(String.format("device config resolved within %ss", CONFIG_THRESHOLD_SEC), () ->
        Instant.now().isBefore(startTime.plusSeconds(CONFIG_THRESHOLD_SEC)));

    deviceConfig.system.min_loglevel = Level.WARNING.value();
    updateConfig();
    // Nothing to actively wait for, so wait for some amount of time instead.
    safeSleep(CONFIG_THRESHOLD_SEC * 2000);
    checkNotLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);

    deviceConfig.system.min_loglevel = savedLevel;
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Description("Check that the device MQTT-acknowledges a sent config.")
  public void device_config_acked() {
    untilTrue("config acked", () -> configAcked);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Description("Check that the device correctly handles a broken (non-json) config message.")
  public void broken_config() {
    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    updateConfig();
    Date stableConfig = deviceConfig.timestamp;
    info("initial stable_config " + getTimestamp(stableConfig));
    untilTrue("state synchronized", () -> dateEquals(stableConfig, deviceState.system.last_config));
    info("initial last_config " + getTimestamp(deviceState.system.last_config));
    checkThat("initial stable_config matches last_config",
        () -> dateEquals(stableConfig, deviceState.system.last_config));
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);

    setExtraField("break_json");
    untilLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    checkThatHasInterestingSystemStatus(true);
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
    checkNotLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);

    resetConfig(); // clears extra_field
    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    checkThatHasInterestingSystemStatus(false);
    untilTrue("last_config updated",
        () -> !dateEquals(stableConfig, deviceState.system.last_config)
    );
    assertTrue("system operational", deviceState.system.operation.operational);
    untilLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    checkNotLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    checkNotLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Description("Check that the device correctly handles an extra out-of-schema field")
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

}
