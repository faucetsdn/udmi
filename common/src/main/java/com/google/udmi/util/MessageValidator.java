package com.google.udmi.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.ExecutionContext;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.format.DateTimeFormat;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.SchemaLoader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validation wrapper for processing individual messages.
 */
public class MessageValidator {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  @SuppressWarnings("checkstyle:linelength")
  private static final List<String> IGNORE_LIST = ImmutableList.of(
      "instance type \\(string\\) does not match any allowed primitive type \\(allowed: \\[.*\"number\"\\]\\)");
  private static final List<Pattern> IGNORE_PATTERNS = IGNORE_LIST.stream().map(Pattern::compile)
      .toList();

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
    ImmutableList<ValidationException> causingExceptions =
        report.stream()
            .filter(MessageValidator::notOnIgnoreList)
            .map(MessageValidator::convertMessage).collect(toImmutableList());
    return causingExceptions.isEmpty() ? null : new ValidationException(
        format("%d schema violations found", causingExceptions.size()), causingExceptions);
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
    JsonMetaSchema metaSchema = JsonMetaSchema.builder(
        JsonMetaSchema.getV7().getUri(), JsonMetaSchema.getV7())
        .addFormat(new DateTimeFormat() {
          @Override
          public boolean matches(ExecutionContext executionContext, String value) {
            return super.matches(executionContext,
                value == null ? null : value.replaceFirst("\\+0000$", "Z"));
          }
        })
        .build();
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7,
        builder -> builder.addMetaSchema(metaSchema)
            .schemaLoaders(schemaLoaders -> schemaLoaders.add(schemaLoader)));
  }

  private JsonSchema getSchema(File schemaFile) {
    try (InputStream schemaStream = new FileInputStream(schemaFile)) {
      JsonNode rawSchema = OBJECT_MAPPER.readTree(schemaStream);
      return getSchemaFactory(new RelativeDownloader()).getSchema(rawSchema);
    } catch (Exception e) {
      throw new RuntimeException("While loading schema " + schemaFile.getAbsolutePath(), e);
    }
  }

  class RelativeDownloader implements SchemaLoader {

    public static final String FILE_URL_PREFIX = "file:";

    @Override
    public InputStreamSource getSchema(AbsoluteIri iri) {
      return () -> {
        String url = iri.toString();
        try {
          if (!url.startsWith(FILE_URL_PREFIX)) {
            throw new IllegalStateException("Expected path to start with " + FILE_URL_PREFIX);
          }
          String newUrl =
              FILE_URL_PREFIX + new File(schemaRoot, url.substring(FILE_URL_PREFIX.length()));
          return (InputStream) (new URL(newUrl)).getContent();
        } catch (Exception e) {
          throw new RuntimeException("While loading URL " + url, e);
        }
      };
    }
  }
}
