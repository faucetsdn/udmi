package com.google.daq.mqtt.sequencer;

import static com.google.daq.mqtt.sequencer.SequenceRunner.processGiven;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static udmi.schema.FeatureEnumeration.FeatureStage.ALPHA;
import static udmi.schema.FeatureEnumeration.FeatureStage.BETA;
import static udmi.schema.FeatureEnumeration.FeatureStage.DISABLED;
import static udmi.schema.FeatureEnumeration.FeatureStage.PREVIEW;
import static udmi.schema.FeatureEnumeration.FeatureStage.STABLE;

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
    assertTrue(processGiven(STABLE, ALPHA));
    assertTrue(processGiven(PREVIEW, ALPHA));
    assertFalse(processGiven(ALPHA, BETA));
    assertFalse(processGiven(PREVIEW, BETA));
    assertFalse(processGiven(BETA, STABLE));
    assertFalse(processGiven(DISABLED, ALPHA));
    assertFalse(processGiven(DISABLED, STABLE));
  }
}
