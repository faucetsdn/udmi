package com.google.daq.mqtt.util;

/**
 * No-validation family with no semantics, available for open use.
 */
public class VendorFamily implements NetworkFamily {

  @Override
  public String familyName() {
    return "vendor";
  }

  @Override
  public void refValidator(String metadataRef) {
    // Always passes, no restrictions!
  }
}
