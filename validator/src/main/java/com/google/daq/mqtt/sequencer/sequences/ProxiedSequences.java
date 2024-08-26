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
    cleanStatusCheck();
    GatewayConfig gatewayConfig = deviceConfig.gateway;
    final FamilyLocalnetModel savedTarget = deepCopy(gatewayConfig.target);
    ifNullThen(gatewayConfig.target, () -> gatewayConfig.target = new FamilyLocalnetModel());
    gatewayConfig.target.family = getRandomCode("family");
    untilTrue("gateway status has target error", this::hasTargetError);
    gatewayConfig.target = savedTarget;
    untilFalse("restored original target config", this::hasGatewayStatus);
  }

  @Feature(stage = FeatureStage.PREVIEW, bucket = Bucket.GATEWAY)
  @Summary("Error handling for badly formed gateway target address")
  @Test(timeout = TWO_MINUTES_MS)
  public void bad_target_address() {
    cleanStatusCheck();
    GatewayConfig gatewayConfig = deviceConfig.gateway;
    final FamilyLocalnetModel savedTarget = deepCopy(gatewayConfig.target);
    ifNullThen(gatewayConfig.target, () -> gatewayConfig.target = new FamilyLocalnetModel());
    gatewayConfig.target.addr = getRandomCode("addr");
    untilTrue("gateway status has target error", this::hasTargetError);
    gatewayConfig.target = savedTarget;
    untilFalse("restored original target config", this::hasGatewayStatus);
  }

  @Feature(stage = FeatureStage.PREVIEW, bucket = Bucket.GATEWAY)
  @Summary("Error handling for badly formed gateway point ref")
  @Test(timeout = TWO_MINUTES_MS)
  public void bad_point_ref() {
    TargetTestingModel target = getTarget(TWEAKED_REF);
    cleanStatusCheck();
    HashMap<String, PointPointsetConfig> points = deviceConfig.pointset.points;
    PointPointsetConfig pointPointsetConfig = points.get(target.target_point);
    PointPointsetConfig savedTarget = deepCopy(pointPointsetConfig);
  }

  private void cleanStatusCheck() {
    checkNotThat("gateway state with significant status", this::hasGatewayStatus);
  }

  private boolean hasGatewayStatus() {
    Integer level = catchToElse(() -> deviceState.gateway.status.level, Level.INFO.value());
    return level > Level.INFO.value();
  }

  private boolean hasTargetError() {
    return hasGatewayStatus()
        && GATEWAY_PROXY_TARGET.equals(deviceState.gateway.status.category)
        && Level.ERROR == Level.fromValue(deviceState.gateway.status.level);
  }

  private String getRandomCode(String prefix) {
    return String.format("%s-%04x", prefix, (int) Math.floor(Math.random() * 0x10000));
  }
}
