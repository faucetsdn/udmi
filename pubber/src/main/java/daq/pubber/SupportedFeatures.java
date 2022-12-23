package daq.pubber;

import com.google.udmi.util.Features;
import java.util.Stack;

/**
 * Static class to represent the features supported by this implementation.
 */
public abstract class SupportedFeatures {

  private static Stack<Features> mapStack = new Stack<>();

  static {
    mapStack.push(new Features());
    group("system", () -> {
      group("mode", () -> {
        feature("restart");
        feature("suspend");
      });
    });
  }

  public static Features getFeatures() {
    return mapStack.peek();
  }

  private static void group(String name, Runnable value) {
    Features features = mapStack.peek().computeIfAbsent(name, key -> new Features());
    mapStack.push(features);
    value.run();
    mapStack.pop();
  }

  private static void feature(String name) {
    group(name, () -> {});
  }
}
