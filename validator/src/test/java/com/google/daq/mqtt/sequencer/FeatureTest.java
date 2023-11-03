package com.google.daq.mqtt.sequencer;

import static com.google.daq.mqtt.sequencer.SequenceRunner.processStage;
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
    assertTrue(processStage(BETA, BETA));
    assertTrue(processStage(ALPHA, ALPHA));
    assertTrue(processStage(STABLE, BETA));
    assertTrue(processStage(STABLE, ALPHA));
    assertTrue(processStage(PREVIEW, ALPHA));
    assertFalse(processStage(ALPHA, BETA));
    assertFalse(processStage(PREVIEW, BETA));
    assertFalse(processStage(BETA, STABLE));
    assertFalse(processStage(DISABLED, ALPHA));
    assertFalse(processStage(DISABLED, STABLE));
  }
}
