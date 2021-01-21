package com.google.bos.iot.core.proxy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageValidator {

  private static final Logger LOG = LoggerFactory.getLogger(MessageValidator.class);

  Map<String, Schema> schemaMap = Maps.newConcurrentMap();
  private File schemaRoot;

  public MessageValidator(String schemaRootPath) {
    schemaRoot = new File(schemaRootPath);
    if (!schemaRoot.isDirectory()) {
      throw new IllegalStateException(
          "Could not find schema directory " + schemaRoot.getAbsolutePath());
    }
  }

  public List<String> validateMessage(String subFolder, String data) {
    Schema schema = schemaMap.computeIfAbsent(subFolder, this::getSchema);
    try {
      schema.validate(new JSONObject(new JSONTokener(data)));
      return ImmutableList.of();
    } catch (ValidationException e) {
      return e.getAllMessages();
    }
  }

  private Schema getSchema(String subFolder) {
    return getSchema(new File(schemaRoot, subFolder + ".json"));
  }

  private Schema getSchema(File schemaFile) {
    try (InputStream schemaStream = new FileInputStream(schemaFile)) {
      JSONObject rawSchema = new JSONObject(new JSONTokener(schemaStream));
      SchemaLoader loader = SchemaLoader.builder()
          .schemaJson(rawSchema).httpClient(new RelativeClient()).build();
      return loader.load().build();
    } catch (Exception e) {
      throw new RuntimeException("While loading schema " + schemaFile.getAbsolutePath(), e);
    }
  }

  class RelativeClient implements SchemaClient {
    public static final String FILE_URL_PREFIX = "file:";

    @Override
    public InputStream get(String url) {
      try {
        if (!url.startsWith(FILE_URL_PREFIX)) {
          throw new IllegalStateException("Expected path to start with " + FILE_URL_PREFIX);
        }
        String new_url = FILE_URL_PREFIX + new File(schemaRoot, url.substring(FILE_URL_PREFIX.length()));
        return (InputStream) (new URL(new_url)).getContent();
      } catch (Exception e) {
        throw new RuntimeException("While loading URL " + url, e);
      }
    }
  }

}
