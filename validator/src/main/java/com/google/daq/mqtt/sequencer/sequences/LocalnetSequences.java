package com.google.daq.mqtt.sequencer.sequences;

import static org.junit.Assert.assertTrue;

import com.google.daq.mqtt.sequencer.SequenceBase;
import java.util.HashMap;
import org.junit.Test;
import udmi.schema.FamilyLocalnetState;

/**
 * Validate localnet related functionality.
 */
public class LocalnetSequences extends SequenceBase {

  @Test
  public void family_addrs() {
    untilTrue("localnet families available", () -> deviceState.localnet.families.size() > 0);
  }
}


