package daq.pubber;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Pubber configuration options which change default behaviour
 */
public class ConfigurationOptions {
  public Boolean noHardware = false;
  public Boolean extraPoint = false;
  public Boolean extraField = false;


  public String toString() {
    String options = "";
    Field[] fields = ConfigurationOptions.class.getDeclaredFields();

    for (Field field : fields) {
      try {
        if ((Boolean) field.get(this)) {
         options = options + " " + field.getName();
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    return options;
  }
}
