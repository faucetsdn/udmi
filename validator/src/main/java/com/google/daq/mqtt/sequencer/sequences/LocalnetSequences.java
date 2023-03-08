package com.google.daq.mqtt.sequencer.sequences;

<<<<<<< HEAD
import static org.junit.Assert.assertEquals;

=======
>>>>>>> master
import com.google.daq.mqtt.sequencer.SequenceBase;
import com.google.daq.mqtt.sequencer.SkipTest;
import org.junit.Test;

/**
 * Validate localnet related functionality.
 */
public class LocalnetSequences extends SequenceBase {

  private void familyAddr(String family) {
<<<<<<< HEAD
    String expected = catchToNull(() -> deviceMetadata.localnet.networks.get(family).addr);
=======
    String expected = catchToNull(() -> deviceMetadata.localnet.families.get(family).addr);
>>>>>>> master
    if (expected == null) {
      throw new SkipTest(String.format("No %S address defined in metadata", family));
    }
    untilTrue("localnet families available", () -> deviceState.localnet.families.size() > 0);
    String actual = catchToNull(() -> deviceState.localnet.families.get(family).addr);
<<<<<<< HEAD
    assertEquals("device family address", expected, actual);
=======
    checkThat(String.format("device family %s address matches", family),
        () -> expected.equals(actual));
>>>>>>> master
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
