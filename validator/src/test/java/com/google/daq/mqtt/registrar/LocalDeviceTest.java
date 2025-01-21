package com.google.daq.mqtt.registrar;

import static com.google.udmi.util.GeneralUtils.catchToNull;
import static com.google.udmi.util.JsonUtil.stringify;
import static com.google.udmi.util.SiteModel.METADATA_JSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.fge.jsonschema.main.JsonSchema;
import com.google.daq.mqtt.registrar.LocalDevice.DeviceKind;
import com.google.udmi.util.JsonUtil;
import com.google.udmi.util.SiteModel;
import java.io.File;
import java.util.Map;
import org.junit.Test;
import udmi.schema.Metadata;

/**
 * Unit tests for LocalDevice.
 */
public class LocalDeviceTest {

  private static final Map<String, JsonSchema> SCHEMAS = null;
  private static final String EMPTY_DEFAULTS_SITE = "../tests/sites/discovery";
  private static final String POPULATED_DEFAULTS_SITE = "../tests/sites/missing";
  private static final String DEVICE_ID = "GAT-123";
  private Metadata rawMetadata;

  @Test
  public void getMergedEmptyMetadata() {
    LocalDevice localDevice = getTestInstance(EMPTY_DEFAULTS_SITE);
    Metadata metadata = localDevice.getMetadata();
    metadata.version = null;
    metadata.upgraded_from = null;
    metadata.gateway = null;
    assertEquals("metadata is same as raw metadata", stringify(rawMetadata), stringify(metadata));
  }

  @Test
  public void getMergedMetadata() {
    LocalDevice localDevice = getTestInstance(POPULATED_DEFAULTS_SITE);
    Metadata metadata = localDevice.getMetadata();
    metadata.version = null;
    assertNotNull("site default cloud detail not defined", metadata.cloud.detail);
    assertNull("raw cloud detail is defined", catchToNull(() -> rawMetadata.cloud.detail));
  }

  private LocalDevice getTestInstance(String sitePath) {
    SiteModel siteModel = new SiteModel(sitePath);
    assertTrue("test device exists: " + DEVICE_ID, siteModel.deviceExists(DEVICE_ID));
    File rawMetadataFile = siteModel.getDeviceFile(DEVICE_ID, METADATA_JSON);
    rawMetadata = JsonUtil.loadFileStrictRequired(Metadata.class, rawMetadataFile);
    rawMetadata.version = null;
    rawMetadata.gateway = null;
    return new LocalDevice(siteModel, DEVICE_ID, SCHEMAS, null, DeviceKind.SIMPLE);
  }
}