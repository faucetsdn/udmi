package com.google.daq.mqtt.sequencer.sequences;

import static java.lang.String.format;

import com.google.daq.mqtt.sequencer.SequenceBase;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

/**
 * Validate localnet related functionality.
 */
public class LocalnetSequences extends SequenceBase {

  private void familyAddr(String family) {
    String expected = catchToNull(() -> deviceMetadata.localnet.families.get(family).addr);
    if (expected == null) {
      throw new AssumptionViolatedException(
          format("No %S address defined in metadata", family));
    }
    untilTrue("localnet families available", () -> deviceState.localnet.families.size() > 0);
    String actual = catchToNull(() -> deviceState.localnet.families.get(family).addr);
    checkThat(format("device family %s address matches", family),
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
