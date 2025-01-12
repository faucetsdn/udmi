package udmi.lib;

import static udmi.lib.base.ListPublisher.getMessageString;

import udmi.lib.base.MqttDevice;
import udmi.schema.DiscoveryCommand;
import udmi.schema.EndpointConfiguration;
import udmi.schema.PubberConfiguration;
import udmi.schema.PubberOptions;

/**
 * Test base for common constants, methods, etc...
 */
public class TestBase {

  protected static final String TEST_TOPIC = "test_topic";
  protected static final Object TEST_MESSAGE = new DiscoveryCommand();
  protected static final String TEST_DEVICE = "AHU-1";
  protected static final String TEST_PREFIX = "test_prefix/" + TEST_DEVICE;
  protected static final String EXPECTED_TOPIC = String.format("%s/%s", TEST_PREFIX, TEST_TOPIC);

  protected static String EXPECTED_MESSAGE_STRING = getMessageString(
      TEST_DEVICE, EXPECTED_TOPIC, TEST_MESSAGE);

  protected PubberConfiguration getTestConfiguration() {
    PubberConfiguration configuration = new PubberConfiguration();
    configuration.iotProject = MqttDevice.TEST_PROJECT;
    configuration.deviceId = TEST_DEVICE;
    configuration.endpoint = new EndpointConfiguration();
    configuration.endpoint.topic_prefix = TEST_PREFIX;
    configuration.options = new PubberOptions();
    return configuration;
  }
}
