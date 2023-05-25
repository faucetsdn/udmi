package daq.pubber;

import static udmi.schema.Bucket.ENUMERATION;
import static udmi.schema.Bucket.ENUMERATION_FEATURES;
import static udmi.schema.FeatureEnumeration.FeatureStage.BETA;

import java.util.HashMap;
import java.util.Map;
import udmi.schema.Bucket;
import udmi.schema.FeatureEnumeration;
import udmi.schema.FeatureEnumeration.FeatureStage;

/**
 * Static class to represent the features supported by this implementation.
 */
public abstract class SupportedFeatures {

  private static final Map<String, FeatureEnumeration> FEATURES_MAP = new HashMap<>();

  static {
    add(ENUMERATION, BETA);
    add(ENUMERATION_FEATURES, BETA);
  }

  private static void add(Bucket featureBucket, FeatureStage stage) {
    FeatureEnumeration featureEnumeration = new FeatureEnumeration();
    featureEnumeration.stage = stage;
    FEATURES_MAP.put(featureBucket.value(), featureEnumeration);
  }

  public static Map<String, FeatureEnumeration> getFeatures() {
    return FEATURES_MAP;
  }
}
