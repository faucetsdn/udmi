package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.validator.CleanDateFormat.dateEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static udmi.schema.Category.BLOBSET_BLOB_APPLY;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY;
import static udmi.schema.Category.SYSTEM_CONFIG_APPLY_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE;
import static udmi.schema.Category.SYSTEM_CONFIG_PARSE_LEVEL;
import static udmi.schema.Category.SYSTEM_CONFIG_RECEIVE;
import static udmi.schema.Category.SYSTEM_CONFIG_RECEIVE_LEVEL;

import com.google.daq.mqtt.sequencer.SequenceRunner;
import com.google.daq.mqtt.util.JsonUtil;
import java.util.Date;
import java.util.HashMap;
import org.junit.Test;
import udmi.schema.BlobBlobsetConfig;
import udmi.schema.BlobBlobsetConfig.BlobPhase;
import udmi.schema.BlobsetConfig;
import udmi.schema.Entry;
import udmi.schema.Level;

/**
 * Validate basic device configuration handling operation, not specific to any device function.
 */
public class ConfigSequences extends SequenceRunner {

  @Test
  @Description("Push endpoint config message to device, resulting in success")
  public void endpoint_config_success() {
    // blob _iot_endpoint_config = ...
  }

  @Test
  @Description("Push endpoint config message to device, resulting in invalid")
  public void endpoint_config_invalid() {
    // blob _iot_endpoint_config = ...
    BlobBlobsetConfig cfg = new BlobBlobsetConfig();
    cfg.phase = BlobPhase.FINAL;
    // { protocol=mqtt, client_id=test_project/device; hostname=localhost }
    cfg.base64 = "ewogICJwcm90b2NvbCI6ICJtcXR0IiwKICAiY2xpZW50X2lkIjogInRlc3RfcHJvamVjdC9kZXZp" +
        "Y2UiLAogICJob3N0bmFtZSI6ICJsb2NhbGhvc3QiCn0K";
    cfg.content_type = "application/json";
    deviceConfig.blobset = new BlobsetConfig();
    deviceConfig.blobset.blobs = new HashMap<String, BlobBlobsetConfig>();
    deviceConfig.blobset.blobs.put("_iot_endpoint_config", cfg);
    info("Before updateConfig");
    updateConfig();
    info("After updateConfig");
    untilTrue("pubber has attempted endpoint reconfig", () -> {
      try {
      if (deviceState.blobset.blobs.get("_iot_endpoint_config").status == null) {
        return false;
      }
      } catch (NullPointerException e) {
        return false;
      }
      Entry stateStatus = deviceState.blobset.blobs.get("_iot_endpoint_config").status;
      if ((stateStatus.category == SYSTEM_CONFIG_PARSE) && ((int)stateStatus.level == Level.ERROR.value())) {
        return true;
      }
      return false;
    });
    info("After hasLogged");
    Entry stateStatus = deviceState.blobset.blobs.get("_iot_endpoint_config").status;
    info("Error message: " + stateStatus.message);
    info("Error detail: " + stateStatus.detail);
    assertEquals(SYSTEM_CONFIG_PARSE, stateStatus.category);
    assertEquals(Level.ERROR.value(), (int) stateStatus.level);
  }

  @Test()
  @Description("Check that last_update state is correctly set in response to a config update.")
  public void system_last_update() {
    untilTrue("state last_config matches config timestamp", () -> {
      Date expectedConfig = deviceConfig.timestamp;
      Date lastConfig = deviceState.system.last_config;
      return dateEquals(expectedConfig, lastConfig);
    });
  }

  @Test
  @Description("Check that the min log-level config is honored by the device.")
  public void system_min_loglevel() {
    clearLogs();
    Integer savedLevel = deviceConfig.system.min_loglevel;
    deviceConfig.system.min_loglevel = Level.WARNING.value();
    hasNotLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    deviceConfig.system.min_loglevel = savedLevel;
    hasLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
  }

  @Test
  @Description("Check that the device MQTT-acknowledges a sent config.")
  public void device_config_acked() {
    untilTrue("config acked", () -> configAcked);
  }

  @Test
  @Description("Check that the device correctly handles a broken (non-json) config message.")
  public void broken_config() {
    deviceConfig.system.min_loglevel = Level.DEBUG.value();
    untilFalse("no interesting status", this::hasInterestingStatus);
    untilTrue("clean config/state synced", this::configUpdateComplete);
    Date stableConfig = deviceConfig.timestamp;
    untilTrue("state synchronized", () -> dateEquals(stableConfig, deviceState.system.last_config));
    info("initial stable_config " + JsonUtil.getTimestamp(stableConfig));
    info("initial last_config " + JsonUtil.getTimestamp(deviceState.system.last_config));
    checkThat("initial stable_config matches last_config",
        () -> dateEquals(stableConfig, deviceState.system.last_config));
    clearLogs();
    extraField = "break_json";
    hasLogged(SYSTEM_CONFIG_RECEIVE, SYSTEM_CONFIG_RECEIVE_LEVEL);
    untilTrue("has interesting status", this::hasInterestingStatus);
    Entry stateStatus = deviceState.system.status;
    info("Error message: " + stateStatus.message);
    info("Error detail: " + stateStatus.detail);
    assertEquals(SYSTEM_CONFIG_PARSE, stateStatus.category);
    assertEquals(Level.ERROR.value(), (int) stateStatus.level);
    info("following stable_config " + JsonUtil.getTimestamp(stableConfig));
    info("following last_config " + JsonUtil.getTimestamp(deviceState.system.last_config));
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
    hasLogged(SYSTEM_CONFIG_APPLY, SYSTEM_CONFIG_APPLY_LEVEL);
    hasLogged(SYSTEM_CONFIG_PARSE, SYSTEM_CONFIG_PARSE_LEVEL);
  }

  private boolean hasInterestingStatus() {
    return deviceState.system.status != null
        && deviceState.system.status.level >= Level.WARNING.value();
  }

  @Test
  @Description("Check that the device correctly handles an extra out-of-schema field")
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
