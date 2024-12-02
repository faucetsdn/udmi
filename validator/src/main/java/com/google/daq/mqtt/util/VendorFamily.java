package com.google.daq.mqtt.util;


/**
 * No-validation family with no semantics, available for open use.
 */
public class VendorFamily implements ProtocolFamily {

  @Override
  public String familyKey() {
    return udmi.lib.ProtocolFamily.VENDOR;
  }

  @Override
  public void refValidator(String metadataRef) {
    // Always passes, no restrictions!
  }
}
