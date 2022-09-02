package com.google.daq.mqtt.util;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.MissingFormatArgumentException;

/**
 * Collection of common constants and minor utilities.
 */
public abstract class Common {

  public static final String STATE_QUERY_TOPIC = "query/state";
  public static final String TIMESTAMP_ATTRIBUTE = "timestamp";
  public static final String NO_SITE = "--";
  public static final String GCP_REFLECT_KEY_PKCS8 = "validator/rsa_private.pkcs8";

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
}
