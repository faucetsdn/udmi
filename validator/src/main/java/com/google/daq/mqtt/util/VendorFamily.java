package com.google.daq.mqtt.util;


import udmi.lib.ProtocolFamily;


/**
 * No-validation family with no semantics, available for open use.
 */
public class VendorFamily implements NetworkFamily {

  @Override
  public String familyKey() {
    return ProtocolFamily.VENDOR;
  }

  @Override
  public void refValidator(String metadataRef) {
    // Always passes, no restrictions!
  }
}
