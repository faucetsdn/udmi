package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.ONE_MINUTE_MS;
import static com.google.udmi.util.GeneralUtils.catchToElse;
import static com.google.udmi.util.GeneralUtils.deepCopy;
import static com.google.udmi.util.GeneralUtils.ifNullThen;
import static udmi.schema.Category.GATEWAY_PROXY_TARGET;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.PointsetBase;
import com.google.daq.mqtt.sequencer.Summary;
import com.google.daq.mqtt.sequencer.semantic.SemanticValue;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.FamilyLocalnetModel;
import udmi.schema.FeatureEnumeration.FeatureStage;
import udmi.schema.GatewayConfig;
import udmi.schema.Level;

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

  // TODO: Add test for missing target/wrong address
  @Feature(stage = FeatureStage.ALPHA, bucket = Bucket.GATEWAY, nostate = true)
  @Summary("Basic check for proxied device proxying points")
  @Test(timeout = ONE_MINUTE_MS)
  public void bad_target_family() {
    initialStatusCheck();
    GatewayConfig gatewayConfig = deviceConfig.gateway;
    FamilyLocalnetModel savedTarget = deepCopy(gatewayConfig.target);
    ifNullThen(gatewayConfig.target, () -> gatewayConfig.target = new FamilyLocalnetModel());
    gatewayConfig.target.family = SemanticValue.describe("random family", getRandomFamily());
    untilTrue("gateway status has target error", this::hasTargetError);
    gatewayConfig.target = savedTarget;
    initialStatusCheck();
  }

  private void initialStatusCheck() {
    checkNotThat("no significant gateway status", this::hasGatewayStatus);
  }

  private boolean hasGatewayStatus() {
    Integer level = catchToElse(() -> deviceState.gateway.status.level, Level.INFO.value());
    return level > Level.INFO.value();
  }

  private boolean hasTargetError() {
    return hasGatewayStatus() && GATEWAY_PROXY_TARGET.equals(deviceState.gateway.status.category);
  }

  private String getRandomFamily() {
    return "family-" + String.format("%04x", (int) Math.floor(Math.random() * 0x10000));
  }
}
