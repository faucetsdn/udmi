package com.google.daq.mqtt.util;

public class VirtualFamily implements NetworkFamily {

  @Override
  public String familyName() {
    return "virtual";
  }

  @Override
  public void refValidator(String metadataRef) {
    // Virtual families allow anything... it's just a string mapping!
  }
}
