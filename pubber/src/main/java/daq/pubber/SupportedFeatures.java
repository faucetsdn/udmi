package daq.pubber;

import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.writeFile;
import static udmi.schema.Bucket.ENUMERATION;
import static udmi.schema.Bucket.ENUMERATION_FAMILIES;
import static udmi.schema.Bucket.ENUMERATION_FEATURES;
import static udmi.schema.Bucket.ENUMERATION_POINTSET;
import static udmi.schema.Bucket.POINTSET;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.Bucket.UNKNOWN_DEFAULT;
import static udmi.schema.FeatureEnumeration.FeatureStage.ALPHA;
import static udmi.schema.FeatureEnumeration.FeatureStage.BETA;
import static udmi.schema.FeatureEnumeration.FeatureStage.PREVIEW;
import static udmi.schema.FeatureEnumeration.FeatureStage.STABLE;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import udmi.schema.Bucket;
import udmi.schema.FeatureEnumeration;
import udmi.schema.FeatureEnumeration.FeatureStage;

/**
 * Static class to represent the features supported by this implementation.
 */
public abstract class SupportedFeatures {

  private static final File PUBBER_FEATURES_JSON = new File("out/pubber_features.json");

  private static final Map<String, FeatureEnumeration> FEATURES_MAP = new HashMap<>();

  static {
    add(ENUMERATION, STABLE);
    add(ENUMERATION_FEATURES, BETA);
    add(ENUMERATION_FAMILIES, PREVIEW);
    add(ENUMERATION_POINTSET, ALPHA);
    add(POINTSET, BETA);
    add(SYSTEM, BETA);
  }

  private static void add(Bucket featureBucket, FeatureStage stage) {
    FeatureEnumeration featureEnumeration = new FeatureEnumeration();
    featureEnumeration.stage = stage;
    FEATURES_MAP.put(featureBucket.value(), featureEnumeration);
  }

  /**
   * Write out the current set of supported features for testability.
   */
  public static void writeFeatureFile() {
    try {
      PUBBER_FEATURES_JSON.getParentFile().mkdirs();
      writeFile(FEATURES_MAP, PUBBER_FEATURES_JSON);
    } catch (Exception e) {
      throw new RuntimeException("While making dir for " + PUBBER_FEATURES_JSON.getAbsolutePath());
    }
  }

  public static Map<String, FeatureEnumeration> getFeatures() {
    return FEATURES_MAP;
  }

  static void setFeatureSwap(Boolean option) {
    if (isTrue(option)) {
      add(UNKNOWN_DEFAULT, BETA);
      FEATURES_MAP.remove(ENUMERATION.value());
    }
  }
}
