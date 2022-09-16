package com.google.daq.mqtt.util;

import com.google.daq.mqtt.sequencer.sequences.ConfigSequences;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.MissingFormatArgumentException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Collection of common constants and minor utilities.
 */
public abstract class Common {

  public static final String STATE_QUERY_TOPIC = "query/state";
  public static final String TIMESTAMP_ATTRIBUTE = "timestamp";
  public static final String NO_SITE = "--";
  public static final String GCP_REFLECT_KEY_PKCS8 = "validator/rsa_private.pkcs8";
  private static final String UDMI_VERSION_KEY = "UDMI_VERSION";

  /**
   * Remove the next item from the list in an exception-safe way.
   *
   * @param argList list of arguments
   * @return removed argument
   */
  public static String removeNextArg(List<String> argList) {
    if (argList.isEmpty()) {
      throw new MissingFormatArgumentException("Missing argument");
    }
    return argList.remove(0);
  }

  static String capitalize(String str) {
    return (str == null || str.isEmpty()) ? str
        : str.substring(0, 1).toUpperCase() + str.substring(1);
  }

  /**
   * Get the code line where the exception occurred (based on package name).
   *
   * @param e         Throwable to trace
   * @param container containing class to use as package prefix
   * @return string indicating the offending line
   */
  public static String getExceptionLine(Throwable e, Class<?> container) {
    String matchPrefix = "\tat " + container.getPackageName() + ".";
    String[] lines = stackTraceString(e).split("\n");
    for (String line : lines) {
      if (line.startsWith(matchPrefix)) {
        return line.substring(matchPrefix.length()).trim();
      }
    }
    return null;
  }

  /**
   * Get a string of the java strack trace.
   *
   * @param e stack to trace
   * @return stack trace string
   */
  public static String stackTraceString(Throwable e) {
    OutputStream outputStream = new ByteArrayOutputStream();
    try (PrintStream ps = new PrintStream(outputStream)) {
      e.printStackTrace(ps);
    }
    return outputStream.toString();
  }


  public static String getExceptionMessage(Throwable exception) {
    String message = exception.getMessage();
    return message != null ? message : exception.toString();
  }

  public static String getUdmiVersion() {
    return Optional.ofNullable(System.getenv(UDMI_VERSION_KEY)).orElse("unknown");
  }

  /**
   * Find all classes (by name) in the given package.
   *
   * @param packageName package to look for
   * @return classes in the indicated package
   */
  public static Set<String> findAllClassesUsingClassLoader(String packageName) {
    InputStream stream = ConfigSequences.class.getClassLoader()
        .getResourceAsStream(packageName.replaceAll("[.]", "/"));
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
    System.err.println("Filtering classes for package " + packageName);
    return reader.lines()
        .map(line -> {
          System.err.println(line);
          return line;
        })
        .filter(line -> line.endsWith(".class"))
        .map(line -> className(packageName, line))
        .collect(Collectors.toSet());
  }

  private static String className(String packageName, String line) {
    return packageName + "." + line.substring(0, line.lastIndexOf("."));
  }

  /**
   * Load a java class given the name. Converts ClassNotFoundException to
   * RuntimeException for convenience.
   *
   * @param className class to load
   * @return loaded class
   */
  public static Class<?> classForName(String className) {
    try {
      return ConfigSequences.class.getClassLoader().loadClass(className);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Class not found " + className, e);
    }
  }
}
