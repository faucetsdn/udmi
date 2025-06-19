package com.google.bos.iot.core.bambi.model;

/**
 * Enum representing the different tabs/sheets in a BAMBI spreadsheet.
 */
public enum BambiSheet {
  SITE_METADATA("site_metadata"),
  CLOUD_IOT_CONFIG("cloud_iot_config"),
  SYSTEM("system"),
  CLOUD("cloud"),
  GATEWAY("gateway"),
  LOCALNET("localnet"),
  POINTSET("pointset"),
  POINTS("points");

  private final String name;

  BambiSheet(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
