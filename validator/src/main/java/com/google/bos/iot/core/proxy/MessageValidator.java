package com.google.bos.iot.core.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.download.URIDownloader;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.daq.mqtt.util.ValidationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Validation wrapper for processing individual messages.
 */
public class MessageValidator {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
      throw ValidationException.fromProcessingReport(report);
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
