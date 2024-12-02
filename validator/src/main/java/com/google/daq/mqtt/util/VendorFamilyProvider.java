package com.google.daq.mqtt.util;


import static udmi.lib.ProtocolFamily.VENDOR;

/**
 * No-validation family with no semantics, available for open use.
 */
public class VendorFamilyProvider implements FamilyProvider {

  @Override
  public String familyKey() {
    return VENDOR;
  }

  @Override
  public void refValidator(String metadataRef) {
    // Always passes, no restrictions!
  }
}
