package com.google.udmi.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for deterministic collection ordering in {@link SiteModel}.
 */
public class SiteModelTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testDeterministicSortedIds() throws IOException {
    File siteDir = tempFolder.newFolder("site_model");
    Files.writeString(
        new File(siteDir, "cloud_iot_config.json").toPath(),
        "{\"registry_id\": \"ZZ-TRI-FECTA\"}",
        StandardCharsets.UTF_8);

    File devicesDir = new File(siteDir, "devices");
    for (String deviceId : List.of("SNS-4", "GAT-123", "AHU-1", "AHU-22")) {
      File devDir = new File(devicesDir, deviceId);
      devDir.mkdirs();
      Files.writeString(
          new File(devDir, "metadata.json").toPath(), "{}", StandardCharsets.UTF_8);
    }

    File templatesDir = new File(siteDir, "templates");
    templatesDir.mkdirs();
    for (String templateId : List.of("z-template", "a-template", "m-template")) {
      Files.writeString(
          new File(templatesDir, templateId + ".json").toPath(), "{}", StandardCharsets.UTF_8);
    }

    File extrasDir = new File(siteDir, "extras");
    for (String extraId : List.of("EXT-99", "EXT-2", "EXT-10")) {
      File extraDir = new File(extrasDir, extraId);
      extraDir.mkdirs();
      Files.writeString(
          new File(extraDir, "cloud_model.json").toPath(), "{}", StandardCharsets.UTF_8);
    }

    SiteModel siteModel = new SiteModel(siteDir.getAbsolutePath());

    assertEquals(
        "sorted device IDs",
        List.of("AHU-1", "AHU-22", "GAT-123", "SNS-4"),
        new ArrayList<>(siteModel.getDeviceIds()));
    assertEquals(
        "sorted template IDs",
        List.of("a-template", "m-template", "z-template"),
        new ArrayList<>(siteModel.getTemplateIds()));
    assertEquals(
        "sorted extra device IDs",
        List.of("EXT-10", "EXT-2", "EXT-99"),
        new ArrayList<>(siteModel.getExtraDevices()));
  }
}
