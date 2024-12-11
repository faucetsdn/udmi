package com.google.daq.mqtt.util.providers;


import static udmi.lib.ProtocolFamily.IOT;

/**
 * No-validation family with no semantics, available for open use.
 */
public class IotFamilyProvider implements FamilyProvider {

  @Override
  public String familyKey() {
    return IOT;
  }

  @Override
  public void validateRef(String metadataRef) {
    // Always passes, no restrictions!
  }

  @Override
  public void validateAddr(String scanAddr) {
    // Always passes, no restrictions!
  }
}
