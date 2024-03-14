package com.google.daq.mqtt.sequencer;

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

  private static final String TEST_TOPIC = "mock/topic";

  /**
   * Reset the state of the underlying infrastructure for each test.
   */
  @Before
  public void resetForTest() {
    SequenceBase.resetState();
    SequenceRunner.exeConfig = TestCommon.testConfiguration();
    SequenceRunner.exeConfig.device_id = TestCommon.DEVICE_ID;
  }

  @Test
  public void messageInterrupted() {
    final SequenceBase base1 = new SequenceBase();
    base1.testWatcher.starting(makeTestDescription("test_one"));

    MessageBundle bundle1 = base1.nextMessageBundle();
    Map<?, ?> features1 = (Map<?, ?>) bundle1.message.get("features");
    assertEquals("first message contents", 0, features1.size());

    final SequenceBase base2 = new SequenceBase();
    base2.testWatcher.starting(makeTestDescription("test_two"));

    try {
      base1.nextMessageBundle();
      fail("shouldn't be a next message bundle to get!");
    } catch (RuntimeException e) {
      // This is expected, but then also preserve the message for the next call.
    }

    MessageBundle bundle2 = base2.nextMessageBundle();
    Map<?, ?> features2 = (Map<?, ?>) bundle2.message.get("features");
    assertEquals("second message contents", 1, features2.size());
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
