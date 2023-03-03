package com.google.udmi.util;

import static com.google.udmi.util.GeneralUtils.stackTraceString;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingFormatArgumentException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Collection of common constants and minor utilities.
 */
public abstract class Common {

  public static final String STATE_QUERY_TOPIC = "update/query";
  public static final String TIMESTAMP_PROPERTY_KEY = "timestamp";
  public static final String VERSION_PROPERTY_KEY = "version";
  public static final String SUBTYPE_PROPERTY_KEY = "subType";
  public static final String SUBFOLDER_PROPERTY_KEY = "subFolder";
  public static final String NO_SITE = "--";
  public static final String GCP_REFLECT_KEY_PKCS8 = "validator/rsa_private.pkcs8";
  public static final String EXCEPTION_KEY = "exception";
  private static final String UDMI_VERSION_KEY = "UDMI_VERSION";
  public static final char DETAIL_SEPARATOR_CHAR = ';';
  public static final String DETAIL_SEPARATOR = DETAIL_SEPARATOR_CHAR + " ";
  public static final Joiner DETAIL_JOINER = Joiner.on(DETAIL_SEPARATOR);

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

  public static String capitalize(String str) {
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
   * @param clazz existing class that's in the package-of-interest
   * @return set of class names in the indicated package
   */
  public static Set<String> allClassesInPackage(Class<?> clazz) {
    String packageName = clazz.getPackageName();
    try {
      ClassPath classPath = ClassPath.from(Common.class.getClassLoader());
      Set<String> classes = classPath.getAllClasses().stream()
          .filter(info -> info.getPackageName().equals(packageName))
          .map(ClassInfo::getName)
          .collect(Collectors.toSet());
      return classes;
    } catch (Exception e) {
      throw new RuntimeException("While loading classes for package " + packageName);
    }
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
      return Common.class.getClassLoader().loadClass(className);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Class not found " + className, e);
    }
  }

  public static String getExceptionDetail(Throwable exception, Class<?> container, Function<Throwable, String> customFilter) {
    List<String> messages = new ArrayList<>();
    String previousMessage = null;
    while (exception != null) {
      final String useMessage;
      useMessage = getExceptionMessage(exception, container, customFilter);
      if (previousMessage == null || !previousMessage.equals(useMessage)) {
        messages.add(useMessage);
        previousMessage = useMessage;
      }
      exception = exception.getCause();
    }
    return DETAIL_JOINER.join(messages);
  }

  private static String getExceptionMessage(Throwable exception, Class<?> container,
      Function<Throwable, String> customFilter) {
    if (customFilter != null) {
       String customMessage = customFilter.apply(exception);
       if (customMessage != null) {
         return customMessage;
       }
    }
    String message = getExceptionMessage(exception);
    String line = getExceptionLine(exception, container);
    return message + (line == null ? "" : " @" + line);
  }
}
