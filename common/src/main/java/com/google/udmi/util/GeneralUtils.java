package com.google.udmi.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class GeneralUtils {
  /**
   * Returns a string of enabled options and values.
   */
  public static String optionsString(Object target) {
    List<String> options = new ArrayList<>();
    Class<?> clazz = target.getClass();
    Field[] fields = clazz.getDeclaredFields();

    for (Field field : fields) {
      try {
        if (field.get(target) != null && Boolean.TRUE.equals(field.get(target))) {
          options.add(field.getName());
        } else if (field.get(target) != null) {
          options.add(field.getName() + "=" + field.get(target));
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    return String.join(" ", options);
  }
  
}
