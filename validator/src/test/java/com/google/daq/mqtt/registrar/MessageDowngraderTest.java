package com.google.daq.mqtt.registrar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import java.io.File;
import org.junit.Test;

/**
 * Unit tests for MessageDowngrader.
 */
public class MessageDowngraderTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setDateFormat(new ISO8601DateFormat());

  private static final String STATE_SCHEMA = "state";
  private static final String CONFIG_SCHEMA = "config";
  private static final String SIMPLE_TEST_FILE = "src/test/configs/localnet.json";

  @Test(expected = IllegalArgumentException.class)
  public void stateFail() {
    new MessageDowngrader(STATE_SCHEMA, new TextNode("hello"));
  }

  @Test
  public void noMolestar() {
    JsonNode simpleConfig = getSimpleTestConfig();
    MessageDowngrader downgrader = new MessageDowngrader(CONFIG_SCHEMA, simpleConfig);
    TextNode versionNode = new TextNode("2.2.2");
    downgrader.downgrade(versionNode);
    assertEquals("version node", versionNode, simpleConfig.get("version"));
    assertTrue("networks", simpleConfig.get("localnet").has("networks"));
    assertFalse("networks", simpleConfig.get("localnet").has("families"));
    assertFalse("networks", simpleConfig.get("localnet").has("subsystem"));
    assertTrue("subsystem", simpleConfig.get("system").has("operation"));
  }

  @Test
  public void version_1_3_13() {
    JsonNode simpleConfig = getSimpleTestConfig();
    MessageDowngrader downgrader = new MessageDowngrader(CONFIG_SCHEMA, simpleConfig);
    TextNode targetVersion = new TextNode("1.3.13");
    downgrader.downgrade(targetVersion);
    assertEquals("version node", targetVersion, simpleConfig.get("version"));
    assertTrue("subsystem", simpleConfig.get("localnet").has("subsystem"));
    assertFalse("subsystem", simpleConfig.get("system").has("operation"));
  }

  @Test
  public void version_1() {
    JsonNode simpleConfig = getSimpleTestConfig();
    MessageDowngrader downgrader = new MessageDowngrader(CONFIG_SCHEMA, simpleConfig);
    TextNode targetVersion = new TextNode("1");
    downgrader.downgrade(targetVersion);
    assertEquals("version node", targetVersion, simpleConfig.get("version"));
    assertTrue("subsystem", simpleConfig.get("localnet").has("subsystem"));
    assertFalse("subsystem", simpleConfig.get("system").has("operation"));
  }

  private JsonNode getSimpleTestConfig() {
    File configFile = new File(SIMPLE_TEST_FILE);
    try {
      return OBJECT_MAPPER.readTree(configFile);
    } catch (Exception e) {
      throw new RuntimeException("While reading test config " + configFile.getAbsolutePath(), e);
    }
  }
}