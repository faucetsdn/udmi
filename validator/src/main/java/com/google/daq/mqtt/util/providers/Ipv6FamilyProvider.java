package com.google.daq.mqtt.util.providers;


import static udmi.lib.ProtocolFamily.IPV_6;

/**
 * No-validation family with no semantics, available for open use.
 */
public class Ipv6FamilyProvider implements FamilyProvider {

  @Override
  public String familyKey() {
    return IPV_6;
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
