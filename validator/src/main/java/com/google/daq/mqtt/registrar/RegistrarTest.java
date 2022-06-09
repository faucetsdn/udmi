package com.google.daq.mqtt.registrar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.daq.mqtt.registrar.Registrar.RelativeDownloader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import udmi.schema.Asset;
import udmi.schema.Location;
import udmi.schema.Metadata;
import udmi.schema.Physical_tag;
import udmi.schema.SystemModel;

public class RegistrarTest {

  public static final String SCHEMA_BASE_PATH = "schema";
  private static final String METADATA_JSON = "metadata.json";
  private static final String PROJECT_ID = "unit-testing";
  private static final String SITE_PATH = "../sites/udmi_site_model";
  private static final String TOOL_ROOT = "../";

  private static final String SYSTEM_LOCATION_SITE = "ZZ-TRI-FECTA";
  private static final String DEVICE_NAME = "AHU-1";
  private ObjectMapper mapper = new ObjectMapper();

  public class RegistrarUnderTest extends Registrar {
    protected JsonSchema getJsonSchema(String schemaName) {
      return schemas.get(schemaName);
    }
  }

  @Test public void emptyTest() {
    Registrar registrar = getRegistrarUnderTest();
    assertEquals(1, 1);
  }

  @Test public void metadataTest() throws JsonProcessingException, ProcessingException {
    RegistrarUnderTest registrar = getRegistrarUnderTest();
    String metadata = getTestMetadataAsString();
    JsonSchema validator = registrar.getJsonSchema(METADATA_JSON);
    validator.validate(mapper.readTree(metadata));
  }

  private RegistrarUnderTest getRegistrarUnderTest() {
    RegistrarUnderTest registrar = new RegistrarUnderTest();
    registrar.setSitePath(SITE_PATH);
    registrar.setProjectId(PROJECT_ID);
    registrar.setToolRoot(TOOL_ROOT);
    return registrar;
  }

  private Metadata getTestMetadata() {
    Metadata metadata = new Metadata();
    metadata.system = new SystemModel();
    /*
    metadata.system.location = new Location();
    metadata.system.location.site = SYSTEM_LOCATION_SITE;
    */
    metadata.system.physical_tag = new Physical_tag();
    metadata.system.physical_tag.asset = new Asset();
    metadata.system.physical_tag.asset.name = DEVICE_NAME;
    metadata.description = "Test Metadata";
    return metadata;
  }

  private String getTestMetadataAsString() throws JsonProcessingException {
    return mapper.writeValueAsString(getTestMetadata());
  }

}
