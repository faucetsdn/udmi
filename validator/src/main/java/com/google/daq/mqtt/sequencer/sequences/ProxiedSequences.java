package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static udmi.schema.Category.GATEWAY_PROXY_TARGET;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.PointsetBase;
import com.google.daq.mqtt.sequencer.Summary;
import java.util.HashMap;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.Entry;
import udmi.schema.FamilyLocalnetModel;
import udmi.schema.FeatureDiscovery.FeatureStage;
import udmi.schema.GatewayConfig;
import udmi.schema.Level;
import udmi.schema.PointPointsetConfig;
import udmi.schema.TargetTestingModel;

;

/**
 * Validate pointset related functionality for proxied devices.
 */
public class ProxiedSequences extends PointsetBase {

  @Override
  public void setUp() {
    ifTrueSkipTest(catchToTrue(() -> deviceMetadata.gateway.gateway_id == null),
        "Not a proxied device");
    super.setUp();
  }

  @Feature(stage = FeatureStage.PREVIEW, bucket = Bucket.GATEWAY)
  @Summary("Error handling for badly formed gateway target family")
  @Test(timeout = TWO_MINUTES_MS)
  public void bad_target_family() {
    cleanStatusCheck(null);
    GatewayConfig gatewayConfig = deviceConfig.gateway;
    final FamilyLocalnetModel savedTarget = deepCopy(gatewayConfig.target);
    ifNullThen(gatewayConfig.target, () -> gatewayConfig.target = new FamilyLocalnetModel());
    gatewayConfig.target.family = getRandomCode("family");
    untilTrue("gateway status has target family error", this::hasGatewayStatusError);
    gatewayConfig.target = savedTarget;
    untilFalse("gateway status has no error", this::hasGatewayStatusDirty);
  }

  @Feature(stage = FeatureStage.PREVIEW, bucket = Bucket.GATEWAY)
  @Summary("Error handling for badly formed gateway target address")
  @Test(timeout = TWO_MINUTES_MS)
  public void bad_target_address() {
    cleanStatusCheck(null);
    GatewayConfig gatewayConfig = deviceConfig.gateway;
    final FamilyLocalnetModel savedTarget = deepCopy(gatewayConfig.target);
    ifNullThen(gatewayConfig.target, () -> gatewayConfig.target = new FamilyLocalnetModel());
    gatewayConfig.target.addr = getRandomCode("addr");
    untilTrue("gateway status has target addr error", this::hasGatewayStatusError);
    gatewayConfig.target = savedTarget;
    untilFalse("gateway status has no error", this::hasGatewayStatusDirty);
  }

  @Feature(stage = FeatureStage.PREVIEW, bucket = Bucket.GATEWAY)
  @Summary("Error handling for badly formed gateway point ref")
  @Test(timeout = TWO_MINUTES_MS)
  public void bad_point_ref() {
    String targetPoint = getTarget(TWEAKED_REF).target_point;
    cleanStatusCheck(targetPoint);
    HashMap<String, PointPointsetConfig> points = deviceConfig.pointset.points;
    PointPointsetConfig pointPointsetConfig = points.get(targetPoint);
    PointPointsetConfig savedTarget = deepCopy(pointPointsetConfig);
    pointPointsetConfig.ref = getRandomCode("ref");
    untilTrue("point status has target error", this::hasPointStatusError);
    points.put(targetPoint, savedTarget);
    untilFalse("no more pointset error", this::hasPointStatusDirty);
  }

  private void cleanStatusCheck(String targetPoint) {
    checkNotThat("gateway state with significant status", this::hasGatewayStatusDirty);
    if (targetPoint != null) {
      checkNotThat("pointset state with significant status", this::hasPointStatusDirty);
    }
  }

  private boolean hasGatewayStatusDirty() {
    Integer level = catchToElse(() -> deviceState.gateway.status.level, Level.INFO.value());
    return level > Level.INFO.value();
  }

  private boolean hasGatewayStatusError() {
    return hasGatewayStatusDirty()
        && GATEWAY_PROXY_TARGET.equals(deviceState.gateway.status.category)
        && Level.ERROR == Level.fromValue(deviceState.gateway.status.level);
  }

  private boolean hasPointStatusDirty() {
    String targetPoint = getTarget(TWEAKED_REF).target_point;
    Entry status = deviceState.pointset.points.get(targetPoint).status;
    return catchToElse(() -> status.level, Level.INFO.value()) > Level.INFO.value();
  }

  private boolean hasPointStatusError() {
    String targetPoint = getTarget(TWEAKED_REF).target_point;
    Entry status = deviceState.pointset.points.get(targetPoint).status;
    return hasGatewayStatusDirty()
        && GATEWAY_PROXY_TARGET.equals(status.category)
        && Level.ERROR == Level.fromValue(status.level);
  }

  private String getRandomCode(String prefix) {
    return String.format("%s-%04x", prefix, (int) Math.floor(Math.random() * 0x10000));
  }
}
