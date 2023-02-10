package daq.pubber;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.FeatureEnumerationEvent;
import udmi.schema.FeatureEnumerationEvent.Stage;

/**
 * Unit tests to make sure supported feature generation is working properly.
 */
public class SupportedFeaturesTest {

  @Test
  public void basicFeatureEnumeration() {
    Map<String, FeatureEnumerationEvent> features = SupportedFeatures.getFeatures();
    validateNames("", features);
    FeatureEnumerationEvent enumeration = features.get("enumeration");
    FeatureEnumerationEvent enumerationFeatures = enumeration.features.get("features");
    assertTrue("features are enumerated", enumerationFeatures.stage == Stage.STABLE);
  }

  private void validateNames(String prefix, Map<String, FeatureEnumerationEvent> features) {
    features.forEach((key, value) -> {
      assertNotEquals("Stage should not be missing", Stage.MISSING, value.stage);
      String fullName = prefix + (prefix.length() > 0 ? "." : "") + key;
      assertTrue("Invalid feature name: " + fullName, Bucket.contains(fullName));
      if (value.features != null) {
        validateNames(fullName, value.features);
      }
    });
  }
}
