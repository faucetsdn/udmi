package daq.pubber;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import udmi.schema.FeatureEnumerationEvent;
import udmi.schema.FeatureEnumerationEvent.Stage;

/**
 * Static class to represent the features supported by this implementation.
 */
public abstract class SupportedFeatures {

  private static final Stack<FeatureEnumerationEvent> mapStack = new Stack<>();
  private static final Stack<String> fullName = new Stack<>();

  static {
    mapStack.push(new FeatureEnumerationEvent());
    bucket("enumeration", Stage.STABLE, () -> {
          bucket("feature");
          bucket("pointset", Stage.BETA);
          bucket("families", Stage.ALPHA);
        }
    );
  }

  public static Map<String, FeatureEnumerationEvent> getFeatures() {
    return mapStack.peek().features;
  }

  private static void bucket(String name) {
    bucket(name, Stage.STABLE);
  }

  private static void bucket(String name, Stage stage) {
    bucket(name, stage, null);
  }

  private static void bucket(String name, Stage stage, Runnable value) {
    FeatureEnumerationEvent container = mapStack.peek();
    String currentName = fullName.isEmpty() ? "" : fullName.peek();
    container.features = Optional.ofNullable(container.features).orElseGet(HashMap::new);
    FeatureEnumerationEvent features = container.features.computeIfAbsent(name,
        key -> new FeatureEnumerationEvent());
    features.stage = stage;
    if (value != null) {
      mapStack.push(features);
      fullName.push(currentName + "." + name);
      value.run();
      mapStack.pop();
      fullName.pop();
    }
  }
}
