package com.google.daq.mqtt.util;


import udmi.schema.Common.ProtocolFamily;

/**
 * No-validation family with no semantics, available for open use.
 */
public class VendorFamily implements NetworkFamily {

  @Override
  public ProtocolFamily familyKey() {
    return ProtocolFamily.VENDOR;
  }

  @Override
  public void refValidator(String metadataRef) {
    // Always passes, no restrictions!
  }
}
