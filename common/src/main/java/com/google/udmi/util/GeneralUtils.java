package com.google.udmi.util;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.symmetricDifference;
import static com.google.udmi.util.JsonUtil.isoConvert;
import static com.google.udmi.util.ProperPrinter.OutputFormat.COMPRESSED;
import static com.google.udmi.util.ProperPrinter.OutputFormat.VERBOSE;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.udmi.util.ProperPrinter.OutputFormat;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;

public class GeneralUtils {

  public static final Joiner CSV_JOINER = Joiner.on(", ");
  public static final Joiner NEWLINE_JOINER = Joiner.on("\n");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .setDateFormat(new ISO8601DateFormat())
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .setSerializationInclusion(Include.NON_NULL);
  public static final ObjectMapper OBJECT_MAPPER_RAW =
      OBJECT_MAPPER.copy()
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .enable(Feature.ALLOW_TRAILING_COMMA)
          .enable(Feature.STRICT_DUPLICATE_DETECTION)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  public static final ObjectMapper OBJECT_MAPPER_STRICT =
      OBJECT_MAPPER_RAW.copy()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .disable(SerializationFeature.INDENT_OUTPUT);

  private static final String SEPARATOR = "\n  ";
  private static final Joiner INDENTED_LINES = Joiner.on(SEPARATOR);
  private static final String NULL_STRING = "null";
  private static final String CONDITIONAL_KEY_PREFIX = "?";
  public static final int SET_SIZE_THRESHOLD = 10;
  private static Duration clockSkew = Duration.ZERO;

  public static String[] arrayOf(String... args) {
    return args;
  }

  public static String booleanString(Boolean bool) {
    return ifNotNullGet(bool, value -> Boolean.toString(bool));
  }

  public static String changedLines(List<DiffEntry> nullableChanges) {
    List<DiffEntry> changes = ofNullable(nullableChanges).orElse(ImmutableList.of());
    String terminator = changes.size() == 0 ? "." : ":";
    String header = format("Changed %d fields%s%s", changes.size(), terminator, SEPARATOR);
    return (header + INDENTED_LINES.join(changes)).trim();
  }

  /**
   * Shallow all fields from one class to another existing class. This can be used, for example, if
   * the target class is "final" but the fields themselves need to be updated.
   *
   * @param from source object
   * @param to   target object
   * @param <T>  type of object
   */
  public static <T> void copyFields(T from, T to, boolean includeNull) {
    Field[] fields = from.getClass().getDeclaredFields();
    for (Field field : fields) {
      if (!Modifier.isStatic(field.getModifiers())) {
        try {
          Object value = field.get(from);
          if (includeNull || value != null) {
            field.set(to, value);
          }
        } catch (Exception e) {
          throw new RuntimeException("While copying field " + field.getName(), e);
        }
      }
    }
  }

  public static String decodeBase64(String payload) {
    return new String(Base64.getDecoder().decode(payload));
  }

  public static <T> T deepCopy(T object) {
    if (object == null) {
      return null;
    }
    Class<?> targetClass = object.getClass();
    try {
      @SuppressWarnings("unchecked")
      T t = (T) OBJECT_MAPPER.readValue(toJsonString(object), targetClass);
      return t;
    } catch (Exception e) {
      throw new RuntimeException("While making deep copy of " + targetClass.getName(), e);
    }
  }

  public static String encodeBase64(String payload) {
    return ifNotNullGet(payload, raw -> encodeBase64(raw.getBytes()));
  }

  public static String encodeBase64(byte[] payload) {
    return Base64.getEncoder().encodeToString(payload);
  }

  /**
   * TODO: Update to use varargs, and create non-supplier version.
   */
  public static <T> T firstNonNull(T obj1, T obj2, Supplier<T> supplier) {
    return ofNullable(ofNullable(obj1).orElse(obj2)).orElseGet(supplier);
  }

  /**
   * Get a "friendly" (cause messages only) stack trace string.  There is no science to this, it's
   * just a hacky algorithm that turns a pedantically detailed Java stack trace into something
   * hopefully somewhat meaningful. Real debuggers will need to dig out the full stack trace!
   */
  public static String friendlyStackTrace(Throwable e) {
    List<String> lines = e instanceof NullPointerException ? traceDetails(e) : friendlyLineTrace(e);
    return CSV_JOINER.join(lines).replace('\n', ' ');
  }

  private static List<String> traceDetails(Throwable e) {
    // Only include the first two lines of output, which will have the message and offending line.
    return Arrays.stream(stackTraceString(e).split("\n")).limit(2).toList();
  }

  public static List<String> friendlyLineTrace(Throwable e) {
    List<String> messages = new ArrayList<>();
    while (e != null) {
      if (e instanceof ValidationException validationException) {
        ImmutableList<ValidationException> causes = validationException.getCausingExceptions();
        if (causes.isEmpty()) {
          messages.add(validationException.getMessage());
        } else {
          causes.forEach(exception -> messages.add(friendlyStackTrace(exception)));
        }
      } else {
        messages.add(ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName()));
      }
      e = e.getCause();
    }
    return messages;
  }

  public static String getRootCause(Throwable e) {
    while (e.getCause() != null) {
      e = e.getCause();
    }
    return e.getMessage();
  }

  private static <T> T fromJsonFile(File path, Class<T> valueType, ObjectMapper objectMapper) {
    try {
      return OBJECT_MAPPER_STRICT.readValue(path, valueType);
    } catch (Exception e) {
      throw new RuntimeException("While loading json file " + path.getAbsolutePath(), e);
    }
  }

  public static <T> T fromJsonFile(File path, Class<T> valueType) {
    return fromJsonFile(path, valueType, OBJECT_MAPPER);
  }

  public static <T> T fromJsonFileStrict(File path, Class<T> valueType) {
    return fromJsonFile(path, valueType, OBJECT_MAPPER_STRICT);
  }

  public static <T> T fromJsonString(String body, Class<T> valueType) {
    try {
      if (body == null) {
        return null;
      }
      return OBJECT_MAPPER.readValue(body, valueType);
    } catch (Exception e) {
      throw new RuntimeException("While loading json string", e);
    }
  }

  public static String compressJsonString(Object data, int lengthThreshold) {
    try {
      String prettyString = writeToPrettyString(data, VERBOSE);
      if (prettyString.length() <= lengthThreshold) {
        return prettyString;
      }

      return writeToPrettyString(data, COMPRESSED);
    } catch (Exception e) {
      throw new RuntimeException("While converting to limited json string", e);
    }
  }

  public static Runnable ignoreValue(Object ignored) {
    return () -> {
    };
  }

  public static boolean isNotEmpty(String s) {
    return !ofNullable(s).map(String::isEmpty).orElse(true);
  }

  public static boolean isNullOrNotEmpty(String value) {
    return !ofNullable(value).map(String::isEmpty).orElse(false);
  }

  public static boolean isNullOrTruthy(String value) {
    return ofNullable(value).map(GeneralUtils::isTruthy).orElse(true);
  }

  private static boolean isTruthy(String value) {
    return value != null && !value.isEmpty() && !"false".equals(value);
  }

  public static String nullAsNull(String part) {
    return NULL_STRING.equals(part) ? null : part;
  }

  public static String thereCanBeOnlyOne(List<String> list) {
    if (list == null || list.size() == 0) {
      return null;
    }
    if (list.size() != 1) {
      throw new RuntimeException("More than one singular candidate: " + list);
    }
    return list.get(0);
  }

  public static Date toDate(Instant lastSeen) {
    return ifNotNullGet(lastSeen, Date::from);
  }

  private static String writeToPrettyString(Object data, OutputFormat indent) {
    try {
      ByteArrayOutputStream outputString = new ByteArrayOutputStream();
      OBJECT_MAPPER_STRICT.writeValue(getPrettyPrinterGenerator(outputString, indent), data);
      return outputString.toString();
    } catch (Exception e) {
      throw new RuntimeException("While writing output string", e);
    }
  }

  /**
   * A custom generator can't be set on a base object mapper instance, so need to do it for each
   * invocation.
   */
  private static JsonGenerator getPrettyPrinterGenerator(OutputStream outputStream,
      OutputFormat indent) {
    try {
      return OBJECT_MAPPER_STRICT
          .getFactory()
          .createGenerator(outputStream)
          .setPrettyPrinter(
              indent == VERBOSE ? ProperPrinter.INDENT_PRINTER : ProperPrinter.NO_INDENT_PRINTER);
    } catch (Exception e) {
      throw new RuntimeException("While creating pretty printer", e);
    }
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> getSubMap(Map<String, Object> input, String field) {
    return (Map<String, Object>) input.get(field);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> getSubMapNull(Map<String, Object> input, String field) {
    return ifNotNullGet(input, map -> (Map<String, Object>) map.get(field));
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> getSubMapDefault(Map<String, Object> input, String field) {
    return ifNotNullGet(input,
        map -> (Map<String, Object>) map.computeIfAbsent(field, k -> new HashMap<>()));
  }

  public static <T, V> V ifNotNullGet(T value, Function<T, V> converter) {
    return ifNotNullGet(value, converter, null);
  }

  public static <T, V> V ifNotNullGet(T value, Function<T, V> converter, V elseResult) {
    return value == null ? elseResult : converter.apply(value);
  }

  public static <T, V> V ifNotNullGetElse(T value, Function<T, V> converter,
      Supplier<V> elseResult) {
    return value == null ? elseResult.get() : converter.apply(value);
  }

  public static <T, V> V ifNotNullGet(T value, Supplier<V> converter) {
    return value == null ? null : converter.get();
  }

  public static <T, V> V ifNullElse(T value, V elseResult, Function<T, V> converter) {
    return value == null ? elseResult : converter.apply(value);
  }

  public static void ifNullThen(Object value, Runnable action) {
    if (value == null) {
      action.run();
    }
  }

  public static void requireNull(Object value, String description) {
    checkState(value == null, description);
  }

  public static <T extends Collection<?>> void ifNotEmptyThrow(T value,
      Function<T, String> detailer) {
    if (!value.isEmpty()) {
      throw new RuntimeException(detailer.apply(value));
    }
  }

  public static <T extends Collection<?>> void ifNotEmptyThen(T value, Consumer<T> handler) {
    if (!value.isEmpty()) {
      handler.accept(value);
    }
  }

  public static <T> void ifNotNullThrow(T value, String message) {
    if (value != null) {
      throw new RuntimeException(message);
    }
  }

  public static <T> void ifNotNullThrow(T value, Function<T, String> formatter) {
    if (value != null) {
      throw new RuntimeException(formatter.apply(value));
    }
  }

  public static <T extends RuntimeException> void ifNotNullThrow(T value) {
    if (value != null) {
      throw value;
    }
  }

  public static <T> void ifNotNullThen(T value, Consumer<T> consumer) {
    ofNullable(value).ifPresent(consumer);
  }

  public static <T> void ifNotNullThen(T value, Consumer<T> consumer, Runnable otherwise) {
    ofNullable(value).ifPresentOrElse(consumer, otherwise);
  }

  public static <T> void ifNotNullThen(T value, Runnable action) {
    ofNullable(value).ifPresent(derp -> action.run());
  }

  public static <T> void ifNotTrueThen(Object conditional, Runnable action) {
    if (!isTrue(conditional)) {
      action.run();
    }
  }

  public static <T> T ifTrueGet(Boolean conditional, T value) {
    return ifTrueGet(conditional, () -> value);
  }

  public static <T> T ifTrueGet(Boolean conditional, Supplier<T> action) {
    if (isTrue(conditional)) {
      return action.get();
    }
    return null;
  }

  public static <T> T ifTrueGet(Supplier<Boolean> conditional, Supplier<T> action) {
    return isTrue(conditional) ? action.get() : null;
  }

  public static <T> T ifTrueGet(Object conditional, Supplier<T> action, Supplier<T> alternate) {
    return isTrue(conditional) ? action.get() : alternate.get();
  }

  public static <T> T ifTrueGet(Object conditional, Supplier<T> action, T alternate) {
    return isTrue(conditional) ? action.get() : alternate;
  }

  public static <T> T ifTrueGet(Object conditional, T value, Supplier<T> alternate) {
    return isTrue(conditional) ? value : alternate.get();
  }

  public static <T> T ifTrueGet(Object conditional, T value, T alternate) {
    return isTrue(conditional) ? value : alternate;
  }

  public static <T> void ifTrueThen(Object conditional, Runnable action) {
    ifTrueThen(conditional, action, null);
  }

  public static <T> void ifTrueThen(Object conditional, Runnable action, Runnable alternative) {
    if (isTrue(conditional)) {
      action.run();
    } else if (alternative != null) {
      alternative.run();
    }
  }

  public static <T> T ifNotTrueGet(Boolean conditional, Supplier<T> supplier) {
    return isTrue(conditional) ? null : supplier.get();
  }

  public static <T> T ifNotTrueGet(Boolean conditional, T value) {
    return isTrue(conditional) ? null : value;
  }

  public static <T> T ifNotTrueGet(Supplier<Boolean> conditional, Supplier<T> supplier) {
    return isTrue(catchToNull(conditional)) ? null : supplier.get();
  }

  public static <T> T ifNotTrueGet(Supplier<Boolean> conditional, T value) {
    return isTrue(catchToNull(conditional)) ? null : value;
  }

  public static boolean isNotTrue(Boolean value) {
    return !isTrue(value);
  }

  public static boolean isTrue(Object value) {
    return Boolean.TRUE.equals(value);
  }

  public static boolean isGetTrue(Supplier<Object> target) {
    try {
      return Boolean.TRUE.equals(target.get());
    } catch (Exception e) {
      return false;
    }
  }

  public static void catchOrElse(Runnable action, Consumer<Exception> caught) {
    try {
      action.run();
    } catch (Exception e) {
      caught.accept(e);
    }
  }

  public static <T> T catchOrElse(Supplier<T> provider, Supplier<T> alternate) {
    try {
      T t = provider.get();
      if (t != null) {
        return t;
      }
    } catch (Exception e) {
      // Silently ignore by design.
    }
    return alternate.get();
  }

  public static <T> T catchToElse(Supplier<T> provider, Function<Exception, T> alternate) {
    try {
      return provider.get();
    } catch (Exception e) {
      return alternate.apply(e);
    }
  }

  public static <T> T catchToElse(Supplier<T> provider, Consumer<Exception> alternate) {
    try {
      return provider.get();
    } catch (Exception e) {
      alternate.accept(e);
      return null;
    }
  }

  public static void catchToElse(Runnable provider, Consumer<Exception> alternate) {
    try {
      provider.run();
    } catch (Exception e) {
      alternate.accept(e);
    }
  }

  public static <T> T catchToElse(Supplier<T> provider, T alternate) {
    try {
      return provider.get();
    } catch (Exception e) {
      return alternate;
    }
  }

  public static boolean catchToFalse(Supplier<Boolean> provider) {
    return catchToElse(provider, false);
  }

  public static boolean catchToTrue(Supplier<Boolean> provider) {
    return catchToElse(provider, true);
  }

  public static <T> T catchToNull(Supplier<T> provider) {
    return catchToElse(provider, (T) null);
  }

  public static String catchToMessage(Runnable action) {
    try {
      action.run();
      return null;
    } catch (Exception e) {
      return e.getMessage();
    }
  }

  public static String joinOrNull(String prefix, Set<Object> setDifference) {
    return ifTrueGet(setDifference == null || setDifference.isEmpty(), (String) null,
        () -> prefix + CSV_JOINER.join(setDifference));
  }

  public static <U> U mapReplace(U previous, U added) {
    return added;
  }

  public static <T> T mergeObject(Object under, Object over) {
    Map<String, Object> target = JsonUtil.asMap(under);
    mergeObject(target, JsonUtil.asMap(over));
    @SuppressWarnings("unchecked")
    T t = (T) JsonUtil.convertTo(under.getClass(), target);
    return t;
  }

  public static void mergeObject(Map<String, Object> under, Map<String, Object> over) {
    over.keySet().forEach(key -> {
          String conditionalKey = CONDITIONAL_KEY_PREFIX + key;
          Object underValue = under.get(key);
          Object underMaybe = under.get(conditionalKey);
          Object overValue = over.get(key);
          if (overValue instanceof Map) {
            if (underMaybe instanceof Map) {
              mergeHelper(underMaybe, overValue);
              under.put(key, underMaybe);
              under.remove(conditionalKey);
            } else if (underValue instanceof Map) {
              mergeHelper(underValue, overValue);
            } else {
              under.put(key, overValue);
            }
          } else {
            under.put(key, overValue);
            under.remove(conditionalKey);
          }
        });
    Set<String> extras = Sets.difference(under.keySet(), over.keySet()).stream()
        .filter(key -> key.startsWith(CONDITIONAL_KEY_PREFIX)).collect(Collectors.toSet());
    extras.forEach(under::remove);
  }

  private static void mergeHelper(Object underValue, Object overValue) {
    @SuppressWarnings("unchecked")
    Map<String, Object> underCast = (Map<String, Object>) underValue;
    @SuppressWarnings("unchecked")
    Map<String, Object> overCast = (Map<String, Object>) overValue;
    mergeObject(underCast, overCast);
  }

  /**
   * Returns a string of enabled options and values.
   */
  public static String optionsString(Object target) {
    List<String> options = new ArrayList<>();
    Class<?> clazz = target.getClass();
    Field[] fields = clazz.getDeclaredFields();

    for (Field field : fields) {
      try {
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
          continue;
        }

        Object fieldValue = field.get(target);
        if (fieldValue instanceof Boolean) {
          ifTrueThen(fieldValue, () -> options.add(field.getName()));
        } else if (fieldValue != null) {
          options.add(field.getName() + "=" + fieldValue);
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException("While accessing field " + field.getName(), e);
      }
    }
    return String.join(" ", options);
  }

  public static List<String> runtimeExec(String... command) {
    ProcessBuilder processBuilder = new ProcessBuilder().command(command);
    try {
      Process process = processBuilder.start();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream()))) {
        int exitVal = process.waitFor();
        if (exitVal != 0) {
          throw new RuntimeException("Process exited with exit code " + exitVal);
        }
        return reader.lines().collect(Collectors.toList());
      }
    } catch (Exception e) {
      throw new RuntimeException("While executing subprocess " + String.join(" ", command), e);
    }
  }

  public static String sha256(String input) {
    return sha256(input.getBytes());
  }

  public static String sha256(byte[] bytes) {
    return Hashing.sha256().hashBytes(bytes).toString();
  }

  public static <T> Collector<Entry<String, T>, ?, TreeMap<String, String>> sortedMapCollector(
      Function<Entry<String, T>, String> prioritizer) {
    return Collectors.toMap(prioritizer, Entry::getKey, GeneralUtils::mapReplace, TreeMap::new);
  }

  /**
   * Get a string of the java stack trace.
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

  public static void toJsonFile(File file, Object target) {
    try {
      OBJECT_MAPPER.writeValue(file, target);
    } catch (Exception e) {
      throw new RuntimeException("While writing target " + file.getAbsolutePath(), e);
    }
  }

  public static String toJsonString(Object object) {
    try {
      if (object == null) {
        return null;
      }
      return OBJECT_MAPPER.writeValueAsString(object);
    } catch (Exception e) {
      throw new RuntimeException("While converting object to json", e);
    }
  }

  public static <T> T using(T variable, Consumer<T> consumer) {
    consumer.accept(variable);
    return variable;
  }

  public static void writeString(File file, String string) {
    try {
      FileUtils.write(file, string, Charset.defaultCharset());
    } catch (IOException e) {
      throw new RuntimeException("While writing output file " + file.getAbsolutePath(), e);
    }
  }

  public static String multiTrim(String message) {
    return multiTrim(message, " ");
  }

  public static String multiTrim(String message, String delimiter) {
    return Arrays.stream(ofNullable(message).orElse("").split("\n"))
        .map(String::trim).collect(Collectors.joining(delimiter));
  }

  public static void setClockSkew(Duration skew) {
    clockSkew = skew;
  }

  public static Date getNow() {
    return Date.from(instantNow());
  }

  public static Instant instantNow() {
    return Instant.now().plus(clockSkew);
  }

  public static String getTimestamp() {
    return isoConvert(getNow());
  }

  public static String prefixedDifference(String prefix, Set<String> a, Set<String> b) {
    return joinOrNull(prefix, symmetricDifference(a, b));
  }

  public static String removeArg(List<String> argList, String description) {
    if (argList.isEmpty()) {
      throw new IllegalArgumentException(format("Missing required %s argument", description));
    }
    return argList.remove(0);
  }

  public static String removeStringArg(List<String> argList, String description) {
    if (!argList.isEmpty() && argList.get(0).startsWith("-")) {
      throw new IllegalArgumentException(
          format("Missing required %s string argument", description));
    }
    return removeArg(argList, description);
  }

  public static byte[] getFileBytes(String dataFile) {
    Path dataPath = Paths.get(dataFile);
    try {
      return Files.readAllBytes(dataPath);
    } catch (Exception e) {
      throw new RuntimeException("While getting data from " + dataPath.toAbsolutePath(), e);
    }
  }

  public static byte[] getFileBytes(File dataFile) {
    return getFileBytes(dataFile.getPath());
  }

  public static Instant toInstant(String timestamp) {
    return ifNotNullGet(timestamp, Instant::parse);
  }

  public static <T> Set<T> findDuplicates(List<T> list) {
    Set<T> elements = new HashSet<>();
    return list.stream()
        .filter(n -> !elements.add(n))
        .collect(Collectors.toSet());
  }

  public static <T> Set<T> listUniqueSet(List<T> proxyIdList) {
    ifNotEmptyThrow(findDuplicates(proxyIdList),
        duplicates -> format("Duplicate proxy_id entries: " + duplicates));
    return new HashSet<>(proxyIdList);
  }

  public static String setOrSize(Set<String> items) {
    return items.size() > SET_SIZE_THRESHOLD
        ? format("%d devices", items.size()) : items.toString();
  }
}
