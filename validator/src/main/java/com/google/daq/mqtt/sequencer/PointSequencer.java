package com.google.daq.mqtt.sequencer;

import com.google.daq.mqtt.sequencer.sequences.WritebackSequences;
import java.util.HashMap;
import java.util.Optional;
import org.junit.Before;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointsetConfig;
import udmi.schema.TargetTestingModel;

/**
 * Class used for testing sequences with points.
 */
public abstract class PointSequencer extends SequenceBase {

  /**
   * Make the required set of points in the config block.
   */
  @Before
  public void makePoints() {
    deviceConfig.pointset = Optional.ofNullable(deviceConfig.pointset).orElse(new PointsetConfig());
    deviceConfig.pointset.points = Optional.ofNullable(deviceConfig.pointset.points)
        .orElse(new HashMap<>());
    try {
      ensurePointConfig(WritebackSequences.INVALID_STATE);
      ensurePointConfig(WritebackSequences.FAILURE_STATE);
      ensurePointConfig(WritebackSequences.APPLIED_STATE);
    } catch (SkipTest skipTest) {
      info("Not setting config points: " + skipTest.getMessage());
    }
  }

  private void ensurePointConfig(String target) {
    String targetPoint = getTarget(target).target_point;
    if (!deviceConfig.pointset.points.containsKey(targetPoint)) {
      deviceConfig.pointset.points.put(targetPoint, new PointPointsetConfig());
    }
  }

  protected TargetTestingModel getTarget(String target) {
    if (deviceMetadata.testing == null
        || deviceMetadata.testing.targets == null
        || !deviceMetadata.testing.targets.containsKey(target)) {
      throw new SkipTest(String.format("Missing '%s' target specification", target));
    }
    TargetTestingModel testingMetadata = deviceMetadata.testing.targets.get(target);
    if (deviceMetadata.pointset == null || deviceMetadata.pointset.points == null) {
      info("No metadata pointset points defined, I hope you know what you're doing");
    } else if (!deviceMetadata.pointset.points.containsKey(testingMetadata.target_point)) {
      throw new RuntimeException(
          String.format("Testing target %s point '%s' not defined in pointset metadata",
              target, testingMetadata.target_point));
    }
    return testingMetadata;
  }

}
