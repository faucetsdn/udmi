package com.google.daq.mqtt.sequencer;

import static com.google.udmi.util.JsonUtil.stringify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.util.TimePeriodConstants;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import java.lang.annotation.Annotation;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;

/**
 * Unit tests for the SequenceBaseTest class.
 */
public class SequenceBaseTest {

  /**
   * Reset the state of the underlying infrastructure for each test.
   */
  @Before
  public void resetForTest() {
    SequenceBase.resetState();
    SequenceBase.exeConfig = TestCommon.testConfiguration();
    SequenceBase.exeConfig.device_id = TestCommon.DEVICE_ID;
  }

  static class TestCapability implements Capability { }

  @Test
  public void capabilityConversion() {
    String capabilityName = SequenceBase.capabilityName(TestCapability.class);
    assertEquals("class name conversion", "test_capability", capabilityName);
  }

  @Test
  public void messageInterrupted() {
    final SequenceBase baseOne = new SequenceBase();
    Description testOne = makeTestDescription("test_one");
    baseOne.testWatcher.starting(testOne);

    MessageBundle bundleOne = waitForNextMessage(baseOne);
    System.err.println(stringify(bundleOne));
    Map<?, ?> featuresOne = (Map<?, ?>) bundleOne.message.get("features");
    assertEquals("first message contents", 0, featuresOne.size());

    baseOne.testWatcher.finished(testOne);

    final SequenceBase baseTwo = new SequenceBase();
    Description testTwo = makeTestDescription("test_two");
    baseTwo.testWatcher.starting(testTwo);

    try {
      waitForNextMessage(baseOne);
      fail("shouldn't be a next message bundle to get!");
    } catch (RuntimeException e) {
      // This is expected, but then also preserve the message for the next call.
    }

    MessageBundle bundleTwo = waitForNextMessage(baseTwo);
    Map<?, ?> featuresTwo = (Map<?, ?>) bundleTwo.message.get("features");
    System.err.println(stringify(bundleTwo));
    assertEquals("second message contents", 1, featuresTwo.size());
    baseTwo.testWatcher.finished(testTwo);
  }

  private static MessageBundle waitForNextMessage(SequenceBase baseOne) {
    MessageBundle messageBundle;
    do {
      messageBundle = baseOne.nextMessageBundle();
    } while (messageBundle == null);
    return messageBundle;
  }

  private Description makeTestDescription(String testName) {
    Test testAnnotation = new Test() {
      @Override
      public Class<? extends Throwable> expected() {
        return null;
      }

      @Override
      public long timeout() {
        return TimePeriodConstants.TWO_MINUTES_MS;
      }

      @Override
      public Class<? extends Annotation> annotationType() {
        return Test.class;
      }
    };
    return Description.createTestDescription(SequenceBase.class, testName, testAnnotation);
  }

}
