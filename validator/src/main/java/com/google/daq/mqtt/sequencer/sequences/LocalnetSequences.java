package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static java.lang.String.format;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.FeatureDiscovery.FeatureStage.PREVIEW;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import org.junit.Test;
import udmi.schema.Common.ProtocolFamily;

/**
 * Validate localnet related functionality.
 */
public class LocalnetSequences extends SequenceBase {

  private void familyAddr(ProtocolFamily family) {
    String expected = ifNullSkipTest(
        catchToNull(() -> deviceMetadata.localnet.families.get(family).addr),
        format("No %s address defined in metadata", family));
    untilTrue("localnet families available", () -> deviceState.localnet.families.size() > 0);
    String actual = catchToNull(() -> deviceState.localnet.families.get(family).addr);
    checkThat(format("device family %s address matches", family),
        () -> expected.equals(actual));
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = PREVIEW, bucket = SYSTEM)
  public void family_ether_addr() {
    familyAddr(ProtocolFamily.ETHER);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = PREVIEW, bucket = SYSTEM)
  public void family_ipv4_addr() {
    familyAddr(ProtocolFamily.IPV_4);
  }

  @Test(timeout = TWO_MINUTES_MS)
  @Feature(stage = PREVIEW, bucket = SYSTEM)
  public void family_ipv6_addr() {
    familyAddr(ProtocolFamily.IPV_6);
  }
}
