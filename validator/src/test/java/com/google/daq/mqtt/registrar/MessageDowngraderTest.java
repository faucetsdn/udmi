package com.google.daq.mqtt.registrar;

import static org.junit.Assert.assertEquals;
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
  private static final String FUTURE_VERSION = "2.2.2";
  private static final String LOCALNET_CONFIG_FILE = "src/test/configs/localnet.json";
  private static final String LOCALNET_VERSION = "1.3.14";
  private static final String OLD_VERSION = "1";

  @Test(expected = IllegalArgumentException.class)
  public void stateFail() {
    new MessageDowngrader(STATE_SCHEMA, new TextNode("hello"));
  }

  @Test
  public void noMolestar() {
    JsonNode simpleConfig = getSimpleTestConfig(LOCALNET_CONFIG_FILE);
    MessageDowngrader downgrader = new MessageDowngrader(CONFIG_SCHEMA, simpleConfig);
    downgrader.downgrade(new TextNode(FUTURE_VERSION));
    assertEquals("version node", simpleConfig.get("version"), new TextNode(LOCALNET_VERSION));
    assertTrue("networks", simpleConfig.get("localnet").has("networks"));
    assertTrue("subsystem", !simpleConfig.get("localnet").has("subsystem"));
  }

  @Test
  public void networks() {
    JsonNode simpleConfig = getSimpleTestConfig(LOCALNET_CONFIG_FILE);
    MessageDowngrader downgrader = new MessageDowngrader(CONFIG_SCHEMA, simpleConfig);
    downgrader.downgrade(new TextNode(OLD_VERSION));
    assertEquals("version node", simpleConfig.get("version"), new TextNode(OLD_VERSION));
    assertTrue("networks", !simpleConfig.get("localnet").has("networks"));
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