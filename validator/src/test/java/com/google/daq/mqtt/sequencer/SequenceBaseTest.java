package com.google.daq.mqtt.sequencer;

import static org.junit.Assert.assertEquals;

import com.google.bos.iot.core.proxy.MockPublisher;
import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.udmi.util.JsonUtil;
import org.junit.Test;
import org.junit.runner.Description;
import udmi.schema.State;

/**
 * Unit tests for the SequenceBaseTest class.
 */
public class SequenceBaseTest {

  private static final String TEST_TOPIC = "mock/topic";

  static {
    SequenceRunner.executionConfiguration = TestCommon.testConfiguration();
    SequenceRunner.executionConfiguration.device_id = TestCommon.DEVICE_ID;
  }

  @Test
  public void messageInterrupted() {
    final MockPublisher mockClient = SequenceBase.getMockClient();
    final SequenceBase base1 = new SequenceBase();
    final SequenceBase base2 = new SequenceBase();

    State stateMessage = new State();
    stateMessage.version = "hello";
    mockClient.publish(TestCommon.DEVICE_ID, TEST_TOPIC, JsonUtil.stringify(stateMessage));
    stateMessage.version = "world";
    mockClient.publish(TestCommon.DEVICE_ID, TEST_TOPIC, JsonUtil.stringify(stateMessage));

    base1.testWatcher.starting(makeTestDescription("test_one"));
    MessageBundle bundle1 = base1.nextMessageBundle();
    assertEquals("first message contents", "hello", bundle1.message.get("version"));

    base2.testWatcher.starting(makeTestDescription("test_two"));
    try {
      base1.nextMessageBundle();
      assert false;
    } catch (RuntimeException e) {
      // This is expected, but then also preserve the message for the next call.
    }
    MessageBundle bundle2 = base2.nextMessageBundle();
    assertEquals("second message contents", "world", bundle2.message.get("version"));
  }

  private Description makeTestDescription(String testName) {
    return Description.createTestDescription(SequenceBase.class, testName);
  }
}
