package com.google.udmi.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.String.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.download.URIDownloader;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

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
  public static ValidationException fromProcessingReport(ProcessingReport report) {
    checkArgument(!report.isSuccess(), "Report must not be successful");
    ImmutableList<ValidationException> causingExceptions =
        StreamSupport.stream(report.spliterator(), false)
            .filter(MessageValidator::errorOrWorse)
            .filter(MessageValidator::notOnIgnoreList)
            .map(MessageValidator::convertMessage).collect(toImmutableList());
    return causingExceptions.isEmpty() ? null : new ValidationException(
        format("%d schema violations found", causingExceptions.size()), causingExceptions);
  }

  private static boolean notOnIgnoreList(ProcessingMessage processingMessage) {
    return IGNORE_PATTERNS.stream()
        .noneMatch(p -> p.matcher(processingMessage.getMessage()).matches());
  }

  private static boolean errorOrWorse(ProcessingMessage processingMessage) {
    return processingMessage.getLogLevel().compareTo(LogLevel.ERROR) >= 0;
  }

  private static ValidationException convertMessage(ProcessingMessage processingMessage) {
    String pointer = processingMessage.asJson().get("instance").get("pointer").asText();
    String prefix =
        com.google.api.client.util.Strings.isNullOrEmpty(pointer) ? "" : (pointer + ": ");
    return new ValidationException(prefix + processingMessage.getMessage());
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
      ProcessingReport report = schema.validate(OBJECT_MAPPER.readTree(data), true);
      if (report.isSuccess()) {
        return ImmutableList.of();
      }
      throw fromProcessingReport(report);
    } catch (ValidationException e) {
      return e.getAllMessages();
    } catch (IOException | ProcessingException ex) {
      return ImmutableList.of(ex.getMessage());
    }
  }

  private JsonSchema getSchema(String subFolder) {
    return getSchema(new File(schemaRoot, subFolder + ".json"));
  }

  private JsonSchema getSchema(File schemaFile) {
    try (InputStream schemaStream = new FileInputStream(schemaFile)) {
      JsonNode rawSchema = OBJECT_MAPPER.readTree(schemaStream);
      return JsonSchemaFactory.newBuilder()
          .setLoadingConfiguration(
              LoadingConfiguration.newBuilder()
                  .addScheme("file", new RelativeDownloader())
                  .freeze())
          .freeze()
          .getJsonSchema(rawSchema);
    } catch (Exception e) {
      throw new RuntimeException("While loading schema " + schemaFile.getAbsolutePath(), e);
    }
  }

  class RelativeDownloader implements URIDownloader {

    public static final String FILE_URL_PREFIX = "file:";

    @Override
    public InputStream fetch(URI source) {
      String url = source.toString();
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
    }
  }
}
