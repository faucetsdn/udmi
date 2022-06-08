package com.google.daq.mqtt.validator.validations;

import static com.google.daq.mqtt.validator.CleanDateFormat.cleanDate;
import static com.google.daq.mqtt.validator.CleanDateFormat.dateEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

  public static final String SYSTEM_CONFIG_RECEIVE = "system.config.receive";
  public static final String SYSTEM_CONFIG_PARSE = "system.config.parse";
  public static final String SYSTEM_CONFIG_APPLY = "system.config.apply";

  @Test
  public void system_last_update() {
    deviceConfig.system.min_loglevel = 400;
    untilTrue("state last_config match", () -> {
      Date expectedConfig = deviceConfig.timestamp;
      Date lastConfig = deviceState.system.last_config;
      debug("date match  " + getTimestamp(cleanDate(expectedConfig)) + " "
          + getTimestamp(cleanDate(lastConfig)));
      return dateEquals(expectedConfig, lastConfig);
    });
    untilTrue("config acked", () -> "true".equals(configAcked));
  }

  @Test
  public void broken_config() {
    untilTrue("system operational", () -> deviceState.system.operational);
    untilTrue("last_config not null", () -> deviceState.system.last_config != null);
    untilTrue("state no status", () -> deviceState.system.status == null);
    clearLogs();
    untilTrue("previous config/state synced",
        () -> dateEquals(deviceConfig.timestamp, deviceState.system.last_config)
    );
    extraField = "break_json";
    updateConfig();
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    AtomicReference<Date> stableConfig = new AtomicReference<>(null);
    untilTrue("state has status", () -> {
      boolean hasStatus = deviceState.system.status != null;
      if (!hasStatus) {
        stableConfig.set(deviceState.system.last_config);
      }
      return hasStatus;
    });
    Entry stateStatus = deviceState.system.status;
    info("Error message: " + stateStatus.message);
    info("Error detail: " + stateStatus.detail);
    assertEquals(SYSTEM_CONFIG_PARSE, stateStatus.category);
    assertEquals(Level.ERROR.value(), (int) stateStatus.level);
    Date matchedConfig = stableConfig.get();
    info("stable_config " + getTimestamp(matchedConfig));
    info("last_config " + getTimestamp(deviceState.system.last_config));
    assertTrue("last_config matches config timestamp",
        dateEquals(matchedConfig, deviceState.system.last_config));
    assertTrue("system operational", deviceState.system.operational);
    hasLogged(SYSTEM_CONFIG_PARSE, Level.ERROR);
    hasNotLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
    resetConfig();
    extraField = null;
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    untilTrue("state no status", () -> deviceState.system.status == null);
    untilTrue("last_config updated",
        () -> !dateEquals(matchedConfig, deviceState.system.last_config)
    );
    assertTrue("system operational", deviceState.system.operational);
    hasLogged(SYSTEM_CONFIG_PARSE, Level.INFO);
    hasLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
  }

  @Test
  public void extra_config() {
    untilTrue("last_config not null", () -> deviceState.system.last_config != null);
    untilTrue("system operational", () -> deviceState.system.operational);
    untilTrue("state no status", () -> deviceState.system.status == null);
    clearLogs();
    final Date prevConfig = deviceState.system.last_config;
    extraField = "Flabberguilstadt";
    updateConfig();
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    untilTrue("last_config updated", () -> !deviceState.system.last_config.equals(prevConfig));
    untilTrue("system operational", () -> deviceState.system.operational);
    untilTrue("state no status", () -> deviceState.system.status == null);
    hasLogged(SYSTEM_CONFIG_PARSE, Level.INFO);
    hasLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
    final Date updatedConfig = deviceState.system.last_config;
    extraField = null;
    updateConfig();
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    untilTrue("last_config updated again",
        () -> !deviceState.system.last_config.equals(updatedConfig)
    );
    untilTrue("system operational", () -> deviceState.system.operational);
    untilTrue("state no status", () -> deviceState.system.status == null);
    hasLogged(SYSTEM_CONFIG_PARSE, Level.INFO);
    hasLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
  }


}
