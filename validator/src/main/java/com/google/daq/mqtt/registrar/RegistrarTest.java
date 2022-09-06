package com.google.daq.mqtt.registrar;

import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.main.JsonSchema;
import java.util.ArrayList;
import java.util.Map;
import org.junit.Test;

/**
 * Test suite for basic registrar functionality.
 */
public class RegistrarTest {

  private static final String SCHEMA_BASE_PATH = "schema";
  private static final String METADATA_JSON = "metadata.json";
  private static final String PROJECT_ID = "unit-testing";
  private static final String SITE_PATH = "../sites/udmi_site_model";
  private static final String TOOL_ROOT = "../";

  private static final String SYSTEM_LOCATION_SITE = "ZZ-TRI-FECTA";
  private static final String DEVICE_NAME = "AHU-1";
  private ObjectMapper mapper = new ObjectMapper();

  private static class RegistrarUnderTest extends Registrar {
    protected JsonSchema getJsonSchema(String schemaName) {
      return getSchemas().get(schemaName);
    }
  }

  private void assertErrorSummaryValidateSuccess(Map<String, Map<String, String>> summary) {
    if ((summary == null) || (summary.get("Validating") == null)
        || (summary.get("Validating").size() == 0)) {
      return;
    }
    fail(summary.get("Validating").toString());
  }

  private void assertErrorSummaryValidateFailure(Map<String, Map<String, String>> summary) {
    if ((summary == null) || (summary.get("Validating") == null)) {
      fail("Error summary for Validating key is null");
    }
    if (summary.get("Validating").size() == 0) {
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
    final RegistrarUnderTest registrar = getRegistrarUnderTest();

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-s");
    argList.add(SITE_PATH);
    Registrar.processArgs(argList, registrar);
    registrar.execute();
    assertErrorSummaryValidateSuccess(registrar.getLastErrorSummary());
  }

  @Test public void metadataValidateFailureTest() {
    final RegistrarUnderTest registrar = getRegistrarUnderTest();

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-t");
    argList.add("-s");
    argList.add(SITE_PATH);
    Registrar.processArgs(argList, registrar);
    registrar.execute();
    assertErrorSummaryValidateFailure(registrar.getLastErrorSummary());
  }

}
