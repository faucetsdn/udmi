package com.google.daq.mqtt.registrar;

import static com.google.udmi.util.JsonUtil.convertTo;
import static com.google.udmi.util.JsonUtil.stringify;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.main.JsonSchema;
import com.google.udmi.util.GeneralUtils;
import com.google.udmi.util.JsonUtil;
import java.util.Date;
import java.util.Map;
import org.junit.Test;
import udmi.schema.Metadata;

/**
 * Unit tests for LocalDevice.
 */
public class LocalDeviceTest {

  public static final String DEVICE_ID = "test_device";
  public static final Map<String, JsonSchema> SCHEMAS = null;
  public static final int FAKE_TIME = 1298213;
  public static String MERGE_DATA = "{ \"testing\": 10 }";

  @Test
  public void getMergedEmptyMetadata() {
    LocalDevice localDevice = getTestInstance(null);
    JsonNode toMerge = JsonUtil.fromString(JsonNode.class, MERGE_DATA);
    String original = stringify(toMerge);
    JsonNode result = localDevice.getMergedMetadata(toMerge);
    assertEquals("merged results with no side defaults", original, stringify(result));
  }

  @Test
  public void getMergedSiteMetadata() {
    Metadata siteMetadata = getSiteMetadata();
    Metadata augmentedData = GeneralUtils.deepCopy(siteMetadata);
    Metadata toMerge = new Metadata();
    toMerge.timestamp = new Date(FAKE_TIME + 1);
    augmentedData.timestamp = toMerge.timestamp;
    toMerge.description = "testing";
    augmentedData.description = toMerge.description;
    LocalDevice localDevice = getTestInstance(siteMetadata);
    Metadata result = convertTo(Metadata.class,
        localDevice.getMergedMetadata(convertTo(JsonNode.class, toMerge)));
    assertEquals("merged results with no side defaults", stringify(augmentedData),
        stringify(result));
  }

  private Metadata getSiteMetadata() {
    Metadata metadata = new Metadata();
    metadata.timestamp = new Date(FAKE_TIME);
    metadata.version = "testing";
    return metadata;
  }

  private LocalDevice getTestInstance(Metadata siteMetadata) {
    return new LocalDevice(null, null, DEVICE_ID, SCHEMAS, null, siteMetadata);
  }
}