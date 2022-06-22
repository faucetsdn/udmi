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

  private void assertErrorSummaryValidateSuccess(Map<String, Map<String, String>> summary) {
    if (summary == null) {
      return;
    }
    if (summary.get("Validating") == null) {
      return;
    }
    if (summary.get("Validating").size() == 0) {
      return;
    }
    fail(summary.get("Validating").toString());
  }

  private void assertErrorSummaryValidateFailure(Map<String, Map<String, String>> summary) {
    if ((summary == null) || (summary.get("Validating") == null)) {
      fail("Error summary for Validating key is null");
    }
    if (summary.get("Validating").size()==0) {
      fail("Error summary for Validating key is size 0");
    }
  }

  private RegistrarUnderTest getRegistrarUnderTest() {
    RegistrarUnderTest registrar = new RegistrarUnderTest();
    registrar.setSitePath(SITE_PATH);
    registrar.setProjectId(PROJECT_ID);
    registrar.setToolRoot(TOOL_ROOT);
    return registrar;
  }

  @Test public void metadataValidateSuccessTest() {
    RegistrarUnderTest registrar = getRegistrarUnderTest();

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-s");
    argList.add(SITE_PATH);
    registrar.executeWithRegistrar(registrar, argList);
    assertErrorSummaryValidateSuccess(registrar.getLastErrorSummary());
  }

  @Test public void metadataValidateFailureTest() {
    RegistrarUnderTest registrar = getRegistrarUnderTest();

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-t");
    argList.add("-s");
    argList.add(SITE_PATH);
    registrar.executeWithRegistrar(registrar, argList);
    assertErrorSummaryValidateFailure(registrar.getLastErrorSummary());
  }

}
