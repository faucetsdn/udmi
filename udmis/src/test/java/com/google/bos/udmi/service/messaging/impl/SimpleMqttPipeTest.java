package com.google.bos.udmi.service.messaging.impl;

import static com.google.common.base.Preconditions.checkState;
import static com.google.udmi.util.Common.DEVICE_ID_KEY;
import static com.google.udmi.util.Common.GATEWAY_ID_KEY;
import static com.google.udmi.util.Common.REGISTRY_ID_PROPERTY_KEY;
import static com.google.udmi.util.Common.SOURCE_KEY;
import static com.google.udmi.util.Common.SUBFOLDER_PROPERTY_KEY;
import static com.google.udmi.util.Common.SUBTYPE_PROPERTY_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.base.Strings;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
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
  private static final String DEVICE_USERNAME = "rocket";
  private static final String DEVICE_PASSWORD = "monkey";
  private static final String DEVICE_CLIENT_ID = "ZZ-TRI-FECTA/testing";
  private static final String SERVICE_USERNAME = "kiwi";
  private static final String SERVICE_PASSWORD = "possum";
  private static final String SERVICE_CLIENT_ID = "ZZ-TRI-FECTA/other";

  protected boolean environmentIsEnabled() {
    boolean environmentEnabled = !Strings.isNullOrEmpty(BROKER_URL);
    if (!environmentEnabled) {
      debug("Skipping test because no broker defined in " + TEST_BROKER_ENV);
    }
    return environmentEnabled;
  }

  private Auth_provider makeBasicAuth(boolean reversed) {
    Auth_provider authProvider = new Auth_provider();
    authProvider.basic = new Basic();
    authProvider.basic.username = reversed ? SERVICE_USERNAME : DEVICE_USERNAME;
    authProvider.basic.password = reversed ? SERVICE_PASSWORD : DEVICE_PASSWORD;
    return authProvider;
  }

  public void augmentConfig(EndpointConfiguration endpoint, boolean reversed) {
    Matcher matcher = URL_PATTERN.matcher(BROKER_URL.trim());
    checkState(matcher.matches(), "Endpoint URL does not match format " + URL_FORMAT);
    endpoint.protocol = Protocol.MQTT;
    endpoint.transport = EndpointConfiguration.Transport.fromValue(matcher.group(1));
    endpoint.hostname = matcher.group(2);
    endpoint.port = Integer.parseInt(matcher.group(3));
    endpoint.auth_provider = makeBasicAuth(reversed);
    endpoint.client_id = reversed ? SERVICE_CLIENT_ID : DEVICE_CLIENT_ID;
  }

  private Map<String, String> parseEnvelopeTopic(String topic) {
    return SimpleMqttPipe.parseEnvelopeTopic(topic);
  }

  @Test
  public void staticEnvelopeTopic() {
    assertThrows(Exception.class, () -> parseEnvelopeTopic("garbage"));
    assertThrows(Exception.class, () -> parseEnvelopeTopic("/f/reg/d/dev/events"));
    assertThrows(Exception.class, () -> parseEnvelopeTopic("/r/registry"));
    assertThrows(Exception.class, () -> parseEnvelopeTopic("/r/registry/d"));
    assertThrows(Exception.class, () -> parseEnvelopeTopic("/r/registry/x/dev/events"));
    assertThrows(Exception.class, () -> parseEnvelopeTopic("/r/registry/d/dev/t/f/g/x"));
    assertThrows(Exception.class, () -> parseEnvelopeTopic("/r/registry/d/dev/c/d/t/f/g/x"));
    Map<String, String> basicEvent = parseEnvelopeTopic("/r/registry/d/dev/events");
    assertEquals("registry", basicEvent.get(REGISTRY_ID_PROPERTY_KEY));
    assertEquals("dev", basicEvent.get(DEVICE_ID_KEY));
    assertEquals("events", basicEvent.get(SUBTYPE_PROPERTY_KEY));
    assertNull(basicEvent.get(SUBFOLDER_PROPERTY_KEY));
    assertEquals(3, basicEvent.keySet().size());
    Map<String, String> pointsetEvent = parseEnvelopeTopic("/r/reg/d/dev/events/pointset");
    assertEquals("pointset", pointsetEvent.get(SUBFOLDER_PROPERTY_KEY));
    assertNull(pointsetEvent.get(GATEWAY_ID_KEY));
    assertEquals(4, pointsetEvent.keySet().size());
    Map<String, String> gatewayState = parseEnvelopeTopic("/r/reg/d/dev/state/pointset/gateway");
    assertEquals("reg", gatewayState.get(REGISTRY_ID_PROPERTY_KEY));
    assertEquals("dev", gatewayState.get(DEVICE_ID_KEY));
    assertEquals("state", gatewayState.get(SUBTYPE_PROPERTY_KEY));
    assertEquals("pointset", gatewayState.get(SUBFOLDER_PROPERTY_KEY));
    assertEquals("gateway", gatewayState.get(GATEWAY_ID_KEY));
    assertEquals(5, gatewayState.keySet().size());
    Map<String, String> channelState = parseEnvelopeTopic("/r/reg/d/dev/c/control/state/p/g");
    assertEquals("reg", channelState.get(REGISTRY_ID_PROPERTY_KEY));
    assertEquals("dev", channelState.get(DEVICE_ID_KEY));
    assertEquals("state", channelState.get(SUBTYPE_PROPERTY_KEY));
    assertEquals("invalid", channelState.get(SUBFOLDER_PROPERTY_KEY));
    assertEquals("g", channelState.get(GATEWAY_ID_KEY));
    assertEquals("control", channelState.get(SOURCE_KEY));
    assertEquals(6, channelState.keySet().size());
  }
}