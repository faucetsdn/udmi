package com.google.bos.udmi.service.messaging;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.JsonUtil.safeSleep;

import com.google.bos.udmi.service.messaging.MessageBase.Bundle;
import com.google.common.base.Strings;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import udmi.schema.Auth_provider;
import udmi.schema.Basic;
import udmi.schema.EndpointConfiguration;
import udmi.schema.EndpointConfiguration.Protocol;
import udmi.schema.MessageConfiguration;
import udmi.schema.MessageConfiguration.Transport;

class SimpleMqttPipeTest extends MessageTestBase {

  public static final String MQTT_TEST_BROKER = "MQTT_TEST_BROKER";
  // Ex. broker URL: MQTT_TEST_BROKER=tcp://localhost:1883
  private static final String BROKER_URL = System.getenv(MQTT_TEST_BROKER);
  private static final String URL_FORMAT = "(.+)://(.+):(.+)";
  private static final Pattern URL_PATTERN = Pattern.compile(URL_FORMAT);
  private static final String TEST_USERNAME = "scrumptus";
  private static final String TEST_PASSWORD = "aardvark";
  public static final int MESSAGE_SYNC_DELAY_MS = 10000;

  protected boolean environmentIsEnabled() {
    boolean environmentEnabled = !Strings.isNullOrEmpty(BROKER_URL);
    if (!environmentEnabled) {
      System.err.println("Skipping test because no broker defined in " + MQTT_TEST_BROKER);
    }
    return environmentEnabled;
  }

  protected MessageBase getTestMessagePipeCore(boolean reversed) {
    MessageConfiguration messageConfiguration = new MessageConfiguration();
    messageConfiguration.transport = Transport.MQTT;
    messageConfiguration.endpoint = makeMqttEndpoint();
    messageConfiguration.namespace = TEST_NAMESPACE;
    messageConfiguration.source = reversed ? TEST_SOURCE : TEST_DESTINATION;
    messageConfiguration.destination = reversed ? TEST_DESTINATION : TEST_SOURCE;
    return new SimpleMqttPipe(messageConfiguration);
  }

  private EndpointConfiguration makeMqttEndpoint() {
    Matcher matcher = URL_PATTERN.matcher(BROKER_URL.trim());
    checkState(matcher.matches(), "Endpoint URL does not match format " + URL_FORMAT);
    EndpointConfiguration endpoint = new EndpointConfiguration();
    endpoint.protocol = Protocol.MQTT;
    endpoint.transport = EndpointConfiguration.Transport.fromValue(matcher.group(1));
    endpoint.hostname = matcher.group(2);
    endpoint.port = Integer.parseInt(matcher.group(3));
    endpoint.auth_provider = makeBasicAuth();
    return endpoint;
  }

  private Auth_provider makeBasicAuth() {
    Auth_provider authProvider = new Auth_provider();
    authProvider.basic = new Basic();
    authProvider.basic.username = TEST_USERNAME;
    authProvider.basic.password = TEST_PASSWORD;
    return authProvider;
  }

  protected List<Bundle> drainPipes() {
    // Extra sync time since message broker is external and async.
    safeSleep(MESSAGE_SYNC_DELAY_MS);
    return super.drainPipes();
  }
}