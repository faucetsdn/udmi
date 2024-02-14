package com.google.daq.mqtt.util;

public class VirtualFamily implements NetworkFamily {

  final static String FAMILY = "virtual";
  final static VirtualFamily INSTANCE = new VirtualFamily();

  @Override
  public void refValidator(String metadataRef) {
    // Virtual families allow anything... it's just a string mapping!
  }
}
