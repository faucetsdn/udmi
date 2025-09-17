package com.google.daq.mqtt.sequencer;

import java.util.HashMap;
import java.util.Optional;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import udmi.schema.PointPointsetConfig;
import udmi.schema.PointPointsetModel;
import udmi.schema.PointsetConfig;
import udmi.schema.TargetTestingModel;

/**
 * Class used for testing sequences with points.
 */
public abstract class PointsetBase extends SequenceBase {

  public static final String INVALID_STATE = "invalid";
  public static final String FAILURE_STATE = "failure";
  public static final String APPLIED_STATE = "applied";
  public static final String TWEAKED_REF = "tweaked_ref";
  public static final String UPDATING_STATE = "updating";

  @Override
  public void setUp() {
    ifTrueSkipTest(catchToTrue(() -> deviceMetadata.pointset == null), "Does not support pointset");
    super.setUp();
  }

  /**
   * Make the required set of points in the config block.
   */
  @Before
  public void makePoints() {
    deviceConfig.pointset = Optional.ofNullable(deviceConfig.pointset).orElse(new PointsetConfig());
    deviceConfig.pointset.points = Optional.ofNullable(deviceConfig.pointset.points)
        .orElse(new HashMap<>());
    try {
      ensurePointConfig(INVALID_STATE);
      ensurePointConfig(FAILURE_STATE);
      ensurePointConfig(APPLIED_STATE);
    } catch (AssumptionViolatedException skipTest) {
      info("Not setting config points: " + skipTest.getMessage());
    }
  }

  private void ensurePointConfig(String target) {
    String targetPoint = getTarget(target).target_point;
    deviceConfig.pointset.points.computeIfAbsent(targetPoint, this::makePointsetConfig);
  }

  private PointPointsetConfig makePointsetConfig(String pointName) {
    return new PointPointsetConfig();
  }

  protected TargetTestingModel getTarget(String target) {
    TargetTestingModel testingMetadata = ifNullSkipTest(
        catchToNull(() -> deviceMetadata.testing.targets.get(target)),
        "No testing target defined for '" + target + "'");
    PointPointsetModel pointPointsetModel = catchToNull(
        () -> deviceMetadata.pointset.points.get(testingMetadata.target_point));
    ifNullSkipTest(pointPointsetModel,
        "No pointset model for target point " + testingMetadata.target_point);
    return testingMetadata;
  }
}
