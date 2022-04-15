package com.google.daq.mqtt.validator;

import com.google.daq.mqtt.validator.validations.WritebackValidator;
import java.util.HashMap;
import java.util.Optional;
import org.junit.Before;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointsetConfig;
import udmi.schema.TargetTestingModel;

/**
 * Class used for validating test about sequences with points.
 */
public abstract class PointValidator extends SequenceValidator {

  /**
   * Make the required set of points in the config block.
   */
  @Before
  public void makePoints() {
    deviceConfig.pointset = Optional.ofNullable(deviceConfig.pointset).orElse(new PointsetConfig());
    deviceConfig.pointset.points = Optional.ofNullable(deviceConfig.pointset.points)
        .orElse(new HashMap<>());
    try {
      ensurePointConfig(WritebackValidator.INVALID_STATE);
      ensurePointConfig(WritebackValidator.FAILURE_STATE);
      ensurePointConfig(WritebackValidator.APPLIED_STATE);
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
