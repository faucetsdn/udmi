package com.google.daq.mqtt.registrar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;
import udmi.schema.Metadata;

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
      return getSchemas().get(schemaName);
    }
  }


  /*
  private InputStream getTestFileStream(String filename) throws FileNotFoundException {
    File f = new File("/home/jrand/src/johnrandolph/udmi/tests/metadata.tests/", filename);
    return new FileInputStream(f);
  }

  private Metadata getTestMetadataValue(String filename) throws IOException {
    return mapper.readValue(getTestFileStream(filename), Metadata.class);
  }

  private JsonNode getTestMetadataTree(String filename) throws IOException {
    return mapper.readTree(getTestFileStream(filename));
  }

  private JsonNode getMetadataAsJsonNode(Metadata metadata) throws JsonProcessingException {
    return mapper.readTree(mapper.writeValueAsString(metadata));
  }

  private void assertSuccessReport(ProcessingReport report) {
    if (!report.isSuccess()) {
      for (ProcessingMessage msg : report) {
        if (msg.getLogLevel().compareTo(LogLevel.ERROR) >= 0) {
          int i = 0;
          fail(msg.getMessage().toString());
        }
      }
    }
  }

  private void assertFailReport(ProcessingReport report, String message) {
    if (!report.isSuccess()) {
      for (ProcessingMessage msg : report) {
        if (msg.getLogLevel().compareTo(LogLevel.ERROR) >= 0) {
          if (msg.getMessage().contains(message)) {
            return;
          }
          fail(msg.getMessage().toString());
        }
      }
    }
    fail("ProcessingReport had no errors or no matching message");
  }
  */

  private void assertErrorSummarySuccess(Map<String, Map<String, String>> summary) {
    if (summary.get("Validating").size() == 0) {
      return;
    }
    fail(summary.get("Validating").toString());
  }

  @Test public void metadataValidateSuccessTest() {
    RegistrarUnderTest registrar = getRegistrarUnderTest();
    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-s");
    argList.add("/home/jrand/src/johnrandolph/udmi_site_model");
    registrar.executeWithRegistrar(registrar, argList);
    assertErrorSummarySuccess(registrar.getLastErrorSummary());
  }

  private RegistrarUnderTest getRegistrarUnderTest() {
    RegistrarUnderTest registrar = new RegistrarUnderTest();
    registrar.setSitePath(SITE_PATH);
    registrar.setProjectId(PROJECT_ID);
    registrar.setToolRoot(TOOL_ROOT);
    return registrar;
  }

}
