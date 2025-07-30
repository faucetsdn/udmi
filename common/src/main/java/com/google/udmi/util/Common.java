package com.google.udmi.util;

import static com.google.udmi.util.GeneralUtils.ifTrueGet;
import static com.google.udmi.util.GeneralUtils.stackTraceString;
import static com.google.udmi.util.JsonUtil.safeSleep;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.udmi.util.ExceptionMap.ExceptionCategory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.MissingFormatArgumentException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import udmi.schema.State;

/**
 * Collection of common constants and minor utilities.
 */
public abstract class Common {

  public static final String UPDATE_QUERY_TOPIC = "update/query";
  public static final String UPDATE_CONFIG_TOPIC = "update/config";
  public static final String EXCEPTION_KEY = "exception";
  public static final String ERROR_KEY = "error";
  public static final String DETAIL_KEY = "detail";
  public static final String TRANSACTION_KEY = "transactionId";
  public static final String PUBLISH_TIME_KEY = "publishTime";
  public static final String MESSAGE_KEY = "message";
  public static final String TIMESTAMP_KEY = "timestamp";
  public static final String VERSION_KEY = "version";
  public static final String UPGRADED_FROM = "upgraded_from";
  public static final String DOWNGRADED_FROM = "downgraded_from";
  public static final String CLOUD_VERSION_KEY = "cloud_version";
  public static final String SITE_METADATA_KEY = ExceptionCategory.site_metadata.toString();
  public static final String UDMI_VERSION_KEY = "udmi_version";
  public static final String UDMI_VERSION_ENV = "UDMI_VERSION";
  public static final String UDMI_COMMIT_ENV = "UDMI_COMMIT";
  public static final String UDMI_REF_ENV = "UDMI_REF";
  public static final String UDMI_TIMEVER_ENV = "UDMI_TIMEVER";
  public static final String SUBTYPE_PROPERTY_KEY = "subType";
  public static final String RAWFOLDER_PROPERTY_KEY = "rawFolder";
  public static final String SUBFOLDER_PROPERTY_KEY = "subFolder";
  public static final String PROJECT_ID_PROPERTY_KEY = "projectId";
  public static final String REGISTRY_ID_PROPERTY_KEY = "deviceRegistryId";
  public static final String DEFAULT_REGION = "us-central1";
  public static final String DEVICE_ID_KEY = "deviceId";
  public static final String DEVICE_NUM_KEY = "deviceNumId";
  public static final String GATEWAY_ID_KEY = "gatewayId";
  public static final String SOURCE_KEY = "source";
  public static final String NO_SITE = "--";
  public static final String GCP_REFLECT_KEY_PKCS8 = "reflector/rsa_private.pkcs8";
  public static final String CONDENSER_STRING = "^^";
  public static final char DETAIL_SEPARATOR_CHAR = ';';
  public static final String DETAIL_SEPARATOR = DETAIL_SEPARATOR_CHAR + " ";
  public static final Joiner DETAIL_JOINER = Joiner.on(DETAIL_SEPARATOR);
  public static final String CATEGORY_PROPERTY_KEY = "category";
  public static final Pattern DEVICE_ID_ALLOWABLE = Pattern.compile("^[-._a-zA-Z0-9]+$");
  public static final Pattern POINT_NAME_ALLOWABLE = Pattern.compile("^[-_a-zA-Z0-9]+$");
  public static final int SEC_TO_MS = 1000;
  public static final String SOURCE_SEPARATOR = "+";
  public static final String SOURCE_SEPARATOR_REGEX = "\\" + SOURCE_SEPARATOR;

  public static final String NAMESPACE_SEPARATOR = "~";
  public static final int EXIT_CODE_ERROR = 1;
  public static final String UNKNOWN_UDMI_VERSION = "unknown";

  public static final String UNKNOWN_DEVICE_ID_PREFIX = "UNK-";

  public static final String DOUBLE_COLON_SEPARATOR = "::";
  public static final String EMPTY_RETURN_RECEIPT = "-1";

  public static final Integer DEFAULT_EXTRAS_DELETION_DAYS = 40;
  public static final Integer DEFAULT_DEVICES_DELETION_DAYS = 30;

  /**
   * Remove the next item from the list in an exception-safe way.
   */
  public static String removeNextArg(List<String> argList) {
    if (argList.isEmpty()) {
      throw new MissingFormatArgumentException("Missing argument");
    }
    return argList.remove(0);
  }

  /**
   * Remove the next item from the list in an exception-safe way.
   */
  public static String removeNextArg(List<String> argList, String descriptor) {
    if (argList.isEmpty()) {
      throw new MissingFormatArgumentException("Missing argument " + descriptor);
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
    return Optional.ofNullable(System.getenv(UDMI_VERSION_ENV)).orElse(UNKNOWN_UDMI_VERSION);
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
      return new TreeSet<>(classes);
    } catch (Exception e) {
      throw new RuntimeException("While loading classes for package " + packageName, e);
    }
  }

  /**
   * Load a java class given the name. Converts ClassNotFoundException to RuntimeException for
   * convenience.
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

  public static String getExceptionDetail(Throwable exception, Class<?> container,
      Function<Throwable, String> customFilter) {
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

  public static String getNamespacePrefix(String udmiNamespace) {
    return Strings.isNullOrEmpty(udmiNamespace) ? "" : udmiNamespace + NAMESPACE_SEPARATOR;
  }

  public static Class<?> classForSchema(String schemaName) {
    try {
      String[] parts = schemaName.split("_");
      String className = ifTrueGet(parts.length > 1, () -> capitalize(parts[1]), "")
          + capitalize(parts[0]);
      String fullName = State.class.getPackageName() + "." + className;
      return Class.forName(fullName);
    } catch (Exception e) {
      throw new IllegalStateException("Could not find class for " + schemaName);
    }
  }

  public static void forcedDelayedShutdown() {
    // Force exist because PubSub Subscriber in PubSubReflector does not shut down properly.
    safeSleep(2000);
    System.exit(0);
  }

  public static String generateColonKey(String field1, String field2) {
    Objects.requireNonNull(field1, "field1 cannot be null");
    Objects.requireNonNull(field2, "field2 cannot be null");
    return field1 + DOUBLE_COLON_SEPARATOR + field2;
  }

  public static long convertDaysToMilliSeconds(int days) {
    return days * 24L * 60 * 60 * 1000;
  }

  public static void deleteFolder(File directory) {
    try {
      FileUtils.deleteDirectory(directory);
    } catch (Exception e) {
      throw new RuntimeException("Error deleting the directory", e);
    }
  }

  public static boolean isDifferenceGreaterThan(long startTimeMillis, long endTimeMillis, long durationMillis) {
    return (endTimeMillis - startTimeMillis) > durationMillis;
  }
}
