package daq.pubber;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Pubber configuration options which change default behavior.
 */
public class ConfigurationOptions {
  public Boolean noHardware;
  public String extraPoint;
  public String extraField;

  /**
   * Returns a string of enabled options and values.
   */
  public String toString() {
    List<String> options = new ArrayList<>();
    Field[] fields = ConfigurationOptions.class.getDeclaredFields();

    for (Field field : fields) {
      try {
        if (field.get(this) != null && Boolean.TRUE.equals(field.get(this))) {
          options.add(field.getName());
        } else if (field.get(this) != null) { 
          options.add(field.getName() + "=" + field.get(this));
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  return String.join(" ", options);
}

}
