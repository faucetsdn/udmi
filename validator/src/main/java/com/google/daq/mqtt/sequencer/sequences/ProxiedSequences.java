package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.sequencer.semantic.SemanticValue.describe;
import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static java.lang.String.format;
import static udmi.schema.Category.GATEWAY_PROXY_TARGET;
import static udmi.schema.Category.POINTSET_POINT_FAILURE;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.PointsetBase;
import com.google.daq.mqtt.sequencer.Summary;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.Entry;
import udmi.schema.FamilyLocalnetModel;
import udmi.schema.FeatureDiscovery.FeatureStage;
import udmi.schema.GatewayConfig;
import udmi.schema.Level;
import udmi.schema.PointPointsetConfig;

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
    gatewayConfig.target.family = describe("original family", savedTarget.family);
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
    gatewayConfig.target.addr = describe("original addr", savedTarget.addr);
    untilFalse("gateway status has no error", this::hasGatewayStatusDirty);
  }

  @Feature(stage = FeatureStage.PREVIEW, bucket = Bucket.GATEWAY)
  @Summary("Error handling for badly formed gateway point ref")
  @Test(timeout = TWO_MINUTES_MS)
  public void bad_point_ref() {
    String targetPoint = getTarget(TWEAKED_REF).target_point;
    cleanStatusCheck(targetPoint);
    PointPointsetConfig pointPointsetConfig = deviceConfig.pointset.points.get(targetPoint);
    String savedRef = pointPointsetConfig.ref;
    pointPointsetConfig.ref = getRandomCode("ref");
    untilTrue("point status has target error", this::hasPointStatusError);
    pointPointsetConfig.ref = describe("original ref", savedRef);
    untilFalse("no more pointset error", this::hasPointStatusDirty);
  }

  private void cleanStatusCheck(String targetPoint) {
    checkThatNot("gateway state with significant status", this::hasGatewayStatusDirty);
    if (targetPoint != null) {
      checkThatNot("pointset state with significant status", this::hasPointStatusDirty);
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
    return hasPointStatusDirty()
        && POINTSET_POINT_FAILURE.equals(status.category)
        && Level.ERROR == Level.fromValue(status.level);
  }

  private String getRandomCode(String prefix) {
    return describe("random " + prefix,
        format("%s-%04x", prefix, (int) Math.floor(Math.random() * 0x10000)));
  }
}
