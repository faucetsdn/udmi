package com.google.bos.udmi.service.messaging.impl;

import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;

/**
 * Core functions and constants for testing anything message related.
 */
public abstract class MessageTestCore {

  public static final String TEST_DEVICE = "bacnet-3104810";
  public static final String TEST_REGION = "us-central1";
  public static final String TEST_REGISTRY = "TEST_REGISTRY";
  public static final String TEST_PROJECT = "TEST_PROJECT";
  public static final String TEST_POINT = "test_point";
  public static final String TEST_NUMID = "7239821792187321";
  protected static final String TEST_NAMESPACE = "test-namespace";
  protected static final String TEST_SOURCE = "message_from";
  protected static final String TEST_DESTINATION = "message_to";
  protected static final String TEST_VERSION = "1.32";
  protected static final String TEST_REF = "g123456789";

  protected void augmentConfig(EndpointConfiguration configuration) {
    configuration.protocol = Protocol.LOCAL;
  }

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
