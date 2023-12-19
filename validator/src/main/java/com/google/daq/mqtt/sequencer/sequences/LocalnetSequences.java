package com.google.daq.mqtt.sequencer.sequences;

import static java.lang.String.format;
import static udmi.schema.Bucket.ENDPOINT;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.FeatureEnumeration.FeatureStage.PREVIEW;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import org.junit.Test;

/**
 * Validate localnet related functionality.
 */
public class LocalnetSequences extends SequenceBase {

  private void familyAddr(String family) {
    String expected = ifNullSkipTest(
        catchToNull(() -> deviceMetadata.localnet.families.get(family).addr),
        format("No %s address defined in metadata", family));
    untilTrue("localnet families available", () -> deviceState.localnet.families.size() > 0);
    String actual = catchToNull(() -> deviceState.localnet.families.get(family).addr);
    checkThat(format("device family %s address matches", family),
        () -> expected.equals(actual));
  }

  @Test
  @Feature(stage = PREVIEW, bucket = SYSTEM)
  public void family_ether_addr() {
    familyAddr("ether");
  }

  @Test
  @Feature(stage = PREVIEW, bucket = SYSTEM)
  public void family_ipv4_addr() {
    familyAddr("ipv4");
  }

  @Test
  @Feature(stage = PREVIEW, bucket = SYSTEM)
  public void family_ipv6_addr() {
    familyAddr("ipv6");
  }
}
