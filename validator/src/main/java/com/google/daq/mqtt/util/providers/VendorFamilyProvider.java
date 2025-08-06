package com.google.daq.mqtt.util.providers;


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
  public void validateRef(String metadataRef) {
    // Always passes, no restrictions!
  }

  @Override
  public void validateAddr(String scanAddr) {
    // Always passes, no restrictions!
  }

  @Override
  public void validateNetwork(String network) {
    // Always passes, no restrictions!
  }
}
