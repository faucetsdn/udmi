package com.google.daq.mqtt.validator;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.daq.mqtt.validator.Validator.MessageBundle;
import com.google.daq.mqtt.validator.Validator.MetadataReport;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ValidatorTest {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setDateFormat(new ISO8601DateFormat())
          .setSerializationInclusion(Include.NON_NULL);

  private static final String SCHEMA_SPEC = "../schema";
  private static final String SITE_DIR = "../sites/udmi_site_model";
  private static final String PROJECT_ID = "ZZ-TRI-FECTA";
  private static final String DEVICE_ID = "AHU-1";
  private static final File REPORT_FILE = new File(SITE_DIR + "/out/validation_report.json");

  private final Validator validator = new Validator(PROJECT_ID);

  {
    validator.setSchemaSpec(SCHEMA_SPEC);
    validator.setSiteDir(SITE_DIR);
  }

  @Test
  public void emptyMessage() {
    MessageBundle bundle = new MessageBundle();
    bundle.attributes = testMessageAttributes("event", "pointset");
    bundle.message = new HashMap<>();
    validator.validateMessage(bundle);
    MetadataReport report = getMetadataReport();
    assertEquals("One error device", report.errorDevices.size(), 1);
  }

  private MetadataReport getMetadataReport() {
    try {
      return OBJECT_MAPPER.readValue(REPORT_FILE, MetadataReport.class);
    } catch (Exception e) {
      throw new RuntimeException("While reading " + REPORT_FILE.getAbsolutePath(), e);
    }
  }

  private Map<String, String> testMessageAttributes(String subType, String subFolder) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put("deviceRegistryId", PROJECT_ID);
    attributes.put("deviceId", DEVICE_ID);
    attributes.put("subFolder", subFolder);
    attributes.put("subType", subType);
    return attributes;
  }
}