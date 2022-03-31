package com.google.daq.mqtt.validator.validations;

import static com.google.daq.mqtt.validator.CleanDateFormat.dateEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.daq.mqtt.validator.CleanDateFormat;
import com.google.daq.mqtt.validator.SequenceValidator;
import java.util.Date;
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
    Date expectedConfig = syncConfig();
    info(String.format("%s expecting config %s%n", getTimestamp(), getTimestamp(expectedConfig)));
    untilTrue(() -> dateEquals(expectedConfig, deviceState.system.last_config),
        "state last_config match");
    info(String.format("%s last_config match %s%n", getTimestamp(), getTimestamp(expectedConfig)));
  }

  @Test
  public void broken_config() {
    untilTrue(() -> deviceState.system.operational, "system operational");
    untilTrue(() -> deviceState.system.last_config != null, "last_config not null");
    untilTrue(() -> deviceState.system.status == null, "state no status");
    clearLogs();
    final Date initialConfig = syncConfig();
    untilTrue(() -> dateEquals(initialConfig, deviceState.system.last_config),
        "previous config/state synced");
    info("saved last_config " + getTimestamp(initialConfig));
    extraField = "break_json";
    updateConfig();
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    untilTrue(() -> deviceState.system.status != null, "state has status");
    Entry stateStatus = deviceState.system.status;
    info("Error message: " + stateStatus.message);
    info("Error detail: " + stateStatus.detail);
    assertEquals(SYSTEM_CONFIG_PARSE, stateStatus.category);
    assertEquals(Level.ERROR.value(), (int) stateStatus.level);
    info("initialConfig " + getTimestamp(initialConfig));
    info("lastConfig " + getTimestamp(CleanDateFormat.cleanDate(deviceState.system.last_config)));
    assertTrue("matches initial last_config",
        dateEquals(initialConfig, deviceState.system.last_config));
    assertTrue("system operational", deviceState.system.operational);
    hasLogged(SYSTEM_CONFIG_PARSE, Level.ERROR);
    hasNotLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
    extraField = null;
    updateConfig();
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    untilTrue(() -> deviceState.system.status == null, "state no status");
    untilTrue(() -> !dateEquals(initialConfig, deviceState.system.last_config),
        "last_config updated");
    assertTrue("system operational", deviceState.system.operational);
    hasLogged(SYSTEM_CONFIG_PARSE, Level.INFO);
    hasLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
  }

  @Test
  public void extra_config() {
    untilTrue(() -> deviceState.system.last_config != null, "last_config not null");
    untilTrue(() -> deviceState.system.operational, "system operational");
    untilTrue(() -> deviceState.system.status == null, "state no status");
    clearLogs();
    final Date prevConfig = deviceState.system.last_config;
    extraField = "Flabberguilstadt";
    updateConfig();
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    untilTrue(() -> !deviceState.system.last_config.equals(prevConfig), "last_config updated");
    untilTrue(() -> deviceState.system.operational, "system operational");
    untilTrue(() -> deviceState.system.status == null, "state no status");
    hasLogged(SYSTEM_CONFIG_PARSE, Level.INFO);
    hasLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
    final Date updatedConfig = deviceState.system.last_config;
    extraField = null;
    updateConfig();
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    untilTrue(() -> !deviceState.system.last_config.equals(updatedConfig),
        "last_config updated again");
    untilTrue(() -> deviceState.system.operational, "system operational");
    untilTrue(() -> deviceState.system.status == null, "state no status");
    hasLogged(SYSTEM_CONFIG_PARSE, Level.INFO);
    hasLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
  }


}
