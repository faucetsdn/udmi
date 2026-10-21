package com.google.daq.mqtt.sequencer;

import static com.google.udmi.util.JsonUtil.stringify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.util.TimePeriodConstants;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import java.lang.annotation.Annotation;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import udmi.schema.Config;
import udmi.schema.Envelope.SubFolder;
import udmi.schema.Metadata;
import udmi.schema.Operation;
import udmi.schema.PointsetConfig;
import udmi.schema.PointsetState;
import udmi.schema.State;
import udmi.schema.StateSystemOperation;
import udmi.schema.SystemConfig;
import udmi.schema.SystemState;
import udmi.schema.TestingModel;

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
  public void nostateUpdateConfigWaitsForTransactionAck() throws Exception {
    SequenceBase.exeConfig.registry_id = TestCommon.REGISTRY_ID;
    SequenceBase.deviceMetadata = new Metadata();
    SequenceBase.deviceMetadata.testing = new TestingModel();
    SequenceBase.deviceMetadata.testing.nostate = true;

    final SequenceBase base = new SequenceBase();
    Description testDesc = makeTestDescription("test_nostate_sync");
    base.testWatcher.starting(testDesc);
    waitForNextMessage(base);
    assertFalse("deviceSupportsState should be false in nostate mode",
        SequenceBase.deviceSupportsState());

    base.resetDeviceConfig(true);

    final CompletableFuture<Void> ackResponder = CompletableFuture.runAsync(() -> {
      try {
        while (base.configTransactions.isEmpty()) {
          Thread.sleep(10);
        }
        Thread.sleep(100);
        final String txnId = base.configTransactions.iterator().next();
        MessageBundle ackBundle = new MessageBundle();
        ackBundle.attributes = new HashMap<>();
        ackBundle.attributes.put("deviceRegistryId", SequenceBase.registryId);
        ackBundle.attributes.put("deviceId", TestCommon.DEVICE_ID);
        ackBundle.attributes.put("subType", "config");
        ackBundle.attributes.put("subFolder", "update");
        ackBundle.attributes.put("transactionId", txnId);
        ackBundle.message = new HashMap<>();
        ackBundle.message.put("timestamp", "2026-10-17T10:00:00Z");
        ackBundle.message.put("version", TestCommon.UDMI_VERSION);
        SequenceBase.messageQueue.add(ackBundle);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    base.updateConfig("nostate test");

    assertTrue("configTransactions should be cleared before updateConfig returns",
        base.configTransactions.isEmpty());
    assertNotNull("deviceConfig.timestamp should be updated from reflector ack",
        base.deviceConfig.timestamp);
    ackResponder.get(5, TimeUnit.SECONDS);
    base.testWatcher.finished(testDesc);
  }

  @Test
  public void resetConfigWaitsForSubblockParityOnSameSecondPartialUpdates() {
    SequenceBase base = new SequenceBase();
    final Date sameSecondTimestamp = new Date(1760699357000L);
    final Date startTime = new Date(1760699000000L);

    base.deviceConfig = new Config();
    base.deviceConfig.timestamp = sameSecondTimestamp;
    base.deviceConfig.system = new SystemConfig();
    base.deviceConfig.system.operation = new Operation();
    base.deviceConfig.system.operation.last_start = startTime;
    base.deviceConfig.pointset = new PointsetConfig();

    base.deviceState = new State();
    base.deviceState.timestamp = sameSecondTimestamp;
    base.deviceState.system = new SystemState();
    base.deviceState.system.last_config = sameSecondTimestamp;
    base.deviceState.system.operation = new StateSystemOperation();
    base.deviceState.system.operation.last_start = startTime;
    base.deviceState.pointset = null;

    base.doPartialUpdates = true;
    base.allowDeviceStateChange(SequenceBase.ALL_CHANGES);

    SequenceBase.sentConfig.put(SubFolder.SYSTEM, "{}");
    assertNull("configIsPending should not block on unsent subblocks during gated partial updates",
        base.configIsPending(false));

    // When state already has a subblock populated (e.g., before full reset or during soft reset)
    // and only SYSTEM has been sent so far, unsent POINTSET must be skipped rather than treated as
    // a null-config mismatch against non-null state.
    base.deviceState.pointset = new PointsetState();
    assertNull(
        "configIsPending should not report mismatch when unsent subblock is non-null in state",
        base.configIsPending(false));

    base.deviceConfig.pointset = null;
    assertNull(
        "configIsPending should not block on unsent null subblock during gated full reset",
        base.configIsPending(false));

    base.deviceConfig.pointset = new PointsetConfig();
    base.deviceState.pointset = null;
    SequenceBase.sentConfig.put(SubFolder.POINTSET, "{}");
    String pending = base.configIsPending(false);
    assertNotNull("configIsPending should wait for state subblocks to match sent config",
        pending);
    assertTrue("pending reason should report subblock mismatch, got: " + pending,
        pending.contains("config/state subblock mismatch: pointset"));

    base.deviceState.pointset = new PointsetState();
    assertNull("configIsPending should clear once state subblocks match config",
        base.configIsPending(false));

    base.disallowDeviceStateChange(SequenceBase.ALL_CHANGES);
    base.deviceState.pointset = null;
    assertNull("outside resetConfig, configIsPending should not block on subblock parity",
        base.configIsPending(false));
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
