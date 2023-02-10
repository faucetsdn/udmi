package daq.pubber;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;
import udmi.schema.FeatureEnumerationEvent;
import udmi.schema.FeatureEnumerationEvent.Stage;

public class SupportedFeaturesTest {

  @Test
  public void basicFeatureEnumeration() {
    Map<String, FeatureEnumerationEvent> features = SupportedFeatures.getFeatures();
    FeatureEnumerationEvent enumeration = features.get("enumeration");
    FeatureEnumerationEvent enumerationFeatures = enumeration.features.get("features");
    assertTrue("features are enumerated", enumerationFeatures.stage == Stage.STABLE);
  }
}