package com.google.udmi.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.udmi.util.GeneralUtils.OBJECT_MAPPER_STRICT;
import static com.google.udmi.util.GeneralUtils.fromJsonString;
import static com.google.udmi.util.GeneralUtils.ifNotNullGet;
import static com.google.udmi.util.GeneralUtils.isNotEmpty;
import static com.google.udmi.util.GeneralUtils.toJsonString;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.gson.internal.bind.util.ISO8601Utils;
import com.google.udmi.util.ProperPrinter.OutputFormat;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Collection of utilities for working with json things.
 */
public abstract class JsonUtil {

  public static final String JSON_EXT = "json";
  public static final String JSON_SUFFIX = ".json";
  public static final String JSON_OBJECT_LEADER = "{";
  private static final ObjectMapper STRICT_MAPPER = new ObjectMapper()
      .enable(Feature.ALLOW_COMMENTS)
      .enable(SerializationFeature.INDENT_OUTPUT)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .setDateFormat(new CleanDateFormat())
      .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature())
      .registerModule(NanSerializer.TO_NULL) // NaN is not valid JSON, so squash it now.
      .setSerializationInclusion(Include.NON_NULL);
  public static final ObjectMapper OBJECT_MAPPER = STRICT_MAPPER.copy()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  public static final ObjectMapper TERSE_MAPPER = OBJECT_MAPPER.copy()
      .disable(SerializationFeature.INDENT_OUTPUT);
  private static final String JSON_STRING_LEADER = "\"";

  /**
   * Convert the json string to a generic map object.
   *
   * @param input input string
   * @return input as map object
   */
  public static Map<String, Object> asMap(String input) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = fromString(TreeMap.class, input);
    return map;
  }

  /**
   * Convert the json object to a generic map object.
   *
   * @param input input file
   * @return input as map object
   */
  public static Map<String, Object> asMap(File input) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = loadFile(TreeMap.class, input);
    return map;
  }

  /**
   * Convert the json object to a LinkedHashMap object.
   *
   * @param input input file
   * @return input as map object
   */
  public static Map<String, Object> asLinkedHashMap(File input) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = loadFile(LinkedHashMap.class, input);
    return map;
  }

  /**
   * Convert the json object to a generic map object.
   *
   * @param input input object
   * @return input as map object
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> asMap(Object input) {
    if (input instanceof Map) {
      return (Map<String, Object>) input;
    } else {
      return convertTo(TreeMap.class, input);
    }
  }

  /**
   * Convert a generic object to a specific class.
   *
   * @param targetClass result class
   * @param message     object to convert
   * @param <T>         class parameter
   * @return converted object
   */
  public static <T> T convertTo(Class<T> targetClass, Object message) {
    requireNonNull(targetClass, "target class is null");
    return message == null ? null : fromString(targetClass, stringify(message));
  }

  /**
   * Convert a generic object to a specific class with strict field mappings.
   *
   * @param targetClass result class
   * @param message     object to convert
   * @param <T>         class parameter
   * @return converted object
   */
  public static <T> T convertToStrict(Class<T> targetClass, Object message) {
    requireNonNull(targetClass, "target class is null");
    try {
      return message == null ? null : fromStringStrict(targetClass, stringify(message));
    } catch (Exception e) {
      throw new RuntimeException("While converting strict to " + targetClass.getName(), e);
    }
  }

  public static <T> T fromString(Class<T> targetClass, String messageString) {
    requireNonNull(targetClass, "target class is null");
    try {
      return OBJECT_MAPPER.readValue(messageString, checkNotNull(targetClass, "target class"));
    } catch (Exception e) {
      throw new RuntimeException("While converting string to " + targetClass.getName(), e);
    }
  }

  public static <T> T fromStringStrict(Class<T> targetClass, String messageString) {
    requireNonNull(targetClass, "target class is null");
    try {
      return STRICT_MAPPER.readValue(messageString, checkNotNull(targetClass, "target class"));
    } catch (Exception e) {
      throw new RuntimeException("While converting string/string to " + targetClass.getName(), e);
    }
  }

  /**
   * Get a Date object parsed from a string representation.
   *
   * @param timestamp string representation
   * @return Date object
   */
  public static Date getDate(String timestamp) {
    return timestamp == null ? null : Date.from(getInstant(timestamp));
  }

  /**
   * Get an Instant object parsed from a string representation. Also perform some munging on the
   * input string to handle standard-yet-not-supported formats.
   *
   * @param timestamp string representation
   * @return Instant object
   */
  public static Instant getInstant(String timestamp) {
    String replaced = ifNotNullGet(timestamp, raw -> raw.replaceFirst("\\+0000$", "Z"));
    return timestamp == null ? null : Instant.parse(replaced);
  }

  /**
   * Get an Instant object for the current time.
   */
  public static Instant getNowInstant() {
    return Instant.now();
  }

  public static String getTimestampString(Date timestamp) {
    try {
      String quotedString = OBJECT_MAPPER_STRICT.writeValueAsString(timestamp);
      return quotedString.substring(1, quotedString.length() - 1);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("While generating updated timestamp", e);
    }
  }

  /**
   * Get a current timestamp string.
   *
   * @return current ISO timestamp
   */
  public static String isoConvert() {
    return isoConvert(CleanDateFormat.cleanDate());
  }

  private static Date isoConvert(String timestamp) {
    try {
      String wrappedString = "\"" + timestamp + "\"";
      return fromJsonString(wrappedString, Date.class);
    } catch (Exception e) {
      throw new RuntimeException("Creating date", e);
    }
  }

  public static String isoConvert(Instant timestamp) {
    return isoConvert(ifNotNullGet(timestamp, Date::from));
  }

  public static String isoConvert(Date timestamp) {
    try {
      if (timestamp == null) {
        return "null";
      }
      String dateString = toJsonString(timestamp);
      // Strip off the leading and trailing quotes from the JSON string-as-string representation.
      return dateString.substring(1, dateString.length() - 1);
    } catch (Exception e) {
      throw new RuntimeException("Creating timestamp", e);
    }
  }

  public static String currentIsoMs() {
    return ISO8601Utils.format(new Date(), true);
  }

  /**
   * Load a file to given type.
   *
   * @param clazz class of result
   * @param file  file to load
   * @param <T>   type of result
   * @return loaded object
   */
  public static <T> T loadFile(Class<T> clazz, File file) {
    try {
      return file.exists() ? OBJECT_MAPPER.readValue(file, clazz) : null;
    } catch (Exception e) {
      throw new RuntimeException("While loading " + file.getAbsolutePath(), e);
    }
  }

  /**
   * Load a file to given type, requiring that it exists.
   *
   * @param clazz class of result
   * @param file  path of file to load
   * @param <T>   type of result
   * @return loaded object
   */
  public static <T> T loadFileRequired(Class<T> clazz, String file) {
    return loadFileRequired(clazz, new File(file));
  }

  /**
   * Load a file to given type, requiring that it exists.
   *
   * @param clazz class of result
   * @param file  file to load
   * @param <T>   type of result
   * @return loaded object
   */
  public static <T> T loadFileRequired(Class<T> clazz, File file) {
    if (!file.exists()) {
      throw new RuntimeException("Required file not found: " + file.getAbsolutePath());
    }
    try {
      return OBJECT_MAPPER.readValue(file, clazz);
    } catch (Exception e) {
      throw new RuntimeException("While loading " + file.getAbsolutePath(), e);
    }
  }

  /**
   * Load file with strict(er) error checking, and throw an exception if necessary.
   *
   * @param clazz class of result
   * @param file  file to load
   * @param <T>   type of result
   * @return converted object
   */
  public static <T> T loadFileStrict(Class<T> clazz, File file) {
    try {
      return file.exists() ? STRICT_MAPPER.readValue(file, clazz) : null;
    } catch (Exception e) {
      throw new RuntimeException("While loading " + file.getAbsolutePath(), e);
    }
  }

  public static <T> T loadFileStrictRequired(Class<T> clazz, String file) {
    return loadFileStrictRequired(clazz, new File(file));
  }

  /**
   * Load file with strict(er) error checking and required-to-exist file.
   *
   * @param clazz class of result
   * @param file  file to load
   * @param <T>   type of result
   * @return converted object
   */
  public static <T> T loadFileStrictRequired(Class<T> clazz, File file) {
    if (!file.exists()) {
      throw new RuntimeException("Required file not found: " + file.getAbsolutePath());
    }
    try {
      return STRICT_MAPPER.readValue(file, clazz);
    } catch (NoSuchFileException notFoundException) {
      throw new RuntimeException("File not found: " + file.getAbsolutePath());
    } catch (Exception e) {
      throw new RuntimeException("While loading " + file.getAbsolutePath(), e);
    }
  }

  public static String loadFileString(File file) {
    try {
      return new String(Files.readAllBytes(file.toPath()));
    } catch (NoSuchFileException notFoundException) {
      throw new RuntimeException("File not found: " + file.getAbsolutePath());
    } catch (Exception e) {
      throw new RuntimeException("While loading file " + file.getAbsolutePath(), e);
    }
  }

  /**
   * Convert the given input file to a mapped representation.
   *
   * @param inputFile input file to convert to a map
   * @return object-as-map
   */
  public static Map<String, Object> loadMap(File inputFile) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = convertTo(TreeMap.class, loadFile(TreeMap.class, inputFile));
    return map;
  }

  /**
   * Convert the given input path to a mapped representation.
   */
  public static Map<String, Object> loadMap(String inputFile) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map =
        convertTo(TreeMap.class, loadFile(TreeMap.class, new File(inputFile)));
    return map;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> mapCast(Object object) {
    return (Map<String, Object>) object;
  }

  /**
   * Parse and get as any Java object from json.
   */
  public static Object parseJson(String message) {
    try {
      if (message != null && message.startsWith(JSON_OBJECT_LEADER)) {
        return OBJECT_MAPPER.readTree(message);
      }
      return message;
    } catch (Exception e) {
      throw new RuntimeException("While parsing json object", e);
    }
  }

  /**
   * Sleep and catch-and-rethrow any exceptions.
   *
   * @param sleepTimeMs duration to sleep
   */
  public static void safeSleep(long sleepTimeMs) {
    try {
      Thread.sleep(sleepTimeMs);
    } catch (Exception e) {
      throw new RuntimeException("Interrupted safe sleep", e);
    }
  }

  /**
   * Convert an object to a json string.
   *
   * @param target object to convert
   * @return json string representation
   */
  public static String stringify(Object target) {
    try {
      return OBJECT_MAPPER.writeValueAsString(target);
    } catch (Exception e) {
      throw new RuntimeException("While stringifying object", e);
    }
  }

  /**
   * Convert an object to a terse (no indent) json string.
   *
   * @param target object to convert
   * @return json string representation
   */
  public static String stringifyTerse(Object target) {
    try {
      return TERSE_MAPPER.writeValueAsString(target);
    } catch (Exception e) {
      throw new RuntimeException("While stringifying object", e);
    }
  }

  /**
   * Convert the pojo to a mapped representation.
   *
   * @param message input object to convert
   * @return object-as-map
   */
  public static Map<String, Object> toMap(Object message) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = convertTo(TreeMap.class, message);
    return map;
  }

  /**
   * Convert the string to a mapped representation.
   *
   * @param message input string to convert
   * @return object-as-map
   */
  public static Map<String, Object> toMap(String message) {
    @SuppressWarnings("unchecked")
    Map<String, Object> map = fromString(TreeMap.class, message);
    return map;
  }

  /**
   * Convert the string to a valid Java object.
   */
  public static Object toObject(String message) {
    if (message != null && message.startsWith(JSON_OBJECT_LEADER)) {
      return fromString(TreeMap.class, message);
    }
    return unquoteJson(message);
  }

  /**
   * Convert the pojo to a mapped representation of strings only.
   *
   * @param message input object to convert
   * @return object-as-map
   */
  public static Map<String, String> toStringMap(Object message) {
    @SuppressWarnings("unchecked")
    Map<String, String> map = convertTo(TreeMap.class, message);
    return map;
  }

  /**
   * Convert the pojo to a mapped representation of strings only.
   *
   * @param message input object to convert
   * @return object-as-map
   */
  public static Map<String, String> toStringMap(String message) {
    @SuppressWarnings("unchecked")
    Map<String, String> map = fromString(TreeMap.class, message);
    return map;
  }

  /**
   * Convert the pojo to a mapped representation of strings only.
   *
   * @param message input object to convert
   * @return object-as-map
   */
  public static Map<String, String> toStringMapStr(String message) {
    @SuppressWarnings("unchecked")
    Map<String, String> map = fromString(TreeMap.class, message);
    return map;
  }

  /**
   * Extract the underlying string representation from a JSON encoded message.
   */
  public static String unquoteJson(String message) {
    if (message == null || message.isEmpty() || message.startsWith(JSON_OBJECT_LEADER)) {
      return message;
    }
    if (message.startsWith(JSON_STRING_LEADER)) {
      return message.substring(1, message.length() - 1);
    }
    throw new RuntimeException("Unrecognized JSON start encoding: " + message.charAt(0));
  }

  /**
   * Write json representation to a file.
   *
   * @param theThing object to write
   * @param file     output file
   */
  public static void writeFile(Object theThing, File file) {
    try {
      OBJECT_MAPPER.writeValue(file, theThing);
    } catch (Exception e) {
      throw new RuntimeException("While writing " + file.getAbsolutePath(), e);
    }
  }

  /**
   * Write json representation to a file.
   * This method is added because the method `writeFile` does not print the array items on a newline.
   *
   * @param theThing object to write
   * @param file     output file
   */
  public static void writeFileWithCustomIndentForArrays(Object theThing, File file) {
    try {
      DefaultPrettyPrinter.Indenter indenter = new DefaultIndenter("  ", DefaultIndenter.SYS_LF);
      DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
      printer.indentArraysWith(indenter);
      OBJECT_MAPPER.writer(printer).writeValue(file, theThing);
    } catch (Exception e) {
      throw new RuntimeException("While writing with custom printer " + file.getAbsolutePath(), e);
    }
  }

  /**
   * Writes a JSON representation to a file using the custom ProperPrinter
   * to ensure correct colon spacing (e.g., "key": "value") and array item indentation.
   *
   * @param theThing object to write
   * @param file     output file
   */
  public static void writeFormattedFile(Object theThing, File file) {
    try {
      ProperPrinter printer = new ProperPrinter(OutputFormat.VERBOSE_ARRAY_ON_NEW_LINE);
      OBJECT_MAPPER.writer(printer).writeValue(file, theThing);
    } catch (Exception e) {
      throw new RuntimeException("While writing formatted file " + file.getAbsolutePath(), e);
    }
  }

  public static Map<String, Object> flattenNestedMap(Map<String, Object> map, String separator) {
    Map<String, Object> flattenedMap = new LinkedHashMap<>();
    flatten(map, "", flattenedMap, separator);
    return flattenedMap;
  }

  @SuppressWarnings("unchecked")
  private static void flatten(Map<String, Object> currentMap, String currentKey,
      Map<String, Object> flattenedMap, String separator) {
    if (currentMap.isEmpty() && isNotEmpty(currentKey)) {
      flattenedMap.put(currentKey, currentMap);
      return;
    }
    for (Map.Entry<String, Object> entry : currentMap.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      String newKey = currentKey.isEmpty() ? key : currentKey + separator + key;

      if (value instanceof Map) {
        flatten((Map<String, Object>) value, newKey, flattenedMap, separator);
      } else {
        flattenedMap.put(newKey, value);
      }
    }
  }

  public static JsonNode nestFlattenedJson(Map<String, String> flattenedJsonMap,
      String separatorRegex, Set<Pattern> nonNumericKeyPatterns) {
    ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();

    for (Map.Entry<String, String> entry : flattenedJsonMap.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      String[] parts = key.split(separatorRegex);
      nest(rootNode, parts, value, 0, OBJECT_MAPPER, key, nonNumericKeyPatterns);
    }

    return rootNode;
  }

  private static void nest(JsonNode currentNode, String[] parts, String value, int index,
      ObjectMapper mapper, String fullKey, Set<Pattern> nonNumericKeyPatterns) {

    String currentPart = parts[index];

    if (index == parts.length - 1) {
      handleSetLeafValue(currentNode, currentPart, value, mapper, fullKey, nonNumericKeyPatterns);
      return;
    }
    JsonNode childNode;
    int numericCurrentPartIfArray = -1;

    if (currentNode instanceof ObjectNode) {
      childNode = currentNode.get(currentPart);
    } else if (currentNode instanceof ArrayNode) {
      numericCurrentPartIfArray = parseAndValidateArrayIndex(currentPart, (ArrayNode) currentNode);
      childNode = currentNode.get(numericCurrentPartIfArray);
    } else {
      throw new RuntimeException(
          "Cannot traverse into node type: " + currentNode.getNodeType() +
              " for part '" + currentPart + "'. Current node: " + currentNode);
    }

    String nextPart = parts[index + 1];
    childNode = ensureAndGetChildNode(currentNode, childNode, currentPart,
        numericCurrentPartIfArray, nextPart, mapper);

    nest(childNode, parts, value, index + 1, mapper, fullKey, nonNumericKeyPatterns);
  }

  private static JsonNode convertValueToJsonNode(String value, ObjectMapper mapper, String fullKey,
      Set<Pattern> nonNumericKeyPatterns) {
    if (value == null) {
      return mapper.getNodeFactory().nullNode();
    }
    String trimmedValue = value.trim();
    if (nonNumericKeyPatterns != null &&
        nonNumericKeyPatterns.stream()
            .anyMatch(pattern -> fullKey.matches(pattern.pattern()))) {
      return mapper.getNodeFactory().textNode(value);
    }

    if ("true".equalsIgnoreCase(trimmedValue)) {
      return mapper.getNodeFactory().booleanNode(true);
    }
    if ("false".equalsIgnoreCase(trimmedValue)) {
      return mapper.getNodeFactory().booleanNode(false);
    }

    if ("null".equalsIgnoreCase(trimmedValue)) {
      return mapper.getNodeFactory().nullNode();
    }
    if ("{}".equals(trimmedValue)) {
      return mapper.getNodeFactory().objectNode();
    }
    if ("[]".equals(trimmedValue)) {
      return mapper.getNodeFactory().arrayNode();
    }

    // If the string contains any letters (and isn't "true" or "false"), treat it as a text node.
    if (trimmedValue.matches(".*[a-zA-Z].*")) {
      return mapper.getNodeFactory().textNode(value);
    }

    // Attempt to parse as a number
    try {
      int intValue = Integer.parseInt(trimmedValue);
      return mapper.getNodeFactory().numberNode(intValue);
    } catch (NumberFormatException e1) {
      try {
        long longValue = Long.parseLong(trimmedValue);
        return mapper.getNodeFactory().numberNode(longValue);
      } catch (NumberFormatException e2) {
        try {
          double doubleValue = Double.parseDouble(trimmedValue);
          return mapper.getNodeFactory().numberNode(doubleValue);
        } catch (NumberFormatException e3) {
          // Handle purely numeric-looking strings that still fail parsing
          // (e.g., "1.2.3") and fall back to text.
          return mapper.getNodeFactory().textNode(value);
        }
      }
    }
  }

  private static void handleSetLeafValue(JsonNode currentNode, String part, String value,
      ObjectMapper mapper, String fullKey, Set<Pattern> nonNumericKeyPatterns) {
    JsonNode valueNode = convertValueToJsonNode(value, mapper, fullKey, nonNumericKeyPatterns);

    if (currentNode instanceof ObjectNode) {
      ((ObjectNode) currentNode).set(part, valueNode);
    } else if (currentNode instanceof ArrayNode arrayNode) {
      int arrayIndex = parseAndValidateArrayIndex(part, arrayNode);
      arrayNode.set(arrayIndex, valueNode);
    } else {
      throw new RuntimeException(
          "Cannot set value on node type: " + currentNode.getNodeType() +
              " for part '" + part + "'. Current node: " + currentNode);
    }
  }

  private static int parseAndValidateArrayIndex(String part, ArrayNode arrayNode) {
    try {
      int arrayIndex = Integer.parseInt(part);
      if (arrayIndex < 0) {
        throw new RuntimeException("Array index cannot be negative: " + part);
      }
      while (arrayNode.size() <= arrayIndex) {
        arrayNode.addNull();
      }
      return arrayIndex;
    } catch (NumberFormatException e) {
      throw new RuntimeException(
          "Expected numeric array index for part '" + part +
              "' when current node is an Array. Array content: " + arrayNode.toString(), e);
    }
  }

  private static JsonNode ensureAndGetChildNode(
      JsonNode parentNode,
      JsonNode childNode,
      String currentKeyOrStringIndex,
      int numericIndexIfParentIsArray,
      String nextKeyOrStringIndex,
      ObjectMapper mapper) {

    boolean nextPathPartSuggestsArray = isPathPartArrayIndex(nextKeyOrStringIndex);

    if (childNode == null || childNode.isNull()) {
      if (nextPathPartSuggestsArray) {
        childNode = mapper.createArrayNode();
      } else {
        childNode = mapper.createObjectNode();
      }

      if (parentNode instanceof ObjectNode) {
        ((ObjectNode) parentNode).set(currentKeyOrStringIndex, childNode);
      } else if (parentNode instanceof ArrayNode) {
        ((ArrayNode) parentNode).set(numericIndexIfParentIsArray, childNode);
      } else {
        throw new IllegalStateException("Parent node is neither ObjectNode nor ArrayNode, "
            + "cannot attach child. Parent: " + parentNode);
      }
    } else {
      if (nextPathPartSuggestsArray && !childNode.isArray()) {
        throw new RuntimeException(
            "Path conflict: Expected ArrayNode for current part '" + currentKeyOrStringIndex +
                "' (because next part '" + nextKeyOrStringIndex + "' is numeric), but found " +
                childNode.getNodeType() + ". Existing node: " + childNode);
      }
      if (!nextPathPartSuggestsArray && !childNode.isObject()) {
        throw new RuntimeException(
            "Path conflict: Expected ObjectNode for current part '" + currentKeyOrStringIndex +
                "' (because next part '" + nextKeyOrStringIndex + "' is not numeric), but found "
                + childNode.getNodeType() + ". Existing node: " + childNode);
      }
    }
    return childNode;
  }

  private static boolean isPathPartArrayIndex(String pathPart) {
    try {
      Integer.parseInt(pathPart);
      return true;
    } catch (NumberFormatException ignored) {
      return false;
    }
  }

}
