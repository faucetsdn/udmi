package com.google.udmi.util;


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
}