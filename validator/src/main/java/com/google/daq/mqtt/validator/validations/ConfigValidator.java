package com.google.daq.mqtt.validator.validations;

import static com.google.daq.mqtt.validator.CleanDateFormat.cleanDate;
import static com.google.daq.mqtt.validator.CleanDateFormat.dateEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_RECEIVE;
import static udmi.schema.Category.SYSTEM_CONFIG_RECEIVE_LEVEL;

import com.google.daq.mqtt.validator.SequenceValidator;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import udmi.schema.Entry;
import udmi.schema.Level;

/**
 * Validate basic device configuration handling operation, not specific to any device function.
 */
public class ConfigValidator extends SequenceValidator {

  @Test
  public void system_last_update() {
    deviceConfig.system.min_loglevel = Level.WARNING.value();
    untilTrue("state last_config match", () -> {
      Date expectedConfig = deviceConfig.timestamp;
      Date lastConfig = deviceState.system.last_config;
      debug("date match  " + getTimestamp(cleanDate(expectedConfig)) + " "
          + getTimestamp(cleanDate(lastConfig)));
      return dateEquals(expectedConfig, lastConfig);
    });
  }

  @Test
  public void device_config_acked() {
    untilTrue("config acked", () -> configAcked);
  }

  @Test
  public void broken_config() {
    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    untilTrue("clean config/state synced", this::configUpdateComplete);
    untilFalse("no interesting status", this::hasInterestingStatus);
    Date stableConfig = deviceConfig.timestamp;
    info("initial stable_config " + getTimestamp(stableConfig));
    info("initial last_config " + getTimestamp(deviceState.system.last_config));
    assertTrue("initial stable_config matches last_config",
        dateEquals(stableConfig, deviceState.system.last_config));
    clearLogs();
    extraField = "break_json";
    hasLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilTrue("has interesting status", this::hasInterestingStatus);
    Entry stateStatus = deviceState.system.status;
    info("Error message: " + stateStatus.message);
    info("Error detail: " + stateStatus.detail);
    assertEquals(SYSTEM_CONFIG_PARSE, stateStatus.category);
    assertEquals(Level.ERROR.value(), (int) stateStatus.level);
    info("following stable_config " + getTimestamp(stableConfig));
    info("following last_config " + getTimestamp(deviceState.system.last_config));
    assertTrue("following stable_config matches last_config",
        dateEquals(stableConfig, deviceState.system.last_config));
    assertTrue("system operational", deviceState.system.operational);
    hasLogged(SYSTEM_CONFIG_PARSE, Level.ERROR);
    hasNotLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    resetConfig(); // clears extra_field
    hasLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilFalse("no interesting status", this::hasInterestingStatus);
    untilTrue("last_config updated",
        () -> !dateEquals(stableConfig, deviceState.system.last_config)
    );
    assertTrue("system operational", deviceState.system.operational);
    hasLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
    hasLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
  }

  private boolean hasInterestingStatus() {
    return deviceState.system.status != null
        && deviceState.system.status.level >= Level.WARNING.value();
  }

  @Test
  public void extra_config() {
    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    untilTrue("last_config not null", () -> deviceState.system.last_config != null);
    untilTrue("system operational", () -> deviceState.system.operational);
    untilFalse("no interesting status", this::hasInterestingStatus);
    clearLogs();
    final Date prevConfig = deviceState.system.last_config;
    extraField = "Flabberguilstadt";
    hasLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilTrue("last_config updated", () -> !deviceState.system.last_config.equals(prevConfig));
    untilTrue("system operational", () -> deviceState.system.operational);
    untilFalse("no interesting status", this::hasInterestingStatus);
    hasLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
    hasLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    final Date updatedConfig = deviceState.system.last_config;
    extraField = null;
    hasLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilTrue("last_config updated again",
        () -> !deviceState.system.last_config.equals(updatedConfig)
    );
    untilTrue("system operational", () -> deviceState.system.operational);
    untilFalse("no interesting status", this::hasInterestingStatus);
    hasLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
    hasLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
  }


}
