package daq.pubber;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import udmi.schema.FeatureEnumerationEvent;

/**
 * Static class to represent the features supported by this implementation.
 */
public abstract class SupportedFeatures {
  static final Map<String, FeatureEnumerationEvent> FEATURES = ImmutableMap.of();

  public static Map<String, FeatureEnumerationEvent> getFeatures() {
    return FEATURES;
  }
}
