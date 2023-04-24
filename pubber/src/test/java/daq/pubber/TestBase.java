package daq.pubber;

import static daq.pubber.ListPublisher.getMessageString;

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
  protected static final String TEST_PREFIX = "test_prefix";
  protected static final String TEST_DEVICE = "test_device";
  protected static final String EXPECTED_TOPIC = String.format(
      "%s/%s/%s", TEST_PREFIX, TEST_DEVICE, TEST_TOPIC);

  protected static String EXPECTED_MESSAGE_STRING = getMessageString(
      TEST_DEVICE, EXPECTED_TOPIC, TEST_MESSAGE);

  protected PubberConfiguration getTestConfiguration() {
    PubberConfiguration configuration = new PubberConfiguration();
    configuration.projectId = MqttDevice.TEST_PROJECT;
    configuration.deviceId = TEST_DEVICE;
    configuration.endpoint = new EndpointConfiguration();
    configuration.endpoint.msg_prefix = TEST_PREFIX;
    configuration.options = new PubberOptions();
    return configuration;
  }
}
