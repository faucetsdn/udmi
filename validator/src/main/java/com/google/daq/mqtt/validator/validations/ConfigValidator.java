package com.google.daq.mqtt.validator.validations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    clearLogs();
    Date expectedConfig = syncConfig();
    System.err.printf("%s expecting config %s%n", getTimestamp(), getTimestamp(expectedConfig));
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    hasLogged(SYSTEM_CONFIG_PARSE, Level.INFO);
    untilTrue(() -> expectedConfig.equals(deviceState.system.last_config),
        "state last_config match");
    hasLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
    System.err.printf("%s last_config match %s%n", getTimestamp(), getTimestamp(expectedConfig));
  }

  @Test
  public void broken_config() {
    untilTrue(() -> deviceState.system.operational, "system operational");
    untilTrue(() -> deviceState.system.last_config != null, "last_config not null");
    untilTrue(() -> !deviceState.system.statuses.containsKey("config"), "statuses missing config");
    clearLogs();
    Date initialConfig = syncConfig();
    untilTrue(() -> initialConfig.equals(deviceState.system.last_config),
        "previous config/state synced");
    info("saved last_config " + getTimestamp(initialConfig));
    extraField = "break_json";
    updateConfig();
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    untilTrue(() -> deviceState.system.statuses.containsKey("config"), "statuses has config");
    Entry configStatus = deviceState.system.statuses.get("config");
    assertEquals(SYSTEM_CONFIG_PARSE, configStatus.category);
    assertEquals(Level.ERROR.value(), (int) configStatus.level);
    assertEquals(initialConfig, deviceState.system.last_config);
    assertTrue("system operational", deviceState.system.operational);
    hasLogged(SYSTEM_CONFIG_PARSE, Level.ERROR);
    hasNotLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
    extraField = null;
    updateConfig();
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    untilTrue(() -> !deviceState.system.statuses.containsKey("config"), "statuses not config");
    untilTrue(() -> !deviceState.system.last_config.equals(initialConfig), "last_config updated");
    assertTrue("system operational", deviceState.system.operational);
    hasLogged(SYSTEM_CONFIG_PARSE, Level.INFO);
    hasLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
  }

  @Test
  public void extra_config() {
    untilTrue(() -> deviceState.system.last_config != null, "last_config not null");
    untilTrue(() -> deviceState.system.operational, "system operational");
    untilTrue(() -> !deviceState.system.statuses.containsKey("config"), "statuses missing config");
    clearLogs();
    final Date prevConfig = deviceState.system.last_config;
    extraField = "Flabberguilstadt";
    updateConfig();
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    untilTrue(() -> !deviceState.system.last_config.equals(prevConfig), "last_config updated");
    untilTrue(() -> deviceState.system.operational, "system operational");
    untilTrue(() -> !deviceState.system.statuses.containsKey("config"), "statuses missing config");
    hasLogged(SYSTEM_CONFIG_PARSE, Level.INFO);
    hasLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
    final Date updatedConfig = deviceState.system.last_config;
    extraField = null;
    updateConfig();
    hasLogged(SYSTEM_CONFIG_RECEIVE, Level.INFO);
    untilTrue(() -> !deviceState.system.last_config.equals(updatedConfig),
        "last_config updated again");
    untilTrue(() -> deviceState.system.operational, "system operational");
    untilTrue(() -> !deviceState.system.statuses.containsKey("config"), "statuses missing config");
    hasLogged(SYSTEM_CONFIG_PARSE, Level.INFO);
    hasLogged(SYSTEM_CONFIG_APPLY, Level.INFO);
  }


}
