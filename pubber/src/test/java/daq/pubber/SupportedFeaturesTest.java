package daq.pubber;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static udmi.schema.Bucket.ENUMERATION_FEATURES;

import java.util.Map;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.FeatureDiscovery;
import udmi.schema.FeatureDiscovery.FeatureStage;

/**
 * Unit tests to make sure supported feature generation is working properly.
 */
public class SupportedFeaturesTest {

  @Test
  public void basicFeatureDiscovery() {
    Map<String, FeatureDiscovery> featureMap = PubberFeatures.getFeatures();
    featureMap.forEach((key, value) -> {
      assertNotEquals("Feature " + key, FeatureStage.DISABLED, value.stage);
      assertTrue("Invalid feature name: " + key, Bucket.contains(key));
    });
    FeatureDiscovery enumeration = featureMap.get(ENUMERATION_FEATURES.value());
    assertEquals("features enumeration", enumeration.stage, FeatureStage.BETA);
  }

}
