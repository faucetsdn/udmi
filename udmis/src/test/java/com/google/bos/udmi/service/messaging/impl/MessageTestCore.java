package com.google.bos.udmi.service.messaging.impl;

import udmi.schema.EndpointConfiguration;

/**
 * Core functions and constants for testing anything message related.
 */
public abstract class MessageTestCore {

  protected static final String TEST_NAMESPACE = "test-namespace";
  protected static final String TEST_SOURCE = "message_from";
  protected static final String TEST_DESTINATION = "message_to";

  protected abstract void augmentConfig(EndpointConfiguration configuration);

  protected void debug(String message) {
    System.err.println(message);
  }

  protected EndpointConfiguration getMessageConfig(boolean reversed) {
    EndpointConfiguration config = new EndpointConfiguration();
    config.hostname = TEST_NAMESPACE;
    config.recv_id = reversed ? TEST_DESTINATION : TEST_SOURCE;
    config.send_id = reversed ? TEST_SOURCE : TEST_DESTINATION;
    augmentConfig(config);
    return config;
  }
}
