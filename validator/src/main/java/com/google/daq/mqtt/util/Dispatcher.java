package com.google.daq.mqtt.util;

import static com.google.udmi.util.GeneralUtils.CSV_JOINER;
import static com.google.udmi.util.GeneralUtils.NEWLINE_JOINER;
import static com.google.udmi.util.GeneralUtils.friendlyLineTrace;
import static com.google.udmi.util.GeneralUtils.friendlyStackTrace;

import com.google.common.collect.ImmutableMap;
import com.google.daq.mqtt.registrar.Registrar;
import com.google.daq.mqtt.validator.Validator;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Simple utility dispatcher main class.
 */
public abstract class Dispatcher {

  private static final Map<String, Class<?>> UTILITIES = ImmutableMap.of(
      "diagnoser", Diagnoser.class,
      "validator", Validator.class,
      "registrar", Registrar.class
  );
  private static final int BAD_USAGE_ERROR_CODE = -2;
  private static final int INVOCATION_ERROR_CODE = -3;

  /**
   * Generic main to springboard into other mains based off of command line arg.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.exit(getUsage());
    }
    String toolName = args[0];
    if (!UTILITIES.containsKey(toolName)) {
      System.err.printf("Unknown utility name '%s'%n", toolName);
      System.exit(getUsage());
    }
    Class<?> toolClass = UTILITIES.get(toolName);
    try {
      Method mainMethod = toolClass.getMethod("main", String[].class);
      String[] newArgs = new String[args.length - 1];
      System.arraycopy(args, 1, newArgs, 0, newArgs.length);
      mainMethod.invoke(null, (Object) newArgs);
    } catch (IllegalAccessException | NoSuchMethodException e) {
      System.err.println("Dispatcher invocation exception: " + friendlyStackTrace(e));
      System.exit(INVOCATION_ERROR_CODE);
    } catch (Exception e) {
      System.err.printf("While executing %s%n", toolName);
      System.err.println(NEWLINE_JOINER.join(friendlyLineTrace(e)));
    }
  }

  private static int getUsage() {
    System.err.println("First command line argument should be utility name,");
    System.err.println("  one of: " + CSV_JOINER.join(UTILITIES.keySet()));
    return BAD_USAGE_ERROR_CODE;
  }
}
