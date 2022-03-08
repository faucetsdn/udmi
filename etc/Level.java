package udmi.schema;

import java.util.HashMap;
import java.util.Map;

// This class is manually curated and then copied into the gencode directory. Look for the
// proper source and don't be fooled! This is subset of the StackDriver LogSeverity levels.
public enum Level {

  DEBUG(100),
  INFO(200),
  NOTICE(300),
  WARNING(400),
  ERROR(500);

  private final int value;
  private final static Map<Integer, Level> CONSTANTS = new HashMap<>();

  static {
    for (Level c: values()) {
      CONSTANTS.put(c.value, c);
    }
  }

  Level(int value) {
    this.value = value;
  }

  public int value() {
    return this.value;
  }

  public static Level fromValue(int value) {
    Level constant = CONSTANTS.get(value);
    if (constant == null) {
      throw new IllegalArgumentException(Integer.toString(value));
    } else {
      return constant;
    }
  }
}
