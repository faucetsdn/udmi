package com.google.daq.mqtt.util;

import static java.lang.String.format;

import java.util.regex.Pattern;

public class BacnetFamily implements NetworkFamily {

  Pattern BACNET_REF = Pattern.compile("([A-Z]{2})([0-9]+)\\.([a-z_]+)");

  @Override
  public String familyName() {
    return "bacnet";
  }

  @Override
  public void refValidator(String metadataRef) {
    boolean matches = BACNET_REF.matcher(metadataRef).matches();
    if (!matches) {
      throw new RuntimeException(
          format("point ref %s does not match expression %s", metadataRef, BACNET_REF));
    }
  }
}
