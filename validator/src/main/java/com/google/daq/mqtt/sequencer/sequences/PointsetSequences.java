package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.sequencer.Feature.Stage.ALPHA;
import static com.google.daq.mqtt.util.TimePeriodConstants.THREE_MINUTES_MS;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.CleanDateFormat.dateEquals;
import static com.google.udmi.util.JsonUtil.getTimestamp;
import static com.google.udmi.util.JsonUtil.safeSleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.SkipTest;
import com.google.daq.mqtt.util.SamplingRange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import udmi.schema.PointsetEvent;

/**
 * Validate pointset related functionality.
 */
public class PointsetSequences extends SequenceBase {

  /**
   * Tests sample_rate_min by measuring the initial interval between the last two messages received,
   * then setting the config.pointset.sample_rate_min to match half the initial interval
   * and measuring the final interval between several messages and ensuring it is less than the new
   * interval (with a tolerance of 1.5s).
   *
   * <p>Pass if: final interval < new sample_rate_min + tolerance
   * Fail if: final interval > new sample_rate_min + tolerance
   * Skip if: initial interval < 5s (too fast for automated test)
   */
  @Test(timeout = THREE_MINUTES_MS)
  @Feature(stage = ALPHA)
  @Description("device publishes pointset events at a rate of no more than config sample_rate_sec")
  public void pointset_sample_rate() {
    Integer defaultSampleRate = 10;

    if (deviceConfig.pointset == null) {
      throw new SkipTest("device pointset not in config");
    }

    // Clear received events because this could contain messages from a previous sample rate test
    popReceivedEvents(PointsetEvent.class);

    Instant endTime = Instant.now().plusSeconds(defaultSampleRate * 3);
    // To pick the test sample rate, either measure the devices
    // given sampling rate from its last 2 messages and half it or use
    // a value if long
    untilTrue("measure initial sample rate",
        () -> (countReceivedEvents(PointsetEvent.class) > 1
          || Instant.now().isAfter(endTime))
    );

    Integer testSampleRate;
    if (countReceivedEvents(PointsetEvent.class) < 2) {
      // 2 messages not seen, assume interval is longer than wait period, pick a small number
      testSampleRate = defaultSampleRate;
    } else {
      List<PointsetEvent> receivedEvents = popReceivedEvents(PointsetEvent.class);
      List<Long> telemetryDelta = intervalFromEvents(receivedEvents);
      Integer nominalInterval = telemetryDelta.get(0).intValue();
      info(String.format("initial sample rate is %d seconds", nominalInterval));

      if (nominalInterval < 5) {
        throw new SkipTest("measured sample rate is too low for automated test");
      }

      // Use an interval smaller than the devices last interval
      testSampleRate = Math.floorDiv(nominalInterval, 2);
    }

    info(String.format("setting sample rate to %d seconds", testSampleRate));
    SamplingRange testSampleRange = new SamplingRange(1, testSampleRate, 1.5);

    testPointsetWithSamplingRange(testSampleRange, 5, 2);
  }

  /**
   * Generates message for checking the time periods are within the sampling range.
   *
   * @param samplingRange sampling range to produce message for
   * @return message
   */
  private String samplingMessagesCheckMessage(SamplingRange samplingRange) {
    return String.format("time period between successive pointset events is %s",
        samplingRange);
  }

  /**
   * Tests both sample_rate_sec and sample_limit_sec by defining two non-intersecting narrow
   * ranges of both parameters, and ensuring telemetry is within this range.
   */
  @Test(timeout = THREE_MINUTES_MS)
  @Description("test sample rate and sample limit sec")
  @Feature(stage = ALPHA)
  public void pointset_publish_interval() {
    
    if (deviceConfig.pointset == null) {
      throw new SkipTest("device pointset not in config");
    }

    // Test two narrow non-intersecting windows
    SamplingRange firstRange = new SamplingRange(5, 8, 1.5);
    testPointsetWithSamplingRange(firstRange, 4, 1);

    SamplingRange secondRange = new SamplingRange(15, 18, 1.5);
    testPointsetWithSamplingRange(secondRange, 4, 1);
  }

  /**
   * Given a list of events, sorts these in timestamp order and returns a list of the
   * the intervals between each pair of successive messages based on the in-payload timestamp.
   *
   * @param receivedEvents list of PointsetEvents
   * @return list of the intervals between successive messages
   */
  private List<Long> intervalFromEvents(List<PointsetEvent> receivedEvents) {
    ArrayList<Long> intervals = new ArrayList<>();

    if (receivedEvents.size() < 2) {
      throw new RuntimeException("cannot calculate interval with less than 2 messages");
    }

    List<Date> events = receivedEvents.stream().map(event -> event.timestamp)
        .collect(Collectors.toList());
    Collections.sort(events);
    for (int i = 1; i < events.size(); i++) {
      intervals.add(((events.get(i).getTime() - events.get(i - 1).getTime()) / 1000));
    }
    return intervals;
  }

  /**
   * Updating the sample_limit_sec and sample_rate_sec according to provided SamplingRange
   * and checks if the interval between subsequent pointset events are within this range.
   *
   * @param sampleRange sample range to test with
   * @param messagesToSample number of messages to sample (must be greater than 2)
   * @param intervalsToIgnore number of intervals to ignore at start (to allow system to settle)
   */
  private void testPointsetWithSamplingRange(SamplingRange sampleRange, Integer messagesToSample,
      Integer intervalsToIgnore) {
    if (messagesToSample < 2) {
      throw new RuntimeException("cannot test with less than two messages");
    } else if (intervalsToIgnore > messagesToSample - 1) {
      throw new RuntimeException("cannot ignore more intervals than intervals measured");
    }

    deviceConfig.pointset.sample_limit_sec = sampleRange.sampleLimit;
    deviceConfig.pointset.sample_rate_sec = sampleRange.sampleRate;

    popReceivedEvents(PointsetEvent.class);
    untilTrue(String.format("receive at least %d pointset events", messagesToSample),
        () -> (countReceivedEvents(PointsetEvent.class) > messagesToSample)
    );

    List<PointsetEvent> receivedEvents = popReceivedEvents(PointsetEvent.class);
    List<Long> intervals = intervalFromEvents(receivedEvents);

    if (intervalsToIgnore > 0) {
      intervals.subList(0, intervalsToIgnore).clear();
    }
    
    checkThat(samplingMessagesCheckMessage(sampleRange),
        () -> sampleRange.doesIntersect(intervals)
    );

  }

}


