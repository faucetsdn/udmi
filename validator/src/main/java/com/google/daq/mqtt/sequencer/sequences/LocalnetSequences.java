package com.google.daq.mqtt.sequencer.sequences;

import static com.google.daq.mqtt.util.TimePeriodConstants.TWO_MINUTES_MS;
import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static java.lang.String.format;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.FeatureDiscovery.FeatureStage.PREVIEW;

import com.google.daq.mqtt.sequencer.Feature;
import com.google.daq.mqtt.sequencer.SequenceBase;
import java.util.HashMap;
import org.junit.Test;
import udmi.lib.ProtocolFamily;
import udmi.schema.FamilyLocalnetState;

/**
 * Validate localnet related functionality.
 */
public class LocalnetSequences extends SequenceBase {

  private void familyAddr(String family) {
    final String expected = ifNullSkipTest(
        catchToNull(() -> deviceMetadata.localnet.families.get(family).addr),
        format("No %s address defined in metadata", family));
    HashMap<String, FamilyLocalnetState> families = deviceState.localnet.families;
    waitUntil(format("device state localnet family %s is available", family),
        () -> families.containsKey(family) ? null
            : format("Because family %s not found in device state localnet family set: %s", family,
                CSV_JOINER.join(families.keySet())));
    String actual = families.get(family).addr;
    checkThat(format("family %s address matches", family), () -> expected.equals(actual));
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
