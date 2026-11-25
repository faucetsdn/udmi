package com.google.daq.mqtt.sequencer.sequences;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.daq.mqtt.TestCommon;
import com.google.daq.mqtt.sequencer.SequenceBase;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import udmi.schema.DiscoveryEvents;
import udmi.schema.Enumerations;
import udmi.schema.Enumerations.Depth;
import udmi.schema.FamilyDiscovery;
import udmi.schema.LocalnetModel;
import udmi.schema.Metadata;
import udmi.schema.Parent;

/**
 * Unit tests for {@link DiscoverySequences} self-enumeration with optional localnet.families.
 */
public class DiscoverySequencesTest {

  private Metadata savedMetadata;

  /**
   * Configure sequencer execution config and capture original static metadata before each test.
   */
  @Before
  public void setUp() {
    SequenceBase.exeConfig = TestCommon.testConfiguration();
    SequenceBase.exeConfig.device_id = TestCommon.DEVICE_ID;
    savedMetadata = MetadataAccessor.get();
  }

  /**
   * Restore static metadata after each test to prevent cross-test state leakage.
   */
  @After
  public void tearDown() {
    MetadataAccessor.set(savedMetadata);
  }

  @Test
  public void checkSelfEnumerationHandlesNullLocalnetFamilies() {
    Metadata metadata = new Metadata();
    metadata.localnet = new LocalnetModel();
    metadata.localnet.parent = new Parent();
    metadata.localnet.parent.target = TestCommon.GATEWAY_ID;
    metadata.localnet.parent.family = "hardwired";
    MetadataAccessor.set(metadata);

    Enumerations depths = new Enumerations();
    depths.families = Depth.ENTRIES;

    DiscoveryEvents emptyEvent = new DiscoveryEvents();
    DiscoverySequences sequences = new DiscoverySequences();
    sequences.checkSelfEnumeration(emptyEvent, depths);

    DiscoveryEvents nonEmptyEvent = new DiscoveryEvents();
    nonEmptyEvent.families = new HashMap<>(Map.of("bacnet", new FamilyDiscovery()));
    IllegalStateException mismatch = assertThrows(IllegalStateException.class,
        () -> sequences.checkSelfEnumeration(nonEmptyEvent, depths));
    assertTrue(mismatch.getMessage().contains("family enumeration size matches"));
  }

  private abstract static class MetadataAccessor extends SequenceBase {
    static Metadata get() {
      return deviceMetadata;
    }

    static void set(Metadata metadata) {
      deviceMetadata = metadata;
    }
  }
}
