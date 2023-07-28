package com.google.daq.mqtt.sequencer.sequences;

import static java.lang.String.format;

import com.google.daq.mqtt.sequencer.SequenceBase;
import org.junit.Test;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validate localnet related functionality.
 */
public class LocalnetSequences extends SequenceBase {

  private void familyAddr(String family) {
    Set<String> expectedFamilies = deviceMetadata.localnet.families.keySet().stream()
        .filter(f -> f.contains(family)).collect(Collectors.toSet());
    if (expectedFamilies.isEmpty()) {
        skipTest(format("No %s address defined in metadata", family));
    }
    expectedFamilies.forEach(expectedFamily -> {
        String expected = catchToNull(() -> deviceMetadata.localnet.families.get(expectedFamily).addr);
        untilTrue("localnet families available", () -> deviceState.localnet.families.size() > 0);
        String actual = catchToNull(() -> deviceState.localnet.families.get(expectedFamily).addr);
        checkThat(format("device family %s address matches", expectedFamily), () -> expected.equals(actual));
    });
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
