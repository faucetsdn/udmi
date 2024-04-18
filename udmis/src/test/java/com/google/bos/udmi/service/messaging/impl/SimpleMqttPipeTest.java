package com.google.bos.udmi.service.messaging.impl;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import udmi.schema.Auth_provider;
import udmi.schema.Basic;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;

class SimpleMqttPipeTest extends MessagePipeTestBase {

  // Ex. broker URL: MQTT_TEST_BROKER=tcp://localhost:1883
  private static final String TEST_BROKER_ENV = "MQTT_TEST_BROKER";
  private static final String BROKER_URL = System.getenv(TEST_BROKER_ENV);
  private static final String URL_FORMAT = "(.+)://(.+):(.+)";
  private static final Pattern URL_PATTERN = Pattern.compile(URL_FORMAT);
  private static final String TEST_USERNAME = "scrumptious";
  private static final String TEST_PASSWORD = "aardvark";

  protected boolean environmentIsEnabled() {
    boolean environmentEnabled = !Strings.isNullOrEmpty(BROKER_URL);
    if (!environmentEnabled) {
      debug("Skipping test because no broker defined in " + TEST_BROKER_ENV);
    }
    return environmentEnabled;
  }

  public void augmentConfig(EndpointConfiguration endpoint) {
    Matcher matcher = URL_PATTERN.matcher(BROKER_URL.trim());
    checkState(matcher.matches(), "Endpoint URL does not match format " + URL_FORMAT);
    endpoint.protocol = Protocol.MQTT;
    endpoint.transport = EndpointConfiguration.Transport.fromValue(matcher.group(1));
    endpoint.hostname = matcher.group(2);
    endpoint.port = Integer.parseInt(matcher.group(3));
    endpoint.auth_provider = makeBasicAuth();
  }

  private Auth_provider makeBasicAuth() {
    Auth_provider authProvider = new Auth_provider();
    authProvider.basic = new Basic();
    authProvider.basic.username = TEST_USERNAME;
    authProvider.basic.password = TEST_PASSWORD;
    return authProvider;
  }

}