package com.google.daq.mqtt.sequencer.sequences;

import static com.google.udmi.util.JsonUtil.asMap;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.sequencer.SequenceBase;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import udmi.schema.PointPointsetEvents;
import udmi.schema.PointsetEvents;
import udmi.schema.TargetTestingModel;

/**
 * Unit tests for {@link WritebackSequences} telemetry ordering and stale-event isolation.
 */
public class WritebackSequencesTest {

  private static final String TARGET_POINT = "filter_differential_pressure_setpoint";
  private static final int TARGET_VALUE = 98;
  private static final String POINTSET_EVENTS_KEY = "events_pointset";

  /**
   * Configure sequencer execution config before each test.
   */
  @Before
  public void setUp() {
    SequenceBase.exeConfig = TestCommon.testConfiguration();
    SequenceBase.exeConfig.device_id = TestCommon.DEVICE_ID;
  }

  @Test
  public void presentValueRemainsSatisfiedAcrossSubsequentPollsAfterTelemetryPop() {
    WritebackSequences sequences = new WritebackSequences();
    TargetTestingModel targetModel = makeTargetModel();

    enqueuePointsetEvent(sequences, TARGET_VALUE);

    // Poll 1: PointsetEvents arrives before throttled DeviceState update.
    assertNull("First poll after target PointsetEvents should match",
        sequences.presentValueIs(targetModel));

    // Poll 2: Throttled DeviceState arrives on next iteration with no new PointsetEvents queued.
    assertNull("Subsequent poll with no new PointsetEvents must still match lastPresentValue",
        sequences.presentValueIs(targetModel));
  }

  @Test
  public void presentValueUsesLatestEventInBatchRatherThanStaleMatch() {
    WritebackSequences sequences = new WritebackSequences();
    TargetTestingModel targetModel = makeTargetModel();

    enqueuePointsetEvent(sequences, TARGET_VALUE);
    enqueuePointsetEvent(sequences, 42);

    String mismatch = sequences.presentValueIs(targetModel);
    assertNotNull("Latest event in batch superseded target value and must not match", mismatch);
    assertTrue("Mismatch should report latest present value 42, got: " + mismatch,
        mismatch.contains("present value 42"));
  }

  @Test
  public void resetPresentValueClearsStalePreWritebackTelemetry() {
    WritebackSequences sequences = new WritebackSequences();
    TargetTestingModel targetModel = makeTargetModel();

    enqueuePointsetEvent(sequences, TARGET_VALUE);
    assertNull(sequences.presentValueIs(targetModel));

    enqueuePointsetEvent(sequences, TARGET_VALUE);
    sequences.resetPresentValue();

    String mismatch = sequences.presentValueIs(targetModel);
    assertNotNull("Pre-writeback telemetry must not satisfy post-writeback check", mismatch);
    assertTrue("Expected null lastPresentValue after reset, got: " + mismatch,
        mismatch.contains("present value null"));
  }

  private static TargetTestingModel makeTargetModel() {
    TargetTestingModel model = new TargetTestingModel();
    model.target_point = TARGET_POINT;
    model.target_value = TARGET_VALUE;
    return model;
  }

  private static void enqueuePointsetEvent(WritebackSequences sequences, Object presentValue) {
    PointsetEvents event = new PointsetEvents();
    event.points = new HashMap<>();
    PointPointsetEvents pointEvent = new PointPointsetEvents();
    pointEvent.present_value = presentValue;
    event.points.put(TARGET_POINT, pointEvent);
    sequences.getCapturedMessagesList(TestCommon.DEVICE_ID, POINTSET_EVENTS_KEY)
        .add(asMap(event));
  }
}
