package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.ONE_MINUTE_MS;
import static com.google.daq.mqtt.util.TimePeriodConstants.THREE_MINUTES_MS;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.ifTrueThen;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static udmi.schema.Bucket.POINTSET;
import static udmi.schema.Category.POINTSET_POINT_INVALID;
import static udmi.schema.Category.POINTSET_POINT_INVALID_VALUE;
import static udmi.schema.FeatureEnumeration.FeatureStage.ALPHA;
import static udmi.schema.FeatureEnumeration.FeatureStage.BETA;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.PointsetBase;
import com.google.daq.mqtt.sequencer.Summary;
import com.google.daq.mqtt.util.SamplingRange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.Test;
import udmi.schema.Level;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetEvent;
import udmi.schema.PointPointsetState;
import udmi.schema.PointsetEvent;

/**
 * Validate pointset related functionality.
 */
public class PointsetSequences extends PointsetBase {

  public static final String EXTRANEOUS_POINT = "extraneous_point";
  private static final int DEFAULT_SAMPLE_RATE_SEC = 10;

  private boolean isErrorState(PointPointsetState pointState) {
    return ofNullable(catchToNull(() -> pointState.status.level)).orElse(Level.INFO.value())
        >= Level.ERROR.value();
  }

  private void untilPointsetSanity() {
    untilTrue("pointset state reports same points as defined in config", () ->
        deviceState.pointset.points.keySet().equals(deviceConfig.pointset.points.keySet()));
    untilTrue("pointset event contains correct points with present_value",
        () -> {
          List<PointsetEvent> pointsetEvents = popReceivedEvents(PointsetEvent.class);
          return pointsetEvents.get(pointsetEvents.size() - 1).points.entrySet().stream()
              .filter(this::validPointEntry).map(Entry::getKey).collect(Collectors.toSet())
              .equals(deviceConfig.pointset.points.keySet());
        }
    );
  }

  private boolean validPointEntry(Entry<String, PointPointsetEvent> point) {
    PointPointsetState pointState = deviceState.pointset.points.get(point.getKey());
    return point.getValue().present_value != null || isErrorState(pointState);
  }

  @Test(timeout = ONE_MINUTE_MS)
  @Summary("pointset configuration contains extraneous point")
  @Feature(stage = BETA, bucket = POINTSET)
  public void pointset_request_extraneous() {
    untilPointsetSanity();

    deviceConfig.pointset.points.put(EXTRANEOUS_POINT, new PointPointsetConfig());

    try {
      untilTrue("pointset status contains extraneous point error",
          () -> ifNotNullGet(deviceState.pointset.points.get(EXTRANEOUS_POINT),
              state -> state.status.category.equals(POINTSET_POINT_INVALID)
                  && state.status.level.equals(POINTSET_POINT_INVALID_VALUE)));
      untilPointsetSanity();
    } finally {
      deviceConfig.pointset.points.remove(EXTRANEOUS_POINT);
    }

    untilTrue("pointset status removes extraneous point error",
        () -> !deviceState.pointset.points.containsKey(EXTRANEOUS_POINT));

    untilPointsetSanity();
  }

  @Test(timeout = ONE_MINUTE_MS)
  @Summary("pointset state does not report unconfigured point")
  @Feature(stage = BETA, bucket = POINTSET)
  public void pointset_remove_point() {
    untilPointsetSanity();

    List<String> candidatePoints = new ArrayList<>(deviceState.pointset.points.keySet());
    ifTrueThen(candidatePoints.isEmpty(), () -> skipTest("No points to remove"));
    String name = candidatePoints.get((int) Math.floor(Math.random() * candidatePoints.size()));

    debug("Removing randomly selected test point " + name);
    PointPointsetConfig removed = requireNonNull(deviceConfig.pointset.points.remove(name));

    try {
      untilFalse("pointset status does not contain removed point",
          () -> deviceState.pointset.points.containsKey(name));
      untilPointsetSanity();
    } finally {
      deviceConfig.pointset.points.put(name, removed);
    }

    untilFalse("pointset status contains removed point",
        () -> deviceState.pointset.points.containsKey(name));

    untilPointsetSanity();
  }


  /**
   * Simple check that device publishes pointset events.
   */
  @Test(timeout = THREE_MINUTES_MS)
  @Feature(stage = BETA, bucket = POINTSET)
  @Summary("device publishes pointset events")
  public void pointset_publish() {
    ifNullSkipTest(deviceConfig.pointset, "no pointset found in config");

    untilTrue("receive a pointset event",
        () -> (countReceivedEvents(PointsetEvent.class) > 1
    ));
  }


  /**
   * Tests sample_rate_min by measuring the initial interval between the last two messages received,
   * then setting the config.pointset.sample_rate_min to match half the initial interval and
   * measuring the final interval between several messages and ensuring it is less than the new
   * interval (with a tolerance of 1.5s).
   *
   * <p>Pass if: final interval < new sample_rate_min + tolerance
   * Fail if: final interval > new sample_rate_min + tolerance Skip if: initial interval < 5s (too
   * fast for automated test)
   */
  @Test(timeout = THREE_MINUTES_MS)
  @Feature(stage = BETA, bucket = POINTSET)
  @Summary("device publishes pointset events at a rate of no more than config sample_rate_sec")
  public void pointset_sample_rate() {
    ifNullSkipTest(deviceConfig.pointset, "no pointset found in config");

    // Clear received events because this could contain messages from a previous sample rate test
    popReceivedEvents(PointsetEvent.class);

    Instant endTime = Instant.now().plusSeconds(DEFAULT_SAMPLE_RATE_SEC * 3);
    // To pick the test sample rate, either measure the devices
    // given sampling rate from its last 2 messages and half it or use
    // a value if long
    untilTrue("measure initial sample rate",
        () -> (countReceivedEvents(PointsetEvent.class) > 1
            || Instant.now().isAfter(endTime))
    );

    final int testSampleRate;
    if (countReceivedEvents(PointsetEvent.class) < 2) {
      // 2 messages not seen, assume interval is longer than wait period, pick a small number
      testSampleRate = DEFAULT_SAMPLE_RATE_SEC;
    } else {
      List<PointsetEvent> receivedEvents = popReceivedEvents(PointsetEvent.class);
      List<Long> telemetryDelta = intervalFromEvents(receivedEvents);
      int nominalInterval = telemetryDelta.get(0).intValue();
      info(format("initial sample rate is %d seconds", nominalInterval));

      ifTrueThen(nominalInterval < 5,
          () -> skipTest("measured sample rate is too low for automated test"));

      // Use an interval smaller than the devices last interval
      testSampleRate = Math.floorDiv(nominalInterval, 2);
    }

    info(format("setting sample rate to %d seconds", testSampleRate));
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
    return format("time period between successive pointset events is %s",
        samplingRange);
  }

  /**
   * Tests both sample_rate_sec and sample_limit_sec by defining two non-intersecting narrow ranges
   * of both parameters, and ensuring telemetry is within this range.
   */
  @Test(timeout = THREE_MINUTES_MS)
  @Summary("test sample rate and sample limit sec")
  @Feature(stage = BETA, bucket = POINTSET)
  public void pointset_publish_interval() {
    ifNullSkipTest(deviceConfig.pointset, "no pointset found in config");

    // Test two narrow non-intersecting windows
    SamplingRange firstRange = new SamplingRange(5, 8, 1.5);
    testPointsetWithSamplingRange(firstRange, 4, 1);

    SamplingRange secondRange = new SamplingRange(15, 18, 1.5);
    testPointsetWithSamplingRange(secondRange, 4, 1);
  }

  /**
   * Given a list of events, sorts these in timestamp order and returns a list of the the intervals
   * between each pair of successive messages based on the in-payload timestamp.
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
   * Updating the sample_limit_sec and sample_rate_sec according to provided SamplingRange and
   * checks if the interval between subsequent pointset events are within this range.
   *
   * @param sampleRange       sample range to test with
   * @param messagesToSample  number of messages to sample (must be greater than 2)
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
    untilTrue(format("receive at least %d pointset events", messagesToSample),
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


