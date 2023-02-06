package com.google.daq.mqtt.sequencer;

import static com.google.daq.mqtt.sequencer.Feature.Stage.ALPHA;
import static com.google.daq.mqtt.sequencer.Feature.Stage.BETA;
import static com.google.daq.mqtt.sequencer.Feature.Stage.STABLE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test class for the sequencer Feature annotation.
 */
public class FeatureTest {

  @Test
  public void stageComparisons() {
    assertTrue(BETA.processGiven(BETA));
    assertTrue(ALPHA.processGiven(ALPHA));
    assertTrue(STABLE.processGiven(BETA));
    assertFalse(ALPHA.processGiven(BETA));
    assertFalse(BETA.processGiven(STABLE));
  }
}
