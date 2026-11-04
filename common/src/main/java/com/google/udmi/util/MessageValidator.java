package com.google.udmi.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.ExecutionContext;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.NonValidationKeyword;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.format.DateTimeFormat;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.SchemaLoader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validation wrapper for processing individual messages.
 */
public class MessageValidator {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  @SuppressWarnings("checkstyle:linelength")
  private static final List<String> IGNORE_LIST = ImmutableList.of(
      ".*: string found, (number|\\[integer, number\\]) expected$");
  private static final List<Pattern> IGNORE_PATTERNS = IGNORE_LIST.stream().map(Pattern::compile)
      .toList();
  private static final List<String> UDMI_NON_VALIDATION_KEYWORDS = ImmutableList.of(
      "$defaultPresentation",
      "$presentation",
      "$section",
      "$udmi_version",
      "descritpion",
      "examples",
      "existingJavaType",
      "note",
      "status");
  private static final List<NonValidationKeyword> NON_VALIDATION_KEYWORDS =
      UDMI_NON_VALIDATION_KEYWORDS.stream()
          .map(NonValidationKeyword::new)
          .toList();
  // UDMI requires UTC timestamps; normalize +0000 to Z for strict RFC 3339 date-time validation.
  private static final DateTimeFormat DATE_TIME_FORMAT = new DateTimeFormat() {
    @Override
    public boolean matches(ExecutionContext executionContext, String value) {
      return super.matches(executionContext,
          value == null ? null : value.replaceFirst("\\+0000$", "Z"));
    }
  };
  private static final JsonMetaSchema V4_META_SCHEMA = JsonMetaSchema.builder(
      JsonMetaSchema.getV4().getUri(), JsonMetaSchema.getV4())
      .addKeywords(NON_VALIDATION_KEYWORDS)
      .addFormat(DATE_TIME_FORMAT)
      .build();
  private static final JsonMetaSchema V7_META_SCHEMA = JsonMetaSchema.builder(
      JsonMetaSchema.getV7().getUri(), JsonMetaSchema.getV7())
      .addKeywords(NON_VALIDATION_KEYWORDS)
      .addFormat(DATE_TIME_FORMAT)
      .build();

  private final Map<String, JsonSchema> schemaMap = Maps.newConcurrentMap();
  private final File schemaRoot;

  /**
   * Create a message validator for the given schema root path.
   *
   * @param schemaRootPath path to schema
   */
  public MessageValidator(String schemaRootPath) {
    schemaRoot = new File(schemaRootPath);
    if (!schemaRoot.isDirectory()) {
      throw new IllegalStateException(
          "Could not find schema directory " + schemaRoot.getAbsolutePath());
    }
  }

  /**
   * From an external processing report.
   *
   * @param report Report to convert
   * @return Converted exception.
   */
  public static ValidationException fromProcessingReport(Set<ValidationMessage> report) {
    checkArgument(!report.isEmpty(), "Report must not be empty");
    Set<String> failedOneOfPrefixes = report.stream()
        .filter(msg -> "oneOf".equals(msg.getType()))
        .map(msg -> oneOfKey(msg) + "[")
        .collect(Collectors.toSet());
    ImmutableList<ValidationException> causingExceptions =
        report.stream()
            .filter(msg -> isNotPrunedByOneOf(msg, failedOneOfPrefixes))
            .filter(MessageValidator::notOnIgnoreList)
            .map(MessageValidator::convertMessage).collect(toImmutableList());
    return causingExceptions.isEmpty() ? null : new ValidationException(
        format("%d schema violations found", causingExceptions.size()), causingExceptions);
  }

  private static String oneOfKey(ValidationMessage validationMessage) {
    return validationMessage.getInstanceLocation() + "|" + validationMessage.getEvaluationPath();
  }

  private static boolean isNotPrunedByOneOf(
      ValidationMessage validationMessage, Set<String> failedOneOfPrefixes) {
    String key = oneOfKey(validationMessage);
    return failedOneOfPrefixes.stream().noneMatch(key::startsWith);
  }

  private static boolean notOnIgnoreList(ValidationMessage validationMessage) {
    return IGNORE_PATTERNS.stream()
        .noneMatch(p -> p.matcher(validationMessage.getMessage()).matches());
  }

  private static ValidationException convertMessage(ValidationMessage validationMessage) {
    return new ValidationException(validationMessage.getMessage());
  }

  /**
   * Validate the indicated message.
   *
   * @param subFolder indicates the type of message for validation
   * @param data message data
   * @return list of validation results
   */
  public List<String> validateMessage(String subFolder, String data) {
    JsonSchema schema = schemaMap.computeIfAbsent(subFolder, this::getSchema);
    try {
      Set<ValidationMessage> report = schema.validate(OBJECT_MAPPER.readTree(data));
      if (report.isEmpty()) {
        return ImmutableList.of();
      }
      ValidationException exception = fromProcessingReport(report);
      if (exception == null) {
        return ImmutableList.of();
      }
      throw exception;
    } catch (ValidationException e) {
      return e.getAllMessages();
    } catch (IOException ex) {
      return ImmutableList.of(ex.getMessage());
    }
  }

  private JsonSchema getSchema(String subFolder) {
    return getSchema(new File(schemaRoot, subFolder + ".json"));
  }

  /**
   * Create a schema factory configured for UDMI Draft-07 schemas and date-time formats.
   *
   * @param schemaLoader loader for relative schema references
   * @return configured JsonSchemaFactory
   */
  public static JsonSchemaFactory getSchemaFactory(SchemaLoader schemaLoader) {
    return JsonSchemaFactory.builder()
        .defaultMetaSchemaURI(V7_META_SCHEMA.getUri())
        .addMetaSchema(V4_META_SCHEMA)
        .addMetaSchema(V7_META_SCHEMA)
        .schemaLoaders(schemaLoaders -> schemaLoaders.add(schemaLoader))
        .build();
  }

  /**
   * Load and compile a JSON schema file, resolving relative file references from its directory.
   *
   * @param schemaFile schema file to load
   * @return compiled JsonSchema
   */
  public static JsonSchema getSchema(File schemaFile) {
    File baseDir = ofNullable(schemaFile.getParentFile()).orElse(new File("."));
    try (InputStream schemaStream = Files.newInputStream(schemaFile.toPath())) {
      JsonNode rawSchema = OBJECT_MAPPER.readTree(schemaStream);
      return getSchemaFactory(new RelativeDownloader(baseDir)).getSchema(rawSchema);
    } catch (Exception e) {
      throw new RuntimeException("While loading schema " + schemaFile.getAbsolutePath(), e);
    }
  }

  static class RelativeDownloader implements SchemaLoader {

    public static final String FILE_URL_PREFIX = "file:";
    private final File baseDir;

    RelativeDownloader(File baseDir) {
      this.baseDir = baseDir;
    }

    @Override
    public InputStreamSource getSchema(AbsoluteIri iri) {
      return () -> {
        String url = iri.toString();
        try {
          checkState(url.startsWith(FILE_URL_PREFIX),
              "Expected path to start with " + FILE_URL_PREFIX);
          File targetFile = new File(baseDir, url.substring(FILE_URL_PREFIX.length()));
          return Files.newInputStream(targetFile.toPath());
        } catch (Exception e) {
          throw new RuntimeException("While loading URL " + url, e);
        }
      };
    }
  }
}
