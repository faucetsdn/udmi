package udmi.lib.intf;

import static com.google.udmi.util.GeneralUtils.getTimestamp;
import static com.google.udmi.util.GeneralUtils.isTrue;
import static com.google.udmi.util.JsonUtil.writeFile;
import static udmi.schema.Bucket.ENDPOINT_CONFIG;
import static udmi.schema.Bucket.ENUMERATION;
import static udmi.schema.Bucket.ENUMERATION_FAMILIES;
import static udmi.schema.Bucket.ENUMERATION_FEATURES;
import static udmi.schema.Bucket.ENUMERATION_POINTSET;
import static udmi.schema.Bucket.POINTSET;
import static udmi.schema.Bucket.SYSTEM;
import static udmi.schema.Bucket.UNKNOWN_DEFAULT;
import static udmi.schema.FeatureDiscovery.FeatureStage.ALPHA;
import static udmi.schema.FeatureDiscovery.FeatureStage.BETA;
import static udmi.schema.FeatureDiscovery.FeatureStage.PREVIEW;
import static udmi.schema.FeatureDiscovery.FeatureStage.STABLE;

import daq.pubber.PubberDeviceManager;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import udmi.schema.Bucket;
import udmi.schema.FeatureDiscovery;
import udmi.schema.FeatureDiscovery.FeatureStage;
import udmi.schema.Level;

/**
 * Static class to represent the features supported by this implementation.
 */
public abstract class SupportedFeatures {

  private static final String PUBBER_FEATURES_JSON = "out/pubber_features.json";

  private static final Map<String, FeatureDiscovery> FEATURES_MAP = new HashMap<>();

  static {
    add(ENDPOINT_CONFIG, BETA);
    add(ENUMERATION, STABLE);
    add(ENUMERATION_FEATURES, BETA);
    add(ENUMERATION_FAMILIES, PREVIEW);
    add(ENUMERATION_POINTSET, ALPHA);
    add(POINTSET, BETA);
    add(SYSTEM, BETA);
  }

  private static void add(Bucket featureBucket, FeatureStage stage) {
    FeatureDiscovery featureDiscovery = new FeatureDiscovery();
    featureDiscovery.stage = stage;
    FEATURES_MAP.put(featureBucket.value(), featureDiscovery);
  }

  /**
   * Write out the current set of supported features for testability.
   */
  public static void writeFeatureFile(String sitePath, PubberDeviceManager deviceManager) {
    File path = new File(sitePath, PUBBER_FEATURES_JSON);
    try {
      String message = "Writing pubber feature file to " + path.getAbsolutePath();
      deviceManager.localLog(message, Level.NOTICE, getTimestamp(), null);
      path.getParentFile().mkdirs();
      writeFile(FEATURES_MAP, path);
    } catch (Exception e) {
      throw new RuntimeException("While making dir for " + path.getAbsolutePath());
    }
  }

  public static Map<String, FeatureDiscovery> getFeatures() {
    return FEATURES_MAP;
  }

  /**
   * Set feature swap.
   */
  public static void setFeatureSwap(Boolean option) {
    if (isTrue(option)) {
      add(UNKNOWN_DEFAULT, BETA);
      FEATURES_MAP.remove(ENUMERATION.value());
    }
  }
}
