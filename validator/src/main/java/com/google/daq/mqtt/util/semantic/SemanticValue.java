package com.google.daq.mqtt.util.semantic;

/**
 * Class of objects that hold a description for semantic understanding.
 */
public interface SemanticValue {

  String BEFORE_MARKER = "@@@";
  String AFTER_MARKER = "###";

  static boolean isSemanticValue(Object other) {
    boolean semanticString = other instanceof String && ((String) other).startsWith(BEFORE_MARKER);
    return semanticString || other instanceof SemanticValue;
  }

  static String describe(String description, String value) {
    return BEFORE_MARKER + description + AFTER_MARKER + value;
  }

  static boolean equals(Object left, Object right) {
    return left.equals(right)
        || (isSemanticString(left) && getDescription(left).equals(getDescription(right)));
  }

  static boolean isSemanticString(Object target) {
    return target instanceof String && ((String) target).startsWith(BEFORE_MARKER);
  }

  /**
   * Get the description of the semantic object.
   *
   * @param target thing to get description of
   * @return semantic description of the thing
   */
  static String getDescription(Object target) {
    if (target instanceof SemanticValue) {
      return ((SemanticValue) target).getDescription();
    }
    if (!isSemanticString(target)) {
      throw new RuntimeException("Trying to get description of non-semantic string");
    }
    String value = target.toString();
    return value.substring(BEFORE_MARKER.length(), value.indexOf(AFTER_MARKER));
  }

  /**
   * Get the description of this object.
   *
   * @return object description
   */
  String getDescription();
}
