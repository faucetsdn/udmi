package com.google.udmi.util;

import static java.lang.Boolean.TRUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.io.File;
import java.util.Map;
import org.junit.Test;
import udmi.schema.State;
import udmi.schema.StateSystemOperation;
import udmi.schema.SystemState;
import udmi.util.SchemaVersion;

/**
 * Unit tests for MessageDowngrader.
 */
public class MessageDowngraderTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setDateFormat(new ISO8601DateFormat());

  private static final String STATE_SCHEMA = "state";
  private static final String CONFIG_SCHEMA = "config";
  private static final String FUTURE_VERSION = "2.2.2";
  private static final String LOCALNET_CONFIG_FILE = "src/test/configs/localnet.json";
  private static final String LOCALNET_VERSION = "1.3.14";
  private static final IntNode OLD_VERSION = new IntNode(1);

  @Test
  public void stateDowngrade() {
    State state = new State();
    state.system = new SystemState();
    state.system.operation = new StateSystemOperation();
    state.system.operation.operational = TRUE;
    MessageDowngrader messageDowngrader = new MessageDowngrader(STATE_SCHEMA, state, null);
    Map<String, Object> downgrade = messageDowngrader.downgrade(SchemaVersion.VERSION_1_4_0);
    Object operational = GeneralUtils.getSubMap(downgrade, "system").get("operational");
    assertTrue("downgraded operational not TRUE", (Boolean) operational);
  }

  @Test
  public void downgradeToFutureVersion() {
    JsonNode simpleConfig = getSimpleTestConfig(LOCALNET_CONFIG_FILE);
    MessageDowngrader downgrader = new MessageDowngrader(CONFIG_SCHEMA, simpleConfig, null);
    assertThrows(IllegalStateException.class, () -> downgrader.downgrade(FUTURE_VERSION));
  }

  @Test
  public void families() {
    JsonNode simpleConfig = getSimpleTestConfig(LOCALNET_CONFIG_FILE);
    MessageDowngrader downgrader = new MessageDowngrader(CONFIG_SCHEMA, simpleConfig, null);
    downgrader.downgrade(OLD_VERSION.asText());
    assertEquals("version node", simpleConfig.get("version"), OLD_VERSION);
    assertTrue("families", !simpleConfig.get("localnet").has("families"));
    assertTrue("subsystem", simpleConfig.get("localnet").has("subsystem"));
  }

  private JsonNode getSimpleTestConfig(String filename) {
    File configFile = new File(filename);
    try {
      return OBJECT_MAPPER.readTree(configFile);
    } catch (Exception e) {
      throw new RuntimeException("While reading test config " + configFile.getAbsolutePath(), e);
    }
  }
}