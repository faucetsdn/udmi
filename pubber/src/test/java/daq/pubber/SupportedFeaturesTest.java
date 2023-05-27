package daq.pubber;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static udmi.schema.Bucket.ENUMERATION;
import static udmi.schema.Bucket.ENUMERATION_FEATURES;

import java.util.Map;
import org.junit.Test;
import udmi.schema.Bucket;
import udmi.schema.FeatureEnumeration;
import udmi.schema.FeatureEnumeration.FeatureStage;

/**
 * Unit tests to make sure supported feature generation is working properly.
 */
public class SupportedFeaturesTest {

  @Test
  public void basicFeatureEnumeration() {
    Map<String, FeatureEnumeration> featureMap = SupportedFeatures.getFeatures();
    featureMap.forEach((key, value) -> {
      assertNotEquals("Feature " + key, FeatureStage.DISABLED, value.stage);
      assertTrue("Invalid feature name: " + key, Bucket.contains(key));
    });
    FeatureEnumeration enumeration = featureMap.get(ENUMERATION_FEATURES.value());
    assertEquals("features enumeration", enumeration.stage, FeatureStage.BETA);
  }

}
