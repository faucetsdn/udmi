package com.google.udmi.util;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import udmi.schema.State;

/**
 * Unit tests for message upgrading.
 */
public class MessageUpgraderTest {

  public static final String BAD_VERSION = "1.4.";

  @Test(expected = Exception.class)
  public void badVersion() {
    State stateMessage = new State();
    stateMessage.version = BAD_VERSION;
    new MessageUpgrader("state", stateMessage);
  }

  @Test
  public void eventsSystemUpgrade() {
    ObjectNode message = MessageUpgrader.NODE_FACTORY.objectNode();
    message.put("version", "1.4.1");
    ObjectNode entry1 = MessageUpgrader.NODE_FACTORY.objectNode();
    entry1.put("category", "system.network.disconnect");
    ObjectNode entry2 = MessageUpgrader.NODE_FACTORY.objectNode();
    entry2.put("category", "system.network.connect");
    ObjectNode entry3 = MessageUpgrader.NODE_FACTORY.objectNode();
    entry3.put("category", "system.auth.login");
    message.putArray("logentries").add(entry1).add(entry2).add(entry3);

    MessageUpgrader upgrader = new MessageUpgrader("events_system", message);
    JsonNode upgraded = (JsonNode) upgrader.upgrade();
    
    JsonNode logentries = upgraded.get("logentries");
    assertEquals("localnet.network.disconnect", logentries.get(0).get("category").asText());
    assertEquals("localnet.network.connect", logentries.get(1).get("category").asText());
    assertEquals("system.auth.login", logentries.get(2).get("category").asText());
  }

}