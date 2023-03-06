package com.google.daq.mqtt.sequencer;

import static com.google.daq.mqtt.sequencer.SequenceRunner.processGiven;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static udmi.schema.SequenceValidationState.FeatureStage.ALPHA;
import static udmi.schema.SequenceValidationState.FeatureStage.BETA;
import static udmi.schema.SequenceValidationState.FeatureStage.STABLE;

import org.junit.Test;

/**
 * Test class for the sequencer Feature annotation.
 */
public class FeatureTest {

  @Test
  public void stageComparisons() {
    assertTrue(processGiven(BETA, BETA));
    assertTrue(processGiven(ALPHA, ALPHA));
    assertTrue(processGiven(STABLE, BETA));
    assertFalse(processGiven(ALPHA, BETA));
    assertFalse(processGiven(BETA, STABLE));
  }
}
