package com.google.daq.mqtt.util;

import static java.lang.String.format;

import java.util.regex.Pattern;

/**
 * General family of bacnet addresses.
 */
public class BacnetFamily implements NetworkFamily {

  // TODO: Make something that validates a bacnet addr against a decimal integer.

  // TODO: Fix this so it can include all the different forms.
  private static final Pattern BACNET_REF = Pattern.compile("([A-Z]{2,4})([0-9]+)\\.([a-z_]+)");

  @Override
  public String familyName() {
    return "bacnet";
  }

  @Override
  public void refValidator(String metadataRef) {
    System.err.println("Evaluating bacnet " + metadataRef);
    boolean matches = BACNET_REF.matcher(metadataRef).matches();
    if (!matches) {
      throw new RuntimeException(
          format("point ref %s does not match expression %s", metadataRef, BACNET_REF));
    }
  }
}
