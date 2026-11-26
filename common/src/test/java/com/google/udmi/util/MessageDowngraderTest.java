package com.google.udmi.util;

import static java.lang.Boolean.TRUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import udmi.schema.Config;
import udmi.schema.FamilyLocalnetConfig;
import udmi.schema.FamilyLocalnetModel;
import udmi.schema.GatewayConfig;
import udmi.schema.LocalnetConfig;
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

  @Test
  public void downgradeGatewayTargetWithoutLocalnet() {
    Config config = new Config();
    config.version = SchemaVersion.CURRENT.key();
    config.gateway = new GatewayConfig();
    config.gateway.target = new FamilyLocalnetModel();
    config.gateway.target.family = "bacnet";
    config.gateway.target.addr = "12345";
    MessageDowngrader downgrader = new MessageDowngrader(CONFIG_SCHEMA, config, null);
    Map<String, Object> downgraded = downgrader.downgrade(SchemaVersion.VERSION_1_4_1);
    Map<String, Object> families =
        GeneralUtils.getSubMap(GeneralUtils.getSubMap(downgraded, "localnet"), "families");
    assertEquals("12345", GeneralUtils.getSubMap(families, "bacnet").get("addr"));
  }

  @Test
  public void downgradeGatewayTargetEdgeCases() {
    Config networksOnly = new Config();
    networksOnly.version = SchemaVersion.CURRENT.key();
    networksOnly.localnet = new LocalnetConfig();
    networksOnly.localnet.networks = new HashMap<>(Map.of("modbus_1", new FamilyLocalnetConfig()));
    networksOnly.gateway = new GatewayConfig();
    networksOnly.gateway.target = new FamilyLocalnetModel();
    networksOnly.gateway.target.family = "modbus";
    Map<String, Object> noAddrDowngrade =
        new MessageDowngrader(CONFIG_SCHEMA, networksOnly, null)
            .downgrade(SchemaVersion.VERSION_1_4_1);
    Map<String, Object> modbusFamily = GeneralUtils.getSubMap(
        GeneralUtils.getSubMap(GeneralUtils.getSubMap(noAddrDowngrade, "localnet"), "families"),
        "modbus");
    assertNull("omitted target.addr should stay absent", modbusFamily.get("addr"));

    ObjectNode nullFamiliesNode = OBJECT_MAPPER.createObjectNode();
    nullFamiliesNode.put("version", SchemaVersion.CURRENT.key());
    nullFamiliesNode.putObject("localnet").putNull("families");
    ObjectNode targetNode = nullFamiliesNode.putObject("gateway").putObject("target");
    targetNode.put("family", "bacnet");
    targetNode.put("addr", "99");
    Map<String, Object> nullNodeDowngrade =
        new MessageDowngrader(CONFIG_SCHEMA, nullFamiliesNode, null)
            .downgrade(SchemaVersion.VERSION_1_4_1);
    Map<String, Object> bacnetFamily = GeneralUtils.getSubMap(
        GeneralUtils.getSubMap(GeneralUtils.getSubMap(nullNodeDowngrade, "localnet"), "families"),
        "bacnet");
    assertEquals("99", bacnetFamily.get("addr"));

    Config noFamilyConfig = new Config();
    noFamilyConfig.version = SchemaVersion.CURRENT.key();
    noFamilyConfig.gateway = new GatewayConfig();
    noFamilyConfig.gateway.target = new FamilyLocalnetModel();
    noFamilyConfig.gateway.target.addr = "42";
    Map<String, Object> noFamilyDowngrade =
        new MessageDowngrader(CONFIG_SCHEMA, noFamilyConfig, null)
            .downgrade(SchemaVersion.VERSION_1_4_1);
    assertNull("omitted target.family should not create localnet",
        noFamilyDowngrade.get("localnet"));
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