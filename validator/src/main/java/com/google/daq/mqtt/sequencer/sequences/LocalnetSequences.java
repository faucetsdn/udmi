package com.google.daq.mqtt.sequencer.sequences;

import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.SkipTest;
import org.junit.Test;

/**
 * Validate localnet related functionality.
 */
public class LocalnetSequences extends SequenceBase {

  private void familyAddr(String family) {
    String expected = catchToNull(() -> deviceMetadata.localnet.families.get(family).addr);
    if (expected == null) {
      throw new SkipTest(String.format("No %S address defined in metadata", family));
    }
    untilTrue("localnet families available", () -> deviceState.localnet.families.size() > 0);
    String actual = catchToNull(() -> deviceState.localnet.families.get(family).addr);
    checkThat(String.format("device %s address was %s, expected %s", family, actual, expected),
        () -> expected.equals(actual));
  }

  @Test
  public void family_ether_addr() {
    familyAddr("ether");
  }

  @Test
  public void family_ipv4_addr() {
    familyAddr("ipv4");
  }

  @Test
  public void family_ipv6_addr() {
    familyAddr("ipv6");
  }
}
